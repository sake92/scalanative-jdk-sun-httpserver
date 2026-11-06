import java.io.IOException
import java.net.{InetSocketAddress, Socket}
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.util.control.NonFatal

/** Bounded loopback observations. Pressure is measured; recovery and cleanup
  * are asserted.
  */
object SlowClients {
  val scenarios = Seq(
    "silent",
    "request-line",
    "headers",
    "idle-drip",
    "fixed-body",
    "chunk-data",
    "trailers",
    "response-reader",
    "shutdown-read",
    "shutdown-write"
  )

  def run(
      suite: String,
      source: Path,
      command: Seq[String],
      destination: Path,
      executor: String,
      duration: Int,
      selected: Seq[String],
      timeout: Double,
      revision: String
  ): Int = {
    val modes =
      if (executor == "both") Seq("default", "bounded") else Seq(executor)
    val names =
      if (suite == "slowhttptest") Seq("H", "B", "X")
      else if (selected.isEmpty) scenarios
      else selected.distinct
    val results = ujson.Arr()
    val report = ujson.Obj(
      "suite" -> suite,
      "revision" -> revision,
      "server" -> ujson.Arr.from(command),
      "duration_seconds" -> duration,
      "connections" -> 4,
      "workers" -> 2,
      "queue_capacity" -> 2,
      "probe_deadline_ms" -> 500,
      "results" -> results
    )
    try {
      for (mode <- modes; name <- names) {
        val directory =
          Files.createDirectory(destination.resolve(s"$mode-$name"))
        val result =
          ujson.Obj("executor" -> mode, "scenario" -> name, "complete" -> false)
        results.value += result
        try {
          observe(
            suite,
            source,
            command,
            directory,
            mode,
            name,
            duration,
            timeout,
            result
          )
          result("complete") = true
        } catch {
          case NonFatal(error) =>
            result("error") = error.toString
            System.err.println(s"$mode/$name failed: $error")
        }
        Files.writeString(
          destination.resolve("run.json"),
          ujson.write(report, indent = 2) + "\n"
        )
        System.err.println(
          s"$mode/$name: complete=${result("complete")}; results in $directory"
        )
      }
      if (results.value.forall(_("complete").bool)) 0 else 1
    } finally
      Files.writeString(
        destination.resolve("run.json"),
        ujson.write(report, indent = 2) + "\n"
      )
  }

