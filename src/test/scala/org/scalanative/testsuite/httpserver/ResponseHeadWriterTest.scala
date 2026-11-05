package com.sun.net.httpserver.internal

import java.io.{BufferedOutputStream, ByteArrayOutputStream, DataInputStream, IOException, OutputStream}
import java.net.{InetAddress, InetSocketAddress, ServerSocket, Socket}
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.ArrayList

import com.sun.net.httpserver.Headers
import org.junit.Assert._
import org.junit.Test

import ResponseHeadWriter._

class ResponseHeadWriterTest {
  private val date = "Thu, 05 Nov 2026 12:00:00 GMT"
  private val dateLine = "Date: " + date + "\r\n"

  @Test def serializesStatusAndRepeatedFields(): Unit = {
    var flushes = 0
    var closes = 0
    val sink = new ByteArrayOutputStream() {
      override def flush(): Unit = flushes += 1
      override def close(): Unit = closes += 1
    }
    val headers = datedHeaders()
    headers.add("Set-Cookie", "a=1")
    headers.add("set-cookie", "b=2")
    headers.set("X-Test", "caf\u00e9\tok")
    headers.set("Empty", "")
    assertEquals(FixedLength(3), write(sink, "GET", "HTTP/1.1", 201, 3, headers))
    val expected = "HTTP/1.1 201 Created\r\n" + dateLine +
      "Set-cookie: a=1\r\nSet-cookie: b=2\r\nX-test: caf\u00e9\tok\r\nEmpty: \r\nContent-length: 3\r\n\r\n"
    assertArrayEquals(expected.getBytes(ISO_8859_1), sink.toByteArray())
    assertEquals("3", headers.getFirst("Content-Length"))
    assertEquals(0, flushes)
    assertEquals(0, closes)
    val other = new ByteArrayOutputStream()
    write(other, "GET", "HTTP/1.1", 299, -1, datedHeaders())
    assertEquals("HTTP/1.1 299 \r\n" + dateLine + "Content-length: 0\r\n\r\n", text(other))
  }

  @Test def selectsAndNormalizesFraming(): Unit = {
    val cases = List(
      (7L, FixedLength(7): Framing, "Content-length: 7\r\n"),
      (Long.MaxValue, FixedLength(Long.MaxValue): Framing, "Content-length: 9223372036854775807\r\n"),
      (0L, Chunked: Framing, "Transfer-encoding: chunked\r\n"),
      (-1L, NoBody: Framing, "Content-length: 0\r\n"),
      (Long.MinValue, NoBody: Framing, "Content-length: 0\r\n")
    )
    cases.foreach { case (length, framing, expectedFields) =>
      val headers = datedHeaders()
      headers.add("Content-Length", "999")
      headers.add("Content-Length", "888")
      headers.set("Transfer-Encoding", "gzip")
      val sink = new ByteArrayOutputStream()
      assertEquals(framing, write(sink, "POST", "HTTP/1.1", 200, length, headers))
      assertEquals("HTTP/1.1 200 OK\r\n" + dateLine + expectedFields + "\r\n", text(sink))
      if (framing == Chunked) assertFalse(headers.containsKey("Content-Length"))
      else assertFalse(headers.containsKey("Transfer-Encoding"))
    }
  }

  @Test def headAndNotModifiedSuppressBody(): Unit = {
    for ((method, status) <- List(("HEAD", 200), ("GET", 304)); length <- List(-1L, 0L, 123L)) {
      val headers = datedHeaders()
      headers.set("Content-Length", " \t00042 ")
      headers.set("Transfer-Encoding", "chunked")
      val sink = new ByteArrayOutputStream()
      assertEquals(NoBody, write(sink, method, "HTTP/1.1", status, length, headers))
      val expectedLength = if (length > 0) "123" else "42"
      val statusLine = if (status == 200) "HTTP/1.1 200 OK\r\n" else "HTTP/1.1 304 Not Modified\r\n"
      assertEquals(statusLine + dateLine + "Content-length: " + expectedLength + "\r\n\r\n", text(sink))
      assertEquals(expectedLength, headers.getFirst("Content-Length"))
      assertFalse(headers.containsKey("Transfer-Encoding"))
      val absent = datedHeaders()
      assertEquals(NoBody, write(new ByteArrayOutputStream(), method, "HTTP/1.1", status, -1, absent))
      assertFalse(absent.containsKey("Content-Length"))
    }
  }

