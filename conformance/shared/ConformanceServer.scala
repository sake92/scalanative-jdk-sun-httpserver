package conformance

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

/** Shared application: Native uses this library; the isolated JVM build uses the JDK server. */
object ConformanceServer {
  def main(args: Array[String]): Unit = {
    val profile = sys.env.getOrElse("HTTP_CONFORMANCE_PROFILE", "h1spec")
    require(profile == "h1spec" || profile == "http11probe", "Unknown conformance profile")
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 128)
    val workers = Executors.newFixedThreadPool(64)
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        try server.stop(0)
        finally {
          workers.shutdownNow()
          workers.awaitTermination(2, TimeUnit.SECONDS)
        }
      }
    }))
    try {
      server.setExecutor(workers)
      server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          try {
            val body = response(exchange, profile)
            exchange.getResponseHeaders().set("Content-Type", "text/plain")
            val length = if (body.isEmpty || exchange.getRequestMethod() == "HEAD") -1L else body.length.toLong
            exchange.sendResponseHeaders(200, length)
            if (length > 0) exchange.getResponseBody().write(body)
          } finally exchange.close()
        }
      })
      server.start()
      println("LISTENING " + server.getAddress().getPort())
      System.out.flush()
      new CountDownLatch(1).await()
    } finally {
      server.stop(0)
      workers.shutdownNow()
      workers.awaitTermination(2, TimeUnit.SECONDS)
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
          val name = entry.getKey().map(c => if (c >= 'A' && c <= 'Z') (c + 32).toChar else c)
          text.append(name).append(": ")
            .append(values.next()).append('\n')
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