  private def observe(
      suite: String,
      source: Path,
      command: Seq[String],
      directory: Path,
      mode: String,
      name: String,
      duration: Int,
      timeout: Double,
      result: ujson.Obj
  ): Unit = {
    val stopFile = directory.resolve("stop").toAbsolutePath
    val log = directory.resolve("server.log")
    val server = ConformanceRunner.Children.start(
      command,
      log,
      Map(
        "HTTP_CONFORMANCE_PROFILE" -> suite,
        "HTTP_CONFORMANCE_EXECUTOR" -> mode,
        "HTTP_CONFORMANCE_STOP_FILE" -> stopFile.toString
      )
    )
    val sockets = mutable.ArrayBuffer.empty[Socket]
    var external: Option[Process] = None
    val samples = ujson.Arr()
    val probes = ujson.Arr()
    result("samples") = samples
    result("probes") = probes
    result("pid") = server.pid().toDouble
    try {
      val port = ConformanceRunner.awaitPort(server, log).toInt
      result("port") = port
      require(probe(port)("ok").bool, "Ordinary request failed before pressure")
      samples.value += resources(server.pid(), "before")
      if (suite == "slowhttptest") {
        val path = if (name == "X") "/large" else "/body"
        val options =
          if (name == "X") Seq("-w", "512", "-y", "1024", "-n", "1", "-z", "32")
          else Seq("-i", "1", "-x", "1")
        val probeCommand = Seq(
          source.resolve("src/slowhttptest").toAbsolutePath.toString,
          s"-$name",
          "-c",
          "4",
          "-r",
          "4",
          "-l",
          duration.toString,
          "-p",
          "1",
          "-s",
          "1048576",
          "-t",
          (if (name == "B") "POST" else "GET"),
          "-u",
          s"http://127.0.0.1:$port$path",
          "-g",
          "-o",
          directory.resolve("tool").toString
        ) ++ options
        result("command") = ujson.Arr.from(probeCommand)
        external = Some(
          ConformanceRunner.Children
            .start(probeCommand, directory.resolve("tool.log"))
        )
      } else {
        val count = if (name == "idle-drip") 1 else 4
        for (_ <- 0 until count) {
          val socket = connect(
            port,
            smallReceiveBuffer =
              name.contains("write") || name == "response-reader"
          )
          sockets += socket
          if (name == "idle-drip") {
            send(socket, "GET /probe HTTP/1.1\r\nHost: localhost\r\n\r\n")
            readResponse(socket, close = false)
          }
          val initial = name match {
            case "silent"                => ""
            case "request-line"          => "GET /"
            case "headers" | "idle-drip" =>
              "GET / HTTP/1.1\r\nHost: localhost\r\nX-Drip: "
            case "fixed-body" | "shutdown-read" =>
              "POST /body HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1048576\r\n\r\nx"
            case "chunk-data" =>
              "POST /body HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n100000\r\nx"
            case "trailers" =>
              "POST /body HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-Trailer: "
            case "response-reader" | "shutdown-write" =>
              "GET /large HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
          }
          send(socket, initial)
        }
        result("opened_connections") = count
      }
      val started = System.nanoTime()
      val seconds = if (name == "idle-drip") 32 else duration
      val deadline = started + TimeUnit.SECONDS.toNanos(seconds.toLong)
      val toolDeadline =
        started + (math.min(timeout, duration + 5.0) * 1000000000).toLong
      var nextDrip = started
      var dripFailures = 0
      var pressure = true
      while (pressure) {
        require(server.isAlive, "Server exited during pressure")
        if (
          Set("headers", "idle-drip", "request-line")
            .contains(name) && System.nanoTime() >= nextDrip
        ) {
          sockets.foreach { socket =>
            try send(socket, "x")
            catch { case _: IOException => dripFailures += 1 }
          }
          nextDrip = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        }
        probes.value += probe(port)
        samples.value += resources(server.pid(), "pressure")
        Thread.sleep(250)
        pressure = external match {
          case Some(process) =>
            require(
              System.nanoTime() < toolDeadline || !process.isAlive,
              "slowhttptest exceeded deadline"
            )
            process.isAlive
          case None => System.nanoTime() < deadline
        }
      }
      result("observed_seconds") = (System.nanoTime() - started) / 1e9
      result("drip_write_failures") = dripFailures
      external.foreach { process =>
        result("tool_exit_code") = process.exitValue()
        require(process.exitValue() == 0, "slowhttptest failed; see tool.log")
        require(
          Files.isRegularFile(directory.resolve("tool.csv")),
          "slowhttptest produced no CSV"
        )
        ConformanceRunner.Children.stop(process)
      }
      val text = Files.readString(log)
      val bodyCase =
        Set("fixed-body", "chunk-data", "trailers", "shutdown-read", "B")
          .contains(name)
      val writeCase =
        Set("response-reader", "shutdown-write", "X").contains(name)
      if (bodyCase)
        require(text.contains("HANDLING /body"), "Body handler was not reached")
      if (writeCase) {
        val begun = "LARGE_STARTED".r.findAllIn(text).length
        val finished = "LARGE_FINISHED".r.findAllIn(text).length
        result("large_started") = begun
        result("large_finished") = finished
        require(begun > finished, "No unfinished large response observed")
      }
      result("pool_saturation_observed") =
        text.contains("POOL active=2 queued=2")
      if (
        mode == "bounded" && suite == "slowclients" && (bodyCase || writeCase)
      )
        require(
          result("pool_saturation_observed").bool,
          "Worker/queue saturation was not reached"
        )
      if (name == "idle-drip") {
        // Finish the head: a complete response proves this same connection survived >30s.
        send(sockets.head, "\r\nConnection: close\r\n\r\n")
        readResponse(sockets.head, close = true)
        result("dripped_connection_survived") = true
      }
      if (name.startsWith("shutdown-"))
        gracefulStop(server, stopFile, log, port, result)
      else {
        sockets.foreach(_.close())
        val recoveryStart = System.nanoTime()
        val recovery = ujson.Arr()
        var recovered = false
        while (
          !recovered && System.nanoTime() - recoveryStart < TimeUnit.SECONDS
            .toNanos(5)
        ) {
          val attempt = probe(port)
          recovery.value += attempt
          recovered = attempt("ok").bool
          if (!recovered) Thread.sleep(100)
        }
        result("recovery_probes") = recovery
        result("recovery_ms") = (System.nanoTime() - recoveryStart) / 1e6
        require(recovered, "Ordinary requests did not recover after disconnect")
        samples.value += resources(server.pid(), "recovered")
        gracefulStop(server, stopFile, log, port, result)
      }
    } finally {
      sockets.foreach(socket =>
        try socket.close()
        catch { case _: IOException => () }
      )
      external.foreach(ConformanceRunner.Children.stop)
      ConformanceRunner.Children.stop(server)
    }
  }