  @Test def bodylessStatusesOverrideRequestedLength(): Unit = {
    for (status <- List(100, 102, 103, 199, 204, 205); length <- List(-1L, 0L, 20L); method <- List("GET", "HEAD")) {
      val headers = datedHeaders()
      headers.set("Content-Length", "999")
      headers.set("Transfer-Encoding", "chunked")
      val sink = new ByteArrayOutputStream()
      assertEquals(NoBody, write(sink, method, "HTTP/1.1", status, length, headers))
      assertFalse(headers.containsKey("Transfer-Encoding"))
      if (status == 205) {
        assertEquals("0", headers.getFirst("Content-Length"))
        assertTrue(text(sink).endsWith("Content-length: 0\r\n\r\n"))
      } else {
        assertFalse(headers.containsKey("Content-Length"))
        assertTrue(text(sink).endsWith(dateLine + "\r\n"))
      }
    }
  }

  @Test def http10NeverUsesChunking(): Unit = {
    for (length <- List(-1L, 0L, 5L)) {
      val headers = datedHeaders()
      headers.set("Transfer-Encoding", "chunked")
      headers.set("Content-Length", "999")
      headers.set("Connection", "keep-alive")
      val sink = new ByteArrayOutputStream()
      val framing = write(sink, "GET", "HTTP/1.0", 200, length, headers)
      val expected = if (length == 0) CloseDelimited else if (length < 0) NoBody else FixedLength(5)
      assertEquals(expected, framing)
      assertTrue(text(sink).startsWith("HTTP/1.0 200 OK\r\n"))
      assertFalse(headers.containsKey("Transfer-Encoding"))
      if (length == 0) {
        assertFalse(headers.containsKey("Content-Length"))
        assertEquals("close", headers.getFirst("Connection"))
        assertEquals("HTTP/1.0 200 OK\r\n" + dateLine + "Connection: close\r\n\r\n", text(sink))
      } else {
        val expectedLength = if (length < 0) "0" else "5"
        assertEquals(expectedLength, headers.getFirst("Content-Length"))
        assertEquals("keep-alive", headers.getFirst("Connection"))
      }
    }
  }

  @Test def addsDateWhenAbsent(): Unit = {
    val headers = new Headers()
    val sink = new ByteArrayOutputStream()
    write(sink, "GET", "HTTP/1.1", 200, -1, headers)
    val actual = headers.getFirst("Date")
    assertTrue(actual.matches("[A-Z][a-z]{2}, [0-9]{2} [A-Z][a-z]{2} [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT"))
    assertEquals("HTTP/1.1 200 OK\r\nContent-length: 0\r\nDate: " + actual + "\r\n\r\n", text(sink))
    val supplied = datedHeaders()
    write(new ByteArrayOutputStream(), "GET", "HTTP/1.1", 200, -1, supplied)
    assertEquals(date, supplied.getFirst("Date"))
  }

  @Test def formatsUtcDatesAcrossCalendarBoundaries(): Unit = {
    val cases = List(
      (-62135596800000L, "Mon, 01 Jan 0001 00:00:00 GMT"),
      (-1L, "Wed, 31 Dec 1969 23:59:59 GMT"),
      (0L, "Thu, 01 Jan 1970 00:00:00 GMT"),
      (951827696000L, "Tue, 29 Feb 2000 12:34:56 GMT"),
      (-2203891200000L, "Thu, 01 Mar 1900 00:00:00 GMT"),
      (4107542400000L, "Mon, 01 Mar 2100 00:00:00 GMT"),
      (253402300799999L, "Fri, 31 Dec 9999 23:59:59 GMT")
    )
    cases.foreach { case (milliseconds, expected) => assertEquals(expected, httpDate(milliseconds)) }
    assertThrows(classOf[IllegalArgumentException], () => httpDate(Long.MinValue))
    assertThrows(classOf[IllegalArgumentException], () => httpDate(Long.MaxValue))
  }

  @Test def rejectsInvalidHeadsBeforeEmission(): Unit = {
    val fields = List(("Bad\r\nInjected", "x"), ("Bad name", "x"), ("", "x"),
      ("X", "x\r\nInjected: y"), ("X", "x\u0000"), ("X", "x\u007f"),
      ("X", "x\u0100"), ("N\u00e4me", "x"), ("X", null), (null, "x"))
    fields.foreach { case (name, value) =>
      val headers = datedHeaders()
      val raw = new java.util.LinkedHashMap[String, java.util.List[String]]()
      val values = new ArrayList[String]()
      values.add(value)
      raw.put(name, values)
      // JDK 8 bulk insertion bypasses direct-write checks; the wire writer
      // must still validate every field before emitting any bytes.
      headers.putAll(raw)
      assertInvalid(headers, "GET", "HTTP/1.1", 200)
    }
    val nullList = datedHeaders()
    val rawNull = new java.util.LinkedHashMap[String, java.util.List[String]]()
    rawNull.put("X", null)
    nullList.putAll(rawNull)
    assertInvalid(nullList, "GET", "HTTP/1.1", 200)
    for (status <- List(99, 600, -1, 101)) assertInvalid(datedHeaders(), "GET", "HTTP/1.1", status)
    for (version <- List("HTTP/2.0", "HTTP/1.1\r\nX", "http/1.1"))
      assertInvalid(datedHeaders(), "GET", version, 200)
    for (method <- List("", "GET\r\nX", "G ET", null))
      assertInvalid(datedHeaders(), method, "HTTP/1.1", 200)
    assertInvalid(datedHeaders(), "CONNECT", "HTTP/1.1", 200)
    assertThrows(classOf[NullPointerException], () => write(null, "GET", "HTTP/1.1", 200, 1, datedHeaders()))
    val sink = new ByteArrayOutputStream()
    assertThrows(classOf[NullPointerException], () => write(sink, "GET", "HTTP/1.1", 200, 1, null))
    assertEquals(0, sink.size())
    assertEquals(NoBody, write(sink, "CONNECT", "HTTP/1.1", 403, -1, datedHeaders()))
  }

