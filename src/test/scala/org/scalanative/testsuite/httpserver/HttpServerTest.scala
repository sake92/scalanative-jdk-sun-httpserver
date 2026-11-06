package org.scalanative.testsuite.httpserver

import java.io.{ByteArrayOutputStream, DataInputStream, FilterInputStream, FilterOutputStream, IOException, InputStream}
import java.net.{InetAddress, InetSocketAddress, Socket, SocketException}
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.{ArrayList, HashMap, List => JList}
import java.util.concurrent.{CountDownLatch, Executor, Executors, RejectedExecutionException, ThreadFactory, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import com.sun.net.httpserver._
import org.junit.Assert._
import org.junit.Test

class HttpServerTest {
  @Test def stalledInitialHeadExpiresAndServerRecovers(): Unit = withHeaderTimeout("300") {
    withServer { server =>
      server.createContext("/", textHandler("ready"))
      server.start()
      withClient(server)(assertConnectionClosed)
      assertEquals("ready", request(server).body)
    }
  }

  @Test def initialHeadHasAnAbsoluteDeadline(): Unit = drippingHeadExpires(false)

  @Test def subsequentHeadHasAnAbsoluteDeadline(): Unit = drippingHeadExpires(true)

  @Test def acceptsSlowCompleteInitialHeadWithinDeadline(): Unit = slowCompleteHeadSucceeds(false)

  @Test def acceptsSlowCompleteSubsequentHeadWithinDeadline(): Unit = slowCompleteHeadSucceeds(true)

  @Test def idlePersistentHeadExpires(): Unit = withHeaderTimeout("300") {
    withServer { server =>
      server.createContext("/", textHandler("ready"))
      server.start()
      withClient(server) { socket =>
        socket.getOutputStream().write(requestBytes("/"))
        assertEquals("ready", readResponse(socket.getInputStream()).body)
        assertConnectionClosed(socket)
      }
    }
  }

  @Test def headerDeadlineDoesNotApplyToBodiesOrLaterHeads(): Unit = withHeaderTimeout("300") {
    withServer { server =>
      val entered = new CountDownLatch(1)
      server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          entered.countDown()
          respond(exchange, new String(readAll(exchange.getRequestBody()), ISO_8859_1))
        }
      })
      server.start()
      withClient(server) { socket =>
        val output = socket.getOutputStream()
        output.write(requestBytes("/", "POST", "Content-Length: 1\r\n"))
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        Thread.sleep(600)
        output.write('a')
        assertEquals("a", readResponse(socket.getInputStream()).body)
        output.write(requestBytes("/", "POST", "Connection: close\r\nContent-Length: 1\r\n", "b"))
        assertEquals("b", readResponse(socket.getInputStream()).body)
        assertConnectionClosed(socket)
      }
    }
  }

  @Test def validatesAndSnapshotsHeaderTimeout(): Unit = {
    Seq("0", "-1", "invalid", "2147483648", "").foreach { value =>
      withHeaderTimeout(value) {
        assertThrows(classOf[IllegalArgumentException], () => HttpServer.create())
      }
    }
    withHeaderTimeout("300") {
      withServer { server =>
        System.setProperty("org.scalanative.httpserver.headerTimeoutMillis", "30000")
        server.start()
        withClient(server)(assertConnectionClosed)
      }
    }
  }

  private def withHeaderTimeout(value: String)(test: => Unit): Unit = {
    val property = "org.scalanative.httpserver.headerTimeoutMillis"
    val previous = System.getProperty(property)
    System.setProperty(property, value)
    try test
    finally {
      if (previous == null) System.clearProperty(property)
      else System.setProperty(property, previous)
    }
  }

  private def slowCompleteHeadSucceeds(subsequent: Boolean): Unit = withHeaderTimeout("2000") {
    withServer { server =>
      val calls = new AtomicInteger()
      server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = { calls.incrementAndGet(); respond(exchange, "ready") }
      })
      server.start()
      withClient(server) { socket =>
        val output = socket.getOutputStream()
        if (subsequent) {
          output.write(requestBytes("/"))
          assertEquals("ready", readResponse(socket.getInputStream()).body)
        }
        val fragments = Seq("GET / HTTP/1.1\r\n", "Host: x\r\n", "Connection: close\r\n", "\r\n")
        output.write(fragments.head.getBytes(ISO_8859_1))
        fragments.tail.foreach { fragment =>
          Thread.sleep(300)
          output.write(fragment.getBytes(ISO_8859_1))
        }
        assertEquals("ready", readResponse(socket.getInputStream()).body)
        assertConnectionClosed(socket)
        val expectedCalls = if (subsequent) 2 else 1
        assertEquals(expectedCalls, calls.get())
      }
    }
  }

  private def drippingHeadExpires(subsequent: Boolean): Unit = withHeaderTimeout("600") {
    withServer { server =>
      val calls = new AtomicInteger()
      server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = { calls.incrementAndGet(); respond(exchange, "ready") }
      })
      server.start()
      val sender = Executors.newSingleThreadScheduledExecutor()
      try {
        withClient(server) { socket =>
          val output = socket.getOutputStream()
          if (subsequent) {
            output.write(requestBytes("/"))
            assertEquals("ready", readResponse(socket.getInputStream()).body)
          }
          output.write("GET / HTTP/1.1\r\nHost: x\r\nX-Slow: ".getBytes(ISO_8859_1))
          val ticks = new AtomicInteger()
          val drip = sender.scheduleAtFixedRate(new Runnable {
            override def run(): Unit = {
              try { output.write('a'); ticks.incrementAndGet() }
              catch { case _: IOException => () }
            }
          }, 0, 50, TimeUnit.MILLISECONDS)
          try {
            assertConnectionClosed(socket)
            assertTrue("Client did not make progress before expiry", ticks.get() >= 3)
            val expectedCalls = if (subsequent) 1 else 0
            assertEquals(expectedCalls, calls.get())
          } finally drip.cancel(true)
        }
      } finally {
        sender.shutdownNow()
        assertTrue(sender.awaitTermination(3, TimeUnit.SECONDS))
      }
    }
  }

  @Test def factoriesAndLifecycle(): Unit = {
    val server = HttpServer.create()
    try {
      assertNull(server.getAddress())
      assertNull(server.getExecutor())
      assertThrows(classOf[IllegalStateException], () => server.start())
      assertThrows(classOf[NullPointerException], () => server.bind(null, 0))
      server.bind(loopback(), -1)
      assertTrue(server.getAddress().getPort() > 0)
      assertThrows(classOf[IOException], () => server.bind(loopback(), 0))
      val occupied = HttpServer.create()
      try {
        assertThrows(classOf[IOException], () => occupied.bind(server.getAddress(), 0))
        occupied.bind(loopback(), 0)
        assertTrue(occupied.getAddress().getPort() > 0)
      } finally occupied.stop(0)
      server.createContext("/", textHandler("ready"))
      server.start()
      assertEquals("ready", request(server).body)
      assertThrows(classOf[IllegalStateException], () => server.start())
      assertThrows(classOf[IllegalStateException], () => server.setExecutor(null))
      assertThrows(classOf[IllegalArgumentException], () => server.stop(-1))
    } finally server.stop(0)
    server.stop(0)
    assertThrows(classOf[IllegalStateException], () => server.start())
    assertThrows(classOf[IllegalStateException], () => server.bind(loopback(), 0))
    val unbound = HttpServer.create(null, 0)
    try assertNull(unbound.getAddress()) finally unbound.stop(0)
  }

  @Test def handlesServerWideOptionsWithoutSelectingAContext(): Unit = withServer { server =>
    val calls = new AtomicInteger()
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        calls.incrementAndGet()
        exchange.sendResponseHeaders(500, -1)
      }
    })
    server.start()
    val response = request(server, path = "*", method = "OPTIONS")
    assertEquals(200, response.status)
    assertEquals("", response.body)
    assertEquals(Some(Vector("0")), response.headers.get("content-length"))
    assertEquals(0, calls.get())
  }

  @Test def routesLiteralPathsAndUpdatesContexts(): Unit = withServer { server =>
    val root = server.createContext("/app", textHandler("app"))
    server.createContext("/app/deep", textHandler("deep"))
    server.createContext("/app/%66oo", textHandler("encoded"))
    val late = server.createContext("/late")
    assertSame(server, root.getServer())
    assertEquals("/app", root.getPath())
    assertThrows(classOf[IllegalArgumentException], () => server.createContext("app", textHandler("x")))
    assertThrows(classOf[NullPointerException], () => server.createContext(null: String))
    assertThrows(classOf[NullPointerException], () => server.createContext("/null", null))
    assertThrows(classOf[NullPointerException], () => late.setHandler(null))
    late.setHandler(textHandler("late"))
    assertThrows(classOf[IllegalArgumentException], () => late.setHandler(textHandler("again")))
    server.start()
    assertEquals("deep", request(server, "/app/deep/x?q=/other").body)
    assertEquals("app", request(server, "/application").body)
    assertEquals("encoded", request(server, "/app/%66oo").body)
    assertEquals("app", request(server, "/app/foo").body)
    assertEquals(404, request(server, "/App").status)
    assertEquals("late", request(server, "/late").body)
    server.removeContext(root)
    assertEquals(404, request(server, "/app").status)
    server.removeContext("/late")
    assertEquals(404, request(server, "/late").status)
    assertThrows(classOf[IllegalArgumentException], () => server.removeContext(root))
    assertThrows(classOf[IllegalArgumentException], () => server.removeContext("/missing"))
  }

  @Test def servesRequestAndResponseFraming(): Unit = withServer { server =>
    val handlerFailure = new AtomicReference[Throwable]()
    server.createContext("/echo", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        try {
          assertEquals(-1, exchange.getResponseCode())
          assertEquals(server.getAddress().getPort(), exchange.getLocalAddress().getPort())
          assertEquals(server.getAddress().getAddress().getHostAddress(), exchange.getLocalAddress().getAddress().getHostAddress())
          assertTrue(exchange.getRemoteAddress().getPort() > 0)
          assertThrows(classOf[IOException], () => exchange.getResponseBody().write('x'))
          val body = readAll(exchange.getRequestBody())
          exchange.getResponseHeaders().set("X-Method", exchange.getRequestMethod())
          exchange.sendResponseHeaders(200, body.length)
          exchange.getResponseBody().write(body)
          exchange.getResponseBody().close()
        } catch { case error: Throwable => handlerFailure.set(error); throw error }
      }
    })
    server.createContext("/chunk", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        exchange.sendResponseHeaders(200, 0)
        exchange.getResponseBody().write("ab".getBytes(ISO_8859_1))
        exchange.getResponseBody().flush()
        exchange.getResponseBody().write('c')
      }
    })
    server.createContext("/none", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = exchange.sendResponseHeaders(204, 8)
    })
    server.createContext("/head", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = exchange.sendResponseHeaders(200, 123)
    })
    server.start()
    val fixed = request(server, "/echo", "POST", "Content-Length: 4\r\n", "data")
    if (handlerFailure.get() != null) throw handlerFailure.get()
    assertEquals("data", fixed.body)
    assertEquals("POST", fixed.headers("x-method").head)
    assertEquals("close", fixed.headers("connection").head)
    val chunked = request(server, "/echo", "POST", "Transfer-Encoding: chunked\r\n",
      "2;name=value\r\nab\r\n1\r\nc\r\n0\r\nX-End: ignored\r\n\r\n")
    assertEquals("abc", chunked.body)
    val chunks = request(server, "/chunk")
    assertEquals("abc", chunks.body)
    assertEquals("chunked", chunks.headers("transfer-encoding").head)
    assertEquals("abc", request(server, "/chunk", version = "HTTP/1.0").body)
    val none = request(server, "/none")
    assertEquals(204, none.status)
    assertEquals("", none.body)
    assertFalse(none.headers.contains("content-length"))
    val head = request(server, "/head", "HEAD")
    assertEquals("123", head.headers("content-length").head)
    assertEquals("", head.body)
  }

  @Test def requestHeadersAreDeeplyReadOnly(): Unit = withServer { server =>
    val handlerFailure = new AtomicReference[Throwable]()
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        try {
          val headers = exchange.getRequestHeaders()
          val values = new ArrayList[String]()
          values.add("replacement")
          val mutations: List[() => Any] = List(
            () => headers.add("X-List", "c"), () => headers.set("X-List", "c"),
            () => headers.put("X-List", values), () => headers.remove("missing"), () => headers.clear(),
            () => headers.putAll(new HashMap[String, JList[String]]()),
            () => headers.get("X-List").add("c"), () => headers.keySet().remove("missing"),
            () => headers.values().clear(), () => headers.entrySet().iterator().next().setValue(values),
            () => { val iterator = headers.keySet().iterator(); iterator.next(); iterator.remove() },
            () => headers.putIfAbsent("Host", values),
            () => headers.remove("missing", values), () => headers.replace("missing", values),
            () => headers.replace("missing", values, values),
            () => headers.replaceAll((key: String, current: JList[String]) => values),
            () => headers.computeIfAbsent("Host", (key: String) => values),
            () => headers.computeIfPresent("missing", (key: String, current: JList[String]) => values),
            () => headers.compute("missing", (key: String, current: JList[String]) => values),
            () => headers.merge("Host", values, (first: JList[String], second: JList[String]) => values)
          )
          mutations.zipWithIndex.foreach { case (action, index) =>
            assertThrows("mutation " + index, classOf[UnsupportedOperationException], () => { action(); () })
          }
          assertEquals(2, headers.get("x-list").size())
          exchange.getResponseHeaders().set("X-Checked", "yes")
          respond(exchange, headers.get("X-List").get(0) + headers.get("X-List").get(1))
        } catch { case error: Throwable => handlerFailure.set(error); throw error }
      }
    })
    server.start()
    val response = request(server, fields = "X-List: a\r\nX-List: b\r\n")
    if (handlerFailure.get() != null) throw handlerFailure.get()
    assertEquals(200, response.status)
    assertEquals("ab", response.body)
    assertEquals("yes", response.headers("x-checked").head)
  }

  @Test def filtersAttributesAndWrappedStreams(): Unit = withServer { server =>
    val inputCloses = new AtomicInteger()
    val context = server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        assertEquals("configured", exchange.getAttribute("shared"))
        assertNull(exchange.getAttribute("missing"))
        assertEquals("filter-second", exchange.getAttribute("trace"))
        assertThrows(classOf[NullPointerException], () => exchange.getAttribute(null))
        assertThrows(classOf[NullPointerException], () => exchange.setAttribute(null, "x"))
        exchange.setAttribute("shared", null)
        assertNull(exchange.getAttribute("shared"))
        val body = readAll(exchange.getRequestBody())
        exchange.sendResponseHeaders(200, body.length)
        exchange.getResponseBody().write(body)
      }
    })
    context.getAttributes().put("shared", "configured")
    context.getFilters().add(new Filter {
      override def description(): String = "wrap streams"
      override def doFilter(exchange: HttpExchange, chain: Filter.Chain): Unit = {
        exchange.setAttribute("trace", "filter")
        val original = exchange.getResponseBody()
        val wrapped = new FilterOutputStream(original) {
          override def write(value: Int): Unit = out.write(Character.toUpperCase(value.toChar).toInt)
          override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
            var index = offset
            while (index < offset + length) { write(bytes(index).toInt & 255); index += 1 }
          }
        }
        val wrappedInput = new FilterInputStream(exchange.getRequestBody()) {
          override def close(): Unit = { inputCloses.incrementAndGet(); super.close() }
        }
        exchange.setStreams(wrappedInput, wrapped)
        exchange.setStreams(null, null)
        assertSame(wrapped, exchange.getResponseBody())
        chain.doFilter(exchange)
      }
    })
    context.getFilters().add(new Filter {
      override def description(): String = "second filter"
      override def doFilter(exchange: HttpExchange, chain: Filter.Chain): Unit = {
        exchange.setAttribute("trace", exchange.getAttribute("trace").toString + "-second")
        chain.doFilter(exchange)
      }
    })
    server.start()
    val first = request(server, method = "POST", fields = "Content-Length: 3\r\n", body = "abc")
    assertEquals("ABC", first.body)
    assertEquals("configured", context.getAttributes().get("shared"))
    assertEquals("DEF", request(server, method = "POST", fields = "Content-Length: 3\r\n", body = "def").body)
    assertEquals(2, inputCloses.get())
  }

  @Test def exchangeCloseHonorsStreamOrder(): Unit = withServer { server =>
    val trace = new AtomicReference[String]("")
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        val input = new FilterInputStream(exchange.getRequestBody()) {
          override def close(): Unit = {
            trace.set(trace.get() + "input;")
            super.close()
          }
        }
        val output = new FilterOutputStream(exchange.getResponseBody()) {
          override def close(): Unit = {
            trace.set(trace.get() + "output;")
            super.close()
          }
        }
        exchange.setStreams(input, output)
        exchange.sendResponseHeaders(200, 1)
        exchange.getResponseBody().write('x')
        exchange.close()
        exchange.close()
      }
    })
    server.start()
    assertEquals("x", request(server).body)
    assertEquals("input;output;", trace.get())
  }

  @Test def authenticatesRealExchanges(): Unit = withServer { server =>
    val calls = new AtomicInteger()
    val context = server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        calls.incrementAndGet()
        val principal = exchange.getPrincipal()
        respond(exchange, if (principal == null) "anonymous" else principal.getName())
      }
    })
    val auth = new BasicAuthenticator("users") {
      override def checkCredentials(user: String, password: String): Boolean = user == "alice" && password == "secret"
    }
    assertNull(context.setAuthenticator(auth))
    server.start()
    val retry = request(server, method = "POST", fields = "Content-Length: 1\r\nExpect: 100-continue\r\n")
    assertEquals(401, retry.status)
    assertEquals("Basic realm=\"users\"", retry.headers("www-authenticate").head)
    assertEquals(401, request(server, fields = "Authorization: Basic YWxpY2U6d3Jvbmc=\r\n").status)
    assertEquals(0, calls.get())
    assertEquals("alice", request(server, fields = "Authorization: Basic YWxpY2U6c2VjcmV0\r\n").body)
    assertEquals(1, calls.get())
    assertSame(auth, context.setAuthenticator(null))
    assertEquals("anonymous", request(server).body)
  }

  @Test def handlesExpectContinue(): Unit = withServer { server =>
    val reads = new AtomicInteger()
    server.createContext("/read", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        reads.incrementAndGet()
        respond(exchange, new String(readAll(exchange.getRequestBody()), ISO_8859_1))
      }
    })
    server.createContext("/reject", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = exchange.sendResponseHeaders(403, -1)
    })
    server.start()
    withClient(server) { socket =>
      socket.getOutputStream().write("POST /read HTTP/1.1\r\nHost: x\r\nContent-Length: 3\r\nExpect: 100-continue\r\n\r\n".getBytes(ISO_8859_1))
      assertEquals(100, readResponse(socket.getInputStream()).status)
      socket.getOutputStream().write("abc".getBytes(ISO_8859_1))
      assertEquals("abc", readResponse(socket.getInputStream()).body)
    }
    val early = request(server, "/reject", "POST", "Content-Length: 99\r\nExpect: 100-continue\r\n")
    assertEquals(403, early.status)
    val bad = request(server, "/read", "POST", "Content-Length: 1\r\nExpect: other\r\n")
    assertEquals(417, bad.status)
    assertEquals(1, reads.get())
  }

  @Test def isolatesProtocolAndHandlerFailures(): Unit = withServer { server =>
    server.createContext("/ok", textHandler("ok"))
    server.createContext("/fail", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = throw new IOException("handler failed")
    })
    server.createContext("/read", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = respond(exchange, new String(readAll(exchange.getRequestBody()), ISO_8859_1))
    })
    server.createContext("/over", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        exchange.sendResponseHeaders(200, 1)
        exchange.getResponseBody().write("excess".getBytes(ISO_8859_1))
      }
    })
    server.createContext("/short", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        exchange.sendResponseHeaders(200, 3)
        exchange.getResponseBody().write('x')
        exchange.getResponseBody().close()
      }
    })
    server.createContext("/twice", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        exchange.sendResponseHeaders(200, 2)
        assertThrows(classOf[IOException], () => exchange.sendResponseHeaders(201, 2))
        exchange.getResponseBody().write("ok".getBytes(ISO_8859_1))
      }
    })
    server.start()
    assertEquals(500, request(server, "/fail").status)
    assertEquals("ok", request(server, "/twice").body)
    for (wire <- List("GET /ok HTTP/1.1\r\n\r\n", "POST /read HTTP/1.1\r\nHost: x\r\nContent-Length: 2\r\nContent-Length: 3\r\n\r\n")) {
      withClient(server) { socket =>
        socket.getOutputStream().write(wire.getBytes(ISO_8859_1))
        assertEquals(400, readResponse(socket.getInputStream()).status)
      }
    }
    assertEquals(400, request(server, "/read", "POST", "Transfer-Encoding: chunked\r\n", "bad-size\r\n").status)
    withClient(server) { socket =>
      socket.getOutputStream().write("POST /read HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nx".getBytes(ISO_8859_1))
      socket.shutdownOutput()
      assertEquals(400, readResponse(socket.getInputStream()).status)
    }
    for (path <- List("/over", "/short")) withClient(server) { socket =>
      socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: x\r\n\r\n").getBytes(ISO_8859_1))
      assertConnectionClosed(socket)
    }
    withClient(server) { socket =>
      socket.getOutputStream().write("POST /read HTTP/1.1\r\nHost: x\r\nContent-Length: 100\r\n\r\nx".getBytes(ISO_8859_1))
    }
    assertEquals("ok", request(server, "/ok").body)
  }

  @Test def usesCallerExecutorWithoutOwningIt(): Unit = withServer { server =>
    val connected = new CountDownLatch(1)
    val pool = Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(task: Runnable): Thread = new Thread(task, "test-http-worker")
    })
    try {
      val attempts = new AtomicInteger()
      server.setExecutor(new Executor {
        override def execute(task: Runnable): Unit = {
          if (attempts.getAndIncrement() == 0) {
            // Native's timed connect can report refusal if the peer closes before connect returns.
            assertTrue(connected.await(3, TimeUnit.SECONDS))
            throw new RejectedExecutionException("first request rejected")
          }
          pool.execute(task)
        }
      })
      server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = respond(exchange, Thread.currentThread().getName())
      })
      server.start()
      withClient(server) { socket =>
        connected.countDown()
        assertConnectionClosed(socket)
      }
      assertEquals("test-http-worker", request(server).body)
      server.stop(0)
      assertFalse(pool.isShutdown())
      assertEquals(42, pool.submit(new java.util.concurrent.Callable[Int] {
        override def call(): Int = 42
      }).get(3, TimeUnit.SECONDS))
    } finally {
      connected.countDown()
      pool.shutdownNow()
      assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS))
    }
  }

  @Test def stopClosesBlockedConnections(): Unit = {
    withServer { server =>
      val accepted = new CountDownLatch(1)
      server.setExecutor(new Executor {
        override def execute(task: Runnable): Unit = { accepted.countDown(); task.run() }
      })
      server.createContext("/", textHandler("unused"))
      server.start()
      withClient(server) { socket =>
        socket.getOutputStream().write("GET / HTTP/1.1\r\n".getBytes(ISO_8859_1))
        assertTrue(accepted.await(3, TimeUnit.SECONDS))
        Thread.currentThread().interrupt()
        server.stop(0)
        assertTrue(Thread.interrupted())
        assertConnectionClosed(socket)
      }
    }
    withServer { server =>
      val entered = new CountDownLatch(1)
      val thread = new AtomicReference[Thread]()
      server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          thread.set(Thread.currentThread())
          entered.countDown()
          exchange.getRequestBody().read()
        }
      })
      server.start()
      withClient(server) { socket =>
        socket.getOutputStream().write("POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 100\r\n\r\n".getBytes(ISO_8859_1))
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        server.stop(0)
        assertConnectionClosed(socket)
        thread.get().join(1000)
        assertFalse(thread.get().isAlive())
      }
    }
  }

  @Test def stopHonorsGraceAndCallerOwnership(): Unit = withServer { server =>
    val pool = Executors.newSingleThreadExecutor()
    val entered = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    val stopping = new CountDownLatch(1)
    val stopped = new CountDownLatch(1)
    val stopFailure = new AtomicReference[Throwable]()
    val stopper = new Thread(new Runnable {
      override def run(): Unit = {
        stopping.countDown()
        try server.stop(2)
        catch { case error: Throwable => stopFailure.set(error) }
        finally stopped.countDown()
      }
    })
    try {
      server.setExecutor(pool)
      server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          entered.countDown()
          if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test release timed out")
          respond(exchange, "finished")
        }
      })
      server.start()
      withClient(server) { socket =>
        socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(ISO_8859_1))
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        stopper.start()
        assertTrue(stopping.await(3, TimeUnit.SECONDS))
        assertFalse(stopped.await(100, TimeUnit.MILLISECONDS))
        release.countDown()
        assertEquals("finished", readResponse(socket.getInputStream()).body)
        assertTrue(stopped.await(3, TimeUnit.SECONDS))
        assertNull(stopFailure.get())
        assertFalse(pool.isShutdown())
      }
    } finally {
      release.countDown()
      stopper.join(3000)
      pool.shutdownNow()
      assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS))
    }
    withServer { queuedServer =>
      val queued = new CountDownLatch(1)
      val task = new AtomicReference[Runnable]()
      val calls = new AtomicInteger()
      queuedServer.setExecutor(new Executor {
        override def execute(value: Runnable): Unit = { task.set(value); queued.countDown() }
      })
      queuedServer.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = { calls.incrementAndGet(); respond(exchange, "wrong") }
      })
      queuedServer.start()
      withClient(queuedServer) { socket =>
        assertTrue(queued.await(3, TimeUnit.SECONDS))
        queuedServer.stop(0)
        task.get().run()
        assertConnectionClosed(socket)
        assertEquals(0, calls.get())
      }
    }
  }

  @Test def reusesHttp11ConnectionsAndHonorsCloseTokens(): Unit = withServer { server =>
    val calls = new AtomicInteger()
    val oldInput = new AtomicReference[InputStream]()
    val oldOutput = new AtomicReference[java.io.OutputStream]()
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        val call = calls.incrementAndGet()
        assertEquals(-1, exchange.getResponseCode())
        assertNull(exchange.getAttribute("previous"))
        exchange.setAttribute("previous", "local")
        if (oldInput.get() != null) {
          assertThrows(classOf[IOException], () => oldInput.get().read())
          assertThrows(classOf[IOException], () => oldOutput.get().write('x'))
        }
        oldInput.set(exchange.getRequestBody())
        oldOutput.set(exchange.getResponseBody())
        if (exchange.getRequestURI().getPath() == "/server-close")
          exchange.getResponseHeaders().set("Connection", "keep-alive, ClOsE")
        respond(exchange, exchange.getRequestURI().getPath() + ":" + exchange.getRemoteAddress().getPort() + ":" + call)
      }
    })
    server.start()
    withClient(server) { socket =>
      socket.getOutputStream().write(requestBytes("/first"))
      val first = readResponse(socket.getInputStream())
      assertFalse(first.headers.getOrElse("connection", Vector.empty).exists(_.equalsIgnoreCase("close")))
      socket.getOutputStream().write(requestBytes("/second", fields = "Connection: Keep-Alive\r\n"))
      val second = readResponse(socket.getInputStream())
      assertEquals(first.body.split(":")(1), second.body.split(":")(1))
      assertTrue(second.body.startsWith("/second:"))
      socket.getOutputStream().write(requestBytes("/server-close") ++ requestBytes("/ignored"))
      val last = readResponse(socket.getInputStream())
      assertTrue(last.body.startsWith("/server-close:"))
      assertEquals("close", last.headers("connection").head)
      assertConnectionClosed(socket)
      assertEquals(3, calls.get())
    }
    withClient(server) { socket =>
      socket.getOutputStream().write(requestBytes("/client-close", fields =
        "Connection: keep-alive\r\nConnection: other, cLoSe\r\n") ++ requestBytes("/ignored"))
      assertEquals("close", readResponse(socket.getInputStream()).headers("connection").head)
      assertConnectionClosed(socket)
      assertEquals(4, calls.get())
    }
    withClient(server) { socket =>
      socket.getOutputStream().write(requestBytes("/invalid", fields = "Connection: not a token\r\n"))
      assertEquals(400, readResponse(socket.getInputStream()).status)
      assertConnectionClosed(socket)
      assertEquals(4, calls.get())
    }
  }

  @Test def pipelinesMixedFramingInOrder(): Unit = withServer { server =>
    val calls = new AtomicInteger()
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        calls.incrementAndGet()
        val path = exchange.getRequestURI().getPath()
        if (path == "/head") exchange.sendResponseHeaders(200, 17)
        else {
          val payload = path + ":" + new String(readAll(exchange.getRequestBody()), ISO_8859_1)
          val bytes = payload.getBytes(ISO_8859_1)
          val length = if (path == "/chunked") 0L else bytes.length.toLong
          exchange.sendResponseHeaders(200, length)
          exchange.getResponseBody().write(bytes)
        }
      }
    })
    server.start()
    withClient(server) { socket =>
      val wire = requestBytes("/fixed", "POST", "Content-Length: 3\r\n", "abc") ++
        requestBytes("/chunked", "POST", "Transfer-Encoding: chunked\r\n",
          "2\r\nde\r\n1\r\nf\r\n0\r\nX-End: ignored\r\n\r\n") ++
        requestBytes("/head", "HEAD") ++ requestBytes("/last", fields = "Connection: close\r\n")
      socket.getOutputStream().write(wire)
      val input = socket.getInputStream()
      assertEquals("/fixed:abc", readResponse(input).body)
      val second = readResponse(input)
      assertEquals("/chunked:def", second.body)
      assertEquals("chunked", second.headers("transfer-encoding").head)
      val head = readResponse(input, headOnly = true)
      assertEquals("17", head.headers("content-length").head)
      assertEquals("/last:", readResponse(input).body)
      assertConnectionClosed(socket)
      assertEquals(4, calls.get())
    }
  }

  @Test def http10RequiresExplicitKeepAlive(): Unit = withServer { server =>
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        if (exchange.getRequestURI().getPath() == "/unknown") {
          exchange.sendResponseHeaders(200, 0)
          exchange.getResponseBody().write("unknown".getBytes(ISO_8859_1))
        } else respond(exchange, "fixed")
      }
    })
    server.start()
    withClient(server) { socket =>
      socket.getOutputStream().write(requestBytes("/", version = "HTTP/1.0"))
      assertEquals("close", readResponse(socket.getInputStream()).headers("connection").head)
      assertConnectionClosed(socket)
    }
    withClient(server) { socket =>
      socket.getOutputStream().write(requestBytes("/", fields = "Connection: KEEP-ALIVE\r\n", version = "HTTP/1.0"))
      val first = readResponse(socket.getInputStream())
      assertEquals("fixed", first.body)
      assertEquals("keep-alive", first.headers("connection").head)
      socket.getOutputStream().write(requestBytes("/unknown", fields = "Connection: keep-alive\r\n", version = "HTTP/1.0"))
      val last = readResponse(socket.getInputStream())
      assertEquals("unknown", last.body)
      assertEquals("close", last.headers("connection").head)
      assertFalse(last.headers.contains("content-length"))
      assertFalse(last.headers.contains("transfer-encoding"))
      assertConnectionClosed(socket)
    }
  }

  @Test def drainsUnreadAndClosedBodies(): Unit = withServer { server =>
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        val path = exchange.getRequestURI().getPath()
        if (path == "/partial") {
          assertEquals('a'.toInt, exchange.getRequestBody().read())
          exchange.getRequestBody().close()
          assertThrows(classOf[IOException], () => exchange.getRequestBody().read())
        }
        respond(exchange, if (path == "/next") "next" else "done")
      }
    })
    server.start()
    val firstRequests = List(
      requestBytes("/partial", "POST", "Content-Length: 5\r\n", "abcde"),
      requestBytes("/ignore", "POST", "Transfer-Encoding: chunked\r\n", "2\r\nab\r\n3\r\ncde\r\n0\r\nX-End: y\r\n\r\n"),
      requestBytes("/ignore", "POST", "Content-Length: 65536\r\n", "a" * 65536)
    )
    firstRequests.foreach { first =>
      withClient(server) { socket =>
        socket.getOutputStream().write(first ++ requestBytes("/next", fields = "Connection: close\r\n"))
        assertEquals("done", readResponse(socket.getInputStream()).body)
        assertEquals("next", readResponse(socket.getInputStream()).body)
        assertConnectionClosed(socket)
      }
    }
  }

  @Test def closesWhenDrainBudgetOrBodyFramingFails(): Unit = withServer { server =>
    val calls = new AtomicInteger()
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        calls.incrementAndGet()
        if (exchange.getRequestURI().getPath() == "/expect") exchange.sendResponseHeaders(403, -1)
        else respond(exchange, "done")
      }
    })
    server.start()
    val unsafe = List(
      requestBytes("/ignore", "POST", "Content-Length: 65537\r\n", "a" * 65537),
      requestBytes("/ignore", "POST", "Transfer-Encoding: chunked\r\n", ("1\r\nx\r\n" * 12000) + "0\r\n\r\n"),
      requestBytes("/ignore", "POST", "Transfer-Encoding: chunked\r\n", "1\r\nxX\n0\r\n\r\n")
    )
    unsafe.foreach { first =>
      val before = calls.get()
      withClient(server) { socket =>
        socket.getOutputStream().write(first ++ requestBytes("/must-not-run", fields = "Connection: close\r\n"))
        assertEquals("done", readResponse(socket.getInputStream()).body)
        assertConnectionClosed(socket)
        assertEquals(before + 1, calls.get())
      }
    }
    withClient(server) { socket =>
      socket.getOutputStream().write(requestBytes("/ignore", "POST", "Content-Length: 5\r\n", "a"))
      socket.shutdownOutput()
      assertEquals("done", readResponse(socket.getInputStream()).body)
      assertConnectionClosed(socket)
    }
    withClient(server) { socket =>
      socket.getOutputStream().write(requestBytes("/expect", "POST", "Expect: 100-continue\r\nContent-Length: 100\r\n"))
      val response = readResponse(socket.getInputStream())
      assertEquals(403, response.status)
      assertEquals("close", response.headers("connection").head)
      assertConnectionClosed(socket)
    }
  }

  @Test def slowUnreadBodiesHaveAnAbsoluteDrainDeadline(): Unit = withServer { server =>
    val finishHandler = new CountDownLatch(1)
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        respond(exchange, "done")
        finishHandler.await(3, TimeUnit.SECONDS)
      }
    })
    server.start()
    val sender = Executors.newSingleThreadScheduledExecutor()
    try {
      withClient(server) { socket =>
        socket.setSoTimeout(2500)
        val output = socket.getOutputStream()
        output.write(requestBytes("/", "POST", "Content-Length: 65536\r\n", "a"))
        val ticks = new CountDownLatch(2)
        val drip = sender.scheduleAtFixedRate(new Runnable {
          override def run(): Unit = {
            try { output.write('b'); ticks.countDown() }
            catch { case _: IOException => () }
          }
        }, 0, 100, TimeUnit.MILLISECONDS)
        try {
          assertEquals("done", readResponse(socket.getInputStream()).body)
          assertTrue(ticks.await(2, TimeUnit.SECONDS))
          assertConnectionClosed(socket)
        } finally drip.cancel(true)
      }
    } finally {
      finishHandler.countDown()
      sender.shutdownNow()
      assertTrue(sender.awaitTermination(3, TimeUnit.SECONDS))
    }
  }

  @Test def stopClosesIdlePersistentConnectionsAndSuppressesQueuedRequests(): Unit = {
    withServer { server =>
      val owner = new AtomicReference[Thread]()
      server.createContext("/", new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = { owner.set(Thread.currentThread()); respond(exchange, "done") }
      })
      server.start()
      withClient(server) { socket =>
        socket.getOutputStream().write(requestBytes("/"))
        assertEquals("done", readResponse(socket.getInputStream()).body)
        val start = System.nanoTime()
        server.stop(2)
        assertTrue("Idle connection used the handler grace period", System.nanoTime() - start < 1500000000L)
        assertConnectionClosed(socket)
        owner.get().join(1000)
        assertFalse(owner.get().isAlive())
      }
    }
    withServer { server =>
      val pool = Executors.newSingleThreadExecutor()
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val calls = new AtomicInteger()
      try {
        server.setExecutor(pool)
        server.createContext("/", new HttpHandler {
          override def handle(exchange: HttpExchange): Unit = {
            calls.incrementAndGet()
            entered.countDown()
            release.await(3, TimeUnit.SECONDS)
            respond(exchange, "done")
          }
        })
        server.start()
        withClient(server) { socket =>
          socket.getOutputStream().write(requestBytes("/first") ++ requestBytes("/queued"))
          assertTrue(entered.await(3, TimeUnit.SECONDS))
          server.stop(0)
          release.countDown()
          assertConnectionClosed(socket)
          assertEquals(1, calls.get())
        }
      } finally {
        release.countDown()
        pool.shutdownNow()
        assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS))
      }
    }
  }

  @Test def doesNotReuseOnResponseFailure(): Unit = withServer { server =>
    val calls = new AtomicInteger()
    server.createContext("/", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        calls.incrementAndGet()
        exchange.getRequestURI().getPath() match {
          case "/over" => exchange.sendResponseHeaders(200, 1); exchange.getResponseBody().write(Array[Byte](1, 2))
          case "/short" => exchange.sendResponseHeaders(200, 2); exchange.getResponseBody().write('x')
          case "/throw" => throw new IOException("handler failure")
          case "/after" => respond(exchange, "done"); throw new IOException("after completion")
          case _ => respond(exchange, "unexpected")
        }
      }
    })
    server.start()
    for (path <- List("/over", "/short", "/throw", "/after")) {
      val before = calls.get()
      withClient(server) { socket =>
        socket.getOutputStream().write(requestBytes(path) ++ requestBytes("/must-not-run"))
        if (path == "/throw") {
          val response = readResponse(socket.getInputStream())
          assertEquals(500, response.status)
          assertEquals("close", response.headers("connection").head)
        } else if (path == "/after") assertEquals("done", readResponse(socket.getInputStream()).body)
        assertConnectionClosed(socket)
        assertEquals(before + 1, calls.get())
      }
    }
  }

  private case class Response(status: Int, headers: Map[String, Vector[String]], body: String)

  private def loopback(): InetSocketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)

  private def withServer(test: HttpServer => Unit): Unit = {
    val server = HttpServer.create(loopback(), 0)
    try test(server) finally server.stop(0)
  }

  private def withClient[A](server: HttpServer)(test: Socket => A): A = {
    val socket = new Socket()
    try {
      socket.connect(server.getAddress(), 3000)
      socket.setSoTimeout(3000)
      test(socket)
    } finally socket.close()
  }

  private def request(server: HttpServer, path: String = "/", method: String = "GET",
      fields: String = "", body: String = "", version: String = "HTTP/1.1"): Response = withClient(server) { socket =>
    socket.getOutputStream().write(requestBytes(path, method, "Connection: close\r\n" + fields, body, version))
    val response = readResponse(socket.getInputStream(), method == "HEAD")
    assertConnectionClosed(socket)
    response
  }

  private def requestBytes(path: String, method: String = "GET", fields: String = "",
      body: String = "", version: String = "HTTP/1.1"): Array[Byte] = {
    val wire = method + " " + path + " " + version + "\r\nHost: x\r\n" + fields + "\r\n" + body
    wire.getBytes(ISO_8859_1)
  }

  private def readResponse(input: InputStream, headOnly: Boolean = false): Response = {
    val status = readLine(input).split(" ")(1).toInt
    var headers = Map.empty[String, Vector[String]]
    var line = readLine(input)
    while (line.nonEmpty) {
      val colon = line.indexOf(':')
      assertTrue(colon > 0)
      val name = line.substring(0, colon).map(c => if (c >= 'A' && c <= 'Z') (c + 32).toChar else c)
      val value = line.substring(colon + 1).trim
      headers = headers.updated(name, headers.getOrElse(name, Vector.empty) :+ value)
      line = readLine(input)
    }
    val bytes = if (headOnly || status < 200 || status == 204 || status == 205 || status == 304) new Array[Byte](0)
    else if (headers.get("transfer-encoding").exists(_.contains("chunked"))) readChunked(input)
    else headers.get("content-length") match {
      case Some(lengths) =>
        val result = new Array[Byte](lengths.head.toInt)
        new DataInputStream(input).readFully(result)
        result
      case None => readAll(input)
    }
    Response(status, headers, new String(bytes, ISO_8859_1))
  }

  private def readLine(input: InputStream): String = {
    val line = new java.lang.StringBuilder()
    var next = input.read()
    while (next != '\r') {
      assertTrue("Unexpected EOF or oversized HTTP line", next >= 0 && line.length() < 65536)
      line.append(next.toChar)
      next = input.read()
    }
    assertEquals('\n'.toInt, input.read())
    line.toString()
  }

  private def assertConnectionClosed(socket: Socket): Unit = {
    try assertEquals(-1, socket.getInputStream().read())
    catch { case _: SocketException => () }
  }

  private def readAll(input: InputStream): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val buffer = new Array[Byte](128)
    var count = input.read(buffer)
    while (count != -1) {
      assertTrue(count > 0)
      bytes.write(buffer, 0, count)
      count = input.read(buffer)
    }
    bytes.toByteArray()
  }

  private def readChunked(input: InputStream): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val data = new DataInputStream(input)
    var length = Integer.parseInt(readLine(input).takeWhile(_ != ';'), 16)
    while (length > 0) {
      val chunk = new Array[Byte](length)
      data.readFully(chunk)
      bytes.write(chunk)
      assertEquals("", readLine(input))
      length = Integer.parseInt(readLine(input).takeWhile(_ != ';'), 16)
    }
    while (readLine(input).nonEmpty) ()
    bytes.toByteArray()
  }

  private def textHandler(text: String): HttpHandler = new HttpHandler {
    override def handle(exchange: HttpExchange): Unit = respond(exchange, text)
  }

  private def respond(exchange: HttpExchange, text: String): Unit = {
    val bytes = text.getBytes(ISO_8859_1)
    exchange.sendResponseHeaders(200, bytes.length)
    exchange.getResponseBody().write(bytes)
    exchange.getResponseBody().close()
  }
}