  private def gracefulStop(
      server: Process,
      stop: Path,
      log: Path,
      port: Int,
      result: ujson.Obj
  ): Unit = {
    val started = System.nanoTime()
    Files.writeString(stop, "stop\n")
    require(
      server.waitFor(5, TimeUnit.SECONDS),
      "Fixture did not stop within five seconds"
    )
    result("shutdown_ms") = (System.nanoTime() - started) / 1e6
    result("server_exit_code") = server.exitValue()
    require(server.exitValue() == 0, "Fixture exited abnormally")
    require(
      Files.readString(log).contains("STOPPED workers=true"),
      "Fixture workers did not terminate"
    )
    require(
      !probe(port)("ok").bool,
      "Listener still accepts requests after stop"
    )
  }

  private def connect(
      port: Int,
      smallReceiveBuffer: Boolean = false
  ): Socket = {
    val socket = new Socket()
    try {
      if (smallReceiveBuffer) socket.setReceiveBufferSize(1024)
      socket.connect(new InetSocketAddress("127.0.0.1", port), 500)
      socket.setSoTimeout(500)
      socket
    } catch {
      case error: Throwable => socket.close(); throw error
    }
  }

  private def send(socket: Socket, text: String): Unit = {
    socket.getOutputStream.write(text.getBytes(US_ASCII))
    socket.getOutputStream.flush()
  }

  private def readResponse(
      socket: Socket,
      close: Boolean,
      deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500)
  ): Unit = {
    val input = socket.getInputStream
    val bytes = new java.io.ByteArrayOutputStream()
    var done = false
    while (!done && bytes.size() < 8192) {
      val left = deadline - System.nanoTime()
      require(left > 0, "Response exceeded absolute probe deadline")
      socket.setSoTimeout(
        math.max(1, TimeUnit.NANOSECONDS.toMillis(left).toInt)
      )
      val value = input.read()
      if (value == -1) done = true
      else bytes.write(value)
      val text = bytes.toString("US-ASCII")
      if (!close && text.endsWith("\r\n\r\nOK")) done = true
    }
    val text = bytes.toString("US-ASCII")
    require(
      text.startsWith("HTTP/1.1 200 ") && text.endsWith("\r\n\r\nOK"),
      "Expected complete 200 response with body OK"
    )
  }

  private def probe(port: Int): ujson.Obj = {
    val started = System.nanoTime()
    val result = ujson.Obj("ok" -> false)
    try {
      val socket = connect(port)
      try {
        send(
          socket,
          "GET /probe HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
        )
        readResponse(
          socket,
          close = true,
          deadline = started + TimeUnit.MILLISECONDS.toNanos(500)
        )
        result("ok") = true
      } finally socket.close()
    } catch { case NonFatal(error) => result("error") = error.toString }
    result("latency_ms") = (System.nanoTime() - started) / 1e6
    result
  }

  private def resources(pid: Long, phase: String): ujson.Obj = {
    val root = Path.of(s"/proc/$pid")
    val status = Files.readString(root.resolve("status"))
    val result = ujson.Obj(
      "phase" -> phase,
      "time_ms" -> System.currentTimeMillis().toDouble
    )
    Seq("VmRSS" -> "rss_kib", "Threads" -> "threads").foreach {
      case (field, key) =>
        val pattern = ("(?m)^" + field + ":\\s+(\\d+)").r
        result(key) = pattern.findFirstMatchIn(status).get.group(1).toDouble
    }
    val files = Files.list(root.resolve("fd"))
    try result("fds") = files.count().toDouble
    finally files.close()
    result
  }
}