  @Test def rejectsInvalidRepresentationLengths(): Unit = {
    for ((method, status) <- List(("HEAD", 200), ("GET", 304)); value <- List("", "-1", "+1", "1,1", "1 2", "9223372036854775808")) {
      val headers = datedHeaders()
      headers.set("Content-Length", value)
      assertInvalid(headers, method, "HTTP/1.1", status)
    }
    val repeated = datedHeaders()
    repeated.add("Content-Length", "1")
    repeated.add("Content-Length", "1")
    assertInvalid(repeated, "HEAD", "HTTP/1.1", 200)
    val empty = datedHeaders()
    empty.put("Content-Length", new ArrayList[String]())
    assertInvalid(empty, "HEAD", "HTTP/1.1", 200)
  }

  @Test def propagatesSinkFailures(): Unit = {
    val expected = ("HTTP/1.1 200 OK\r\n" + dateLine + "Content-length: 0\r\n\r\n").getBytes(ISO_8859_1)
    for (boundary <- 0 until expected.length) {
      val bytes = new ByteArrayOutputStream()
      val error = new IOException("head write failed")
      val sink = new OutputStream {
        override def write(value: Int): Unit = {
          if (bytes.size() == boundary) throw error
          bytes.write(value)
        }
        override def flush(): Unit = throw new AssertionError("Caller owns flush")
        override def close(): Unit = throw new AssertionError("Caller owns connection")
      }
      val headers = datedHeaders()
      assertSame(error, assertThrows(classOf[IOException], () => write(sink, "GET", "HTTP/1.1", 200, -1, headers)))
      assertArrayEquals(expected.take(boundary), bytes.toByteArray())
      assertEquals("0", headers.getFirst("Content-Length"))
    }
  }

  @Test def writesResponsesOverLoopback(): Unit = {
    val listener = new ServerSocket()
    val client = new Socket()
    var accepted: Socket = null
    try {
      listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
      listener.setSoTimeout(3000)
      client.connect(listener.getLocalSocketAddress(), 3000)
      client.setSoTimeout(3000)
      accepted = listener.accept()
      val output = new BufferedOutputStream(accepted.getOutputStream())
      assertEquals(FixedLength(5), write(output, "GET", "HTTP/1.1", 200, 5, datedHeaders()))
      val body = new FixedLengthOutputStream(output, 5)
      body.write("hello".getBytes(ISO_8859_1))
      body.close()
      assertEquals(NoBody, write(output, "GET", "HTTP/1.1", 204, 0, datedHeaders()))
      new FixedLengthOutputStream(output, 0).close()
      assertFalse(accepted.isClosed())
      accepted.shutdownOutput()
      val expected = ("HTTP/1.1 200 OK\r\n" + dateLine + "Content-length: 5\r\n\r\nhello" +
        "HTTP/1.1 204 No Content\r\n" + dateLine + "\r\n").getBytes(ISO_8859_1)
      val actual = new Array[Byte](expected.length)
      val input = new DataInputStream(client.getInputStream())
      input.readFully(actual)
      assertArrayEquals(expected, actual)
      assertEquals(-1, input.read())
    } finally {
      if (accepted != null) accepted.close()
      client.close()
      listener.close()
    }
  }

  private def datedHeaders(): Headers = {
    val headers = new Headers()
    headers.set("Date", date)
    headers
  }

  private def text(sink: ByteArrayOutputStream): String = new String(sink.toByteArray(), ISO_8859_1)

  private def assertInvalid(headers: Headers, method: String, version: String, status: Int): Unit = {
    val before = new Headers()
    before.putAll(headers)
    val sink = new ByteArrayOutputStream()
    assertThrows(classOf[IllegalArgumentException], () => write(sink, method, version, status, -1, headers))
    assertEquals(0, sink.size())
    assertEquals(before.entrySet(), headers.entrySet())
  }
}
