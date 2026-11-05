package org.scalanative.testsuite.httpserver

import java.io.{ByteArrayOutputStream, DataInputStream, InputStream}
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.concurrent.atomic.AtomicReference
import com.sun.net.httpserver._
import org.junit.Assert._
import org.junit.Test

/** Public server contracts run unchanged against Native and the host JDK. */
class HttpServerContractTest {
  @Test def lifecycleAndContexts(): Unit = withServer { server =>
    assertNull(server.getAddress())
    assertThrows(classOf[IllegalStateException], () => server.start())
    server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    assertTrue(server.getAddress().getPort() > 0)
    assertThrows(classOf[java.io.IOException], () => server.bind(server.getAddress(), 0))
    assertThrows(classOf[IllegalArgumentException], () => server.createContext("invalid"))
    assertThrows(classOf[NullPointerException], () => server.createContext("/null", null))
    val context = server.createContext("/late")
    assertSame(server, context.getServer())
    assertNull(context.getHandler())
    context.setHandler(handler("late"))
    assertThrows(classOf[IllegalArgumentException], () => context.setHandler(handler("again")))
    server.start()
    assertThrows(classOf[IllegalStateException], () => server.setExecutor(null))
    assertThrows(classOf[IllegalArgumentException], () => server.stop(-1))
    server.stop(0)
    assertThrows(classOf[IllegalStateException], () => server.start())
  }

  @Test def duplicateContextsFollowRuntimeBaseline(): Unit = withServer { server =>
    server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val first = server.createContext("/same", handler("first"))
    if (ReferenceRuntime.jdk8) {
      val second = server.createContext("/same", handler("second"))
      assertNotSame(first, second)
      server.start()
      assertEquals("first", request(server, "/same")._2)
      server.removeContext(first)
      assertEquals("second", request(server, "/same")._2)
      server.removeContext(second)
      assertEquals(404, request(server, "/same")._1)
      val third = server.createContext("/same", handler("third"))
      server.createContext("/same", handler("fourth"))
      server.removeContext("/same")
      assertEquals("fourth", request(server, "/same")._2)
      assertThrows(classOf[IllegalArgumentException], () => server.removeContext(third))
    } else assertThrows(classOf[IllegalArgumentException], () => server.createContext("/same"))
  }

  @Test def routesAndRemovesContexts(): Unit = withServer { server =>
    server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/app", handler("app"))
    val deep = server.createContext("/app/deep", handler("deep"))
    server.start()
    assertEquals("deep", request(server, "/app/deep/item?q=/other")._2)
    assertEquals("app", request(server, "/application")._2)
    assertEquals(404, request(server, "/App")._1)
    server.removeContext(deep)
    assertEquals("app", request(server, "/app/deep/item")._2)
    server.removeContext("/app")
    assertEquals(404, request(server, "/app")._1)
    assertThrows(classOf[IllegalArgumentException], () => server.removeContext("/missing"))
  }

