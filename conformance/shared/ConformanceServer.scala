package conformance

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.{ArrayBlockingQueue, ThreadPoolExecutor}
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.{Files, Paths}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

/** Shared application: Native uses this library; the isolated JVM build uses
  * the JDK server.
  */
object ConformanceServer {
  def main(args: Array[String]): Unit = {
    val profile = sys.env.getOrElse("HTTP_CONFORMANCE_PROFILE", "h1spec")
    require(
      Set("h1spec", "http11probe", "slowclients", "slowhttptest").contains(
        profile
      ),
      "Unknown conformance profile"
    )
    val slow = profile == "slowclients" || profile == "slowhttptest"
    val mode = sys.env.getOrElse("HTTP_CONFORMANCE_EXECUTOR", "bounded")
    require(mode == "default" || mode == "bounded", "Unknown executor mode")
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 128)
    val workers =
      if (slow && mode == "default") None
      else if (slow)
        Some(
          new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue[Runnable](2),
            new ThreadPoolExecutor.AbortPolicy()
          )
        )
      else Some(Executors.newFixedThreadPool(64))
    val closed = new AtomicBoolean(false)
    def cleanup(): Unit = if (closed.compareAndSet(false, true)) {
      val started = System.nanoTime()
      try server.stop(0)
      finally {
        workers.foreach(_.shutdownNow())
        val terminated = workers.forall(_.awaitTermination(2, TimeUnit.SECONDS))
        println(
          "STOPPED workers=" + terminated + " millis=" + (System
            .nanoTime() - started) / 1000000
        )
        System.out.flush()
      }
    }
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        cleanup()
      }
    }))
    try {
      workers.foreach(server.setExecutor)
      server.createContext(
        "/",
        new HttpHandler {
          override def handle(exchange: HttpExchange): Unit = {
            try {
              if (slow) {
                val path = exchange.getRequestURI().getPath()
                println("HANDLING " + path)
                System.out.flush()
                if (path == "/large") {
                  val block = new Array[Byte](8192)
                  exchange.sendResponseHeaders(200, 64L * 1024 * 1024)
                  println("LARGE_STARTED")
                  System.out.flush()
                  var remaining = 8192
                  while (remaining > 0) {
                    exchange.getResponseBody().write(block)
                    remaining -= 1
                  }
                  println("LARGE_FINISHED")
                  System.out.flush()
                  return
                }
              }
              val body = response(exchange, profile)
              exchange.getResponseHeaders().set("Content-Type", "text/plain")
              val length =
                if (body.isEmpty || exchange.getRequestMethod() == "HEAD") -1L
                else body.length.toLong
              exchange.sendResponseHeaders(200, length)
              if (length > 0) exchange.getResponseBody().write(body)
            } finally exchange.close()
          }
        }
      )
      server.start()
      println("LISTENING " + server.getAddress().getPort())
      System.out.flush()
      if (slow) {
        val stop = Paths.get(sys.env("HTTP_CONFORMANCE_STOP_FILE"))
        while (!Files.exists(stop)) {
          workers.collect { case pool: ThreadPoolExecutor =>
            println(
              "POOL active=" + pool
                .getActiveCount() + " queued=" + pool.getQueue().size()
            )
          }
          System.out.flush()
          Thread.sleep(250)
        }
      } else new CountDownLatch(1).await()
    } finally {
      cleanup()
    }
  }

  private def response(exchange: HttpExchange, profile: String): Array[Byte] = {
    val path = exchange.getRequestURI().getPath()
    if (profile == "http11probe" && path == "/echo") {
      val text = new StringBuilder
      val entries = exchange.getRequestHeaders().entrySet().iterator()
      while (entries.hasNext()) {
        val entry = entries.next()
        val values = entry.getValue().iterator()
        while (values.hasNext()) {
          val name = entry
            .getKey()
            .map(c => if (c >= 'A' && c <= 'Z') (c + 32).toChar else c)
          text
            .append(name)
            .append(": ")
            .append(values.next())
            .append('\n')
        }
      }
      text.toString.getBytes(UTF_8)
    } else if (profile == "h1spec" || exchange.getRequestMethod() == "POST") {
      val body = new ByteArrayOutputStream()
      val buffer = new Array[Byte](4096)
      val input = exchange.getRequestBody()
      var count = input.read(buffer)
      while (count != -1) {
        body.write(buffer, 0, count)
        count = input.read(buffer)
      }
      body.toByteArray()
    } else "OK".getBytes(UTF_8)
  }
}