  @Test def filtersAndExchangeMetadata(): Unit = withServer { server =>
    val failure = new AtomicReference[Throwable]()
    server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val context = server.createContext("/echo", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        try {
          assertEquals(-1, exchange.getResponseCode())
          assertEquals("POST", exchange.getRequestMethod())
          assertEquals("HTTP/1.1", exchange.getProtocol())
          assertEquals("/echo?value=%2F", exchange.getRequestURI().toString())
          assertEquals(server.getAddress().getAddress().getHostAddress(),
            exchange.getLocalAddress().getAddress().getHostAddress())
          assertEquals(server.getAddress().getPort(), exchange.getLocalAddress().getPort())
          assertTrue(exchange.getRemoteAddress().getPort() > 0)
          assertNull(exchange.getPrincipal())
          assertEquals("default", exchange.getAttribute("configured"))
          assertEquals("filtered", exchange.getAttribute("stage"))
          assertThrows(classOf[NullPointerException], () => exchange.getAttribute(null))
          assertThrows(classOf[UnsupportedOperationException], () => exchange.getRequestHeaders().set("X-New", "value"))
          assertEquals("one", exchange.getRequestHeaders().get("x-repeat").get(0))
          assertEquals("two", exchange.getRequestHeaders().get("x-repeat").get(1))
          val input = exchange.getRequestBody()
          assertSame(input, exchange.getRequestBody())
          exchange.setStreams(null, null)
          assertSame(input, exchange.getRequestBody())
          val body = readAll(input)
          exchange.getResponseHeaders().set("X-Checked", "yes")
          exchange.sendResponseHeaders(201, body.length)
          assertEquals(201, exchange.getResponseCode())
          assertSame(exchange.getResponseBody(), exchange.getResponseBody())
          exchange.getResponseBody().write(body)
          exchange.close()
        } catch { case error: Throwable => failure.set(error); throw error }
      }
    })
    context.getAttributes().put("configured", "default")
    context.getFilters().add(new Filter {
      override def description(): String = "exchange attributes"
      override def doFilter(exchange: HttpExchange, chain: Filter.Chain): Unit = {
        exchange.setAttribute("stage", "filtered")
        chain.doFilter(exchange)
      }
    })
    server.start()
    val response = request(server, "/echo?value=%2F", "POST", "data", "X-Repeat: one\r\nX-Repeat: two\r\n")
    if (failure.get() != null) throw failure.get()
    assertEquals(201, response._1)
    assertEquals("data", response._2)
    assertEquals("yes", response._3("x-checked"))
  }

  @Test def reusesConnectionForSequentialRequests(): Unit = withServer { server =>
    server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        val body = readAll(exchange.getRequestBody())
        exchange.getResponseHeaders().set("X-Remote-Port", exchange.getRemoteAddress().getPort().toString())
        exchange.sendResponseHeaders(200, body.length)
        exchange.getResponseBody().write(body)
        exchange.getResponseBody().close()
      }
    })
    server.start()
    withSocket(server) { socket =>
      send(socket, "/first", "POST", "first", "", close = false)
      val first = receive(socket.getInputStream())
      send(socket, "/second", "POST", "second", "", close = true)
      val second = receive(socket.getInputStream())
      assertEquals(200, first._1)
      assertEquals(200, second._1)
      assertEquals("first", first._2)
      assertEquals("second", second._2)
      assertEquals(socket.getLocalPort().toString(), first._3("x-remote-port"))
      assertEquals(first._3("x-remote-port"), second._3("x-remote-port"))
    }
  }

  private def withServer(body: HttpServer => Unit): Unit = {
    val server = HttpServer.create()
    try body(server) finally server.stop(0)
  }

  private def withSocket[A](server: HttpServer)(body: Socket => A): A = {
    val socket = new Socket()
    try {
      socket.connect(server.getAddress(), 3000)
      socket.setSoTimeout(3000)
      body(socket)
    } finally socket.close()
  }

  private def handler(text: String): HttpHandler = new HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val bytes = text.getBytes(ISO_8859_1)
      exchange.sendResponseHeaders(200, bytes.length)
      exchange.getResponseBody().write(bytes)
      exchange.close()
    }
  }

  private def request(server: HttpServer, path: String, method: String = "GET",
      body: String = "", headers: String = ""): (Int, String, Map[String, String]) = withSocket(server) { socket =>
    send(socket, path, method, body, headers, close = true)
    receive(socket.getInputStream())
  }

  private def send(socket: Socket, path: String, method: String, body: String,
      headers: String, close: Boolean): Unit = {
    val connection = if (close) "Connection: close\r\n" else ""
    val bytes = body.getBytes(ISO_8859_1)
    val head = method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n" + connection +
      "Content-Length: " + bytes.length + "\r\n" + headers + "\r\n"
    socket.getOutputStream().write(head.getBytes(ISO_8859_1))
    if (bytes.nonEmpty) socket.getOutputStream().write(bytes)
    socket.getOutputStream().flush()
  }

  // All successful handlers in this suite send fixed lengths. Unknown-path
  // responses use Connection: close, allowing the JDK's own error body format.
  private def receive(input: InputStream): (Int, String, Map[String, String]) = {
    val status = line(input).split(" ")(1).toInt
    var headers = Map.empty[String, String]
    var field = line(input)
    while (field.nonEmpty) {
      val colon = field.indexOf(':')
      assertTrue("header separator", colon > 0)
      val name = field.take(colon).map(c => if (c >= 'A' && c <= 'Z') (c + 32).toChar else c)
      headers += name -> field.drop(colon + 1).trim
      field = line(input)
    }
    val body = headers.get("content-length") match {
      case Some(length) =>
        val bytes = new Array[Byte](length.toInt)
        new DataInputStream(input).readFully(bytes)
        bytes
      case None => readAll(input)
    }
    (status, new String(body, ISO_8859_1), headers)
  }

  private def line(input: InputStream): String = {
    val bytes = new ByteArrayOutputStream()
    var previous = -1
    var current = input.read()
    while (current != -1 && !(previous == '\r' && current == '\n')) {
      bytes.write(current)
      assertTrue("bounded response line", bytes.size() < 8192)
      previous = current
      current = input.read()
    }
    assertTrue("complete response line", current != -1)
    new String(bytes.toByteArray(), ISO_8859_1).dropRight(1)
  }

  private def readAll(input: InputStream): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    var value = input.read()
    while (value != -1) { bytes.write(value); value = input.read() }
    bytes.toByteArray()
  }
}
