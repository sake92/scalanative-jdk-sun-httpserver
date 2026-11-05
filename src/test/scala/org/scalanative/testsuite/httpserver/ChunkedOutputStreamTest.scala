package com.sun.net.httpserver.internal

import java.io.{BufferedOutputStream, ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, IOException, OutputStream}
import java.nio.charset.StandardCharsets.ISO_8859_1

import org.junit.Assert._
import org.junit.Test

class ChunkedOutputStreamTest {
  @Test def encodesWritesWithHexSizes(): Unit = {
    val sink = new ByteArrayOutputStream()
    val body = new ChunkedOutputStream(sink)
    body.write(0x1ff)
    body.write(-2)
    val bytes = "_0123456789abcdef_".getBytes(ISO_8859_1)
    body.write(bytes, 1, 16)
    body.write("0123456789".getBytes(ISO_8859_1))
    val expected = "1\r\n\u00ff\r\n1\r\n\u00fe\r\n10\r\n0123456789abcdef\r\na\r\n0123456789\r\n"
    assertArrayEquals(expected.getBytes(ISO_8859_1), sink.toByteArray())
    assertArrayEquals("_0123456789abcdef_".getBytes(ISO_8859_1), bytes)
    assertFalse(body.isComplete)
    body.close()
    assertTrue(body.isComplete)
    assertArrayEquals((expected + "0\r\n\r\n").getBytes(ISO_8859_1), sink.toByteArray())
  }

  @Test def emptyWritesEmitNothingAndEmptyCloseTerminates(): Unit = {
    val sink = new FailingOutputStream()
    val body = new ChunkedOutputStream(sink)
    body.write(new Array[Byte](0))
    body.write(new Array[Byte](4), 4, 0)
    assertEquals(0, sink.writes)
    assertEquals(0, sink.flushes)
    assertFalse(body.isComplete)
    body.close()
    assertArrayEquals("0\r\n\r\n".getBytes(ISO_8859_1), sink.bytes.toByteArray())
    assertEquals(1, sink.flushes)
    assertTrue(body.isComplete)

    val nonemptySink = new ByteArrayOutputStream()
    val nonempty = new ChunkedOutputStream(nonemptySink)
    nonempty.write('a')
    nonempty.write(new Array[Byte](0))
    nonempty.write('b')
    nonempty.close()
    assertArrayEquals("1\r\na\r\n1\r\nb\r\n0\r\n\r\n".getBytes(ISO_8859_1), nonemptySink.toByteArray())
  }

  @Test def flushDoesNotTerminateTheBody(): Unit = {
    val sink = new ByteArrayOutputStream()
    val buffered = new BufferedOutputStream(sink, 64)
    try {
      val body = new ChunkedOutputStream(buffered)
      body.write('a')
      assertEquals(0, sink.size())
      body.flush()
      assertArrayEquals("1\r\na\r\n".getBytes(ISO_8859_1), sink.toByteArray())
      assertFalse(body.isComplete)
      body.write("bc".getBytes(ISO_8859_1))
      body.close()
      assertArrayEquals("1\r\na\r\n2\r\nbc\r\n0\r\n\r\n".getBytes(ISO_8859_1), sink.toByteArray())
      assertTrue(body.isComplete)
    } finally buffered.close()
  }

  @Test def closePreservesConnectionOwnership(): Unit = {
    val sink = new FailingOutputStream()
    val body = new ChunkedOutputStream(sink)
    body.write('a')
    body.close()
    body.close()
    assertTrue(body.isComplete)
    assertEquals(1, sink.flushes)
    assertEquals(0, sink.closes)
    assertThrows(classOf[IOException], () => body.write('x'))
    assertThrows(classOf[IOException], () => body.write(new Array[Byte](0)))
    assertThrows(classOf[IOException], () => body.flush())
    val nextHeaders = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
    sink.write(nextHeaders.getBytes(ISO_8859_1))
    val next = new ChunkedOutputStream(sink)
    next.write('b')
    next.close()
    val expected = "1\r\na\r\n0\r\n\r\n" + nextHeaders + "1\r\nb\r\n0\r\n\r\n"
    assertArrayEquals(expected.getBytes(ISO_8859_1), sink.bytes.toByteArray())
    assertEquals(2, sink.flushes)
    assertEquals(0, sink.closes)
  }

  @Test def rejectsInvalidArgumentsWithoutEmittingBytes(): Unit = {
    assertThrows(classOf[NullPointerException], () => new ChunkedOutputStream(null))
    val sink = new FailingOutputStream()
    val body = new ChunkedOutputStream(sink)
    assertThrows(classOf[NullPointerException], () => body.write(null))
    assertThrows(classOf[NullPointerException], () => body.write(null, 0, 0))
    val bytes = "abc".getBytes(ISO_8859_1)
    List((-1, 1), (0, -1), (4, 0), (2, 2), (1, Int.MaxValue), (Int.MaxValue, 1)).foreach {
      case (offset, length) =>
        assertThrows(classOf[IndexOutOfBoundsException], () => body.write(bytes, offset, length))
    }
    assertEquals(0, sink.writes)
    body.write(bytes)
    body.close()
    assertTrue(body.isComplete)
    assertArrayEquals("3\r\nabc\r\n0\r\n\r\n".getBytes(ISO_8859_1), sink.bytes.toByteArray())
  }

  @Test def writeFailuresAreTerminal(): Unit = {
    for (mode <- List("single", "bulk")) {
      val chunk = if (mode == "single") "1\r\nb\r\n" else "3\r\nbcd\r\n"
      for (boundary <- 0 until chunk.length) {
        val sink = new FailingOutputStream()
        val body = new ChunkedOutputStream(sink)
        body.write('a')
        val initial = "1\r\na\r\n"
        sink.failAt = initial.length + boundary
        assertSame(sink.error, assertThrows(classOf[IOException], () => {
          if (mode == "single") body.write('b') else body.write("bcd".getBytes(ISO_8859_1))
        }))
        val expected = (initial + chunk.substring(0, boundary)).getBytes(ISO_8859_1)
        assertArrayEquals(expected, sink.bytes.toByteArray())
        val writesAtFailure = sink.writes
        sink.failAt = -1
        assertSame(sink.error, assertThrows(classOf[IOException], () => body.write('x')))
        assertSame(sink.error, assertThrows(classOf[IOException], () => body.write(Array[Byte](1))))
        assertSame(sink.error, assertThrows(classOf[IOException], () => body.flush()))
        assertSame(sink.error, assertThrows(classOf[IOException], () => body.close()))
        body.close()
        assertFalse(body.isComplete)
        assertEquals(writesAtFailure, sink.writes)
        assertEquals(0, sink.flushes)
        assertEquals(0, sink.closes)
        assertArrayEquals(expected, sink.bytes.toByteArray())
      }
    }
  }

  @Test def terminationFailuresAreTerminal(): Unit = {
    val ending = "0\r\n\r\n"
    for (boundary <- 0 until ending.length) {
      val sink = new FailingOutputStream()
      val body = new ChunkedOutputStream(sink)
      body.write('a')
      val initial = "1\r\na\r\n"
      sink.failAt = initial.length + boundary
      assertSame(sink.error, assertThrows(classOf[IOException], () => body.close()))
      val expected = (initial + ending.substring(0, boundary)).getBytes(ISO_8859_1)
      assertArrayEquals(expected, sink.bytes.toByteArray())
      val writesAtFailure = sink.writes
      sink.failAt = -1
      body.close()
      assertSame(sink.error, assertThrows(classOf[IOException], () => body.write('x')))
      assertSame(sink.error, assertThrows(classOf[IOException], () => body.flush()))
      assertFalse(body.isComplete)
      assertEquals(writesAtFailure, sink.writes)
      assertEquals(0, sink.flushes)
      assertEquals(0, sink.closes)
      assertArrayEquals(expected, sink.bytes.toByteArray())
    }
  }

  @Test def flushFailuresAreTerminal(): Unit = {
    for (mode <- List("flush", "close")) {
      val sink = new FailingOutputStream()
      val body = new ChunkedOutputStream(sink)
      body.write('a')
      sink.failFlush = true
      assertSame(sink.error, assertThrows(classOf[IOException], () => {
        if (mode == "flush") body.flush() else body.close()
      }))
      val expected = if (mode == "flush") "1\r\na\r\n" else "1\r\na\r\n0\r\n\r\n"
      val writesAtFailure = sink.writes
      sink.failFlush = false
      assertSame(sink.error, assertThrows(classOf[IOException], () => body.write('x')))
      assertSame(sink.error, assertThrows(classOf[IOException], () => body.flush()))
      if (mode == "flush") assertSame(sink.error, assertThrows(classOf[IOException], () => body.close()))
      body.close()
      assertFalse(body.isComplete)
      assertEquals(writesAtFailure, sink.writes)
      assertEquals(1, sink.flushes)
      assertEquals(0, sink.closes)
      assertArrayEquals(expected.getBytes(ISO_8859_1), sink.bytes.toByteArray())
    }
  }

  @Test def roundTripsAcrossWriteBoundaries(): Unit = {
    val sink = new ByteArrayOutputStream()
    val expected = new ByteArrayOutputStream()
    val body = new ChunkedOutputStream(sink)
    for (size <- List(1, 2, 15, 16, 255, 256, 4097)) {
      val bytes = Array.tabulate[Byte](size)(index => ((index * 37) % 256).toByte)
      body.write(bytes)
      expected.write(bytes)
      body.write(255)
      expected.write(255)
    }
    body.close()
    val nextResponse = "HTTP/1.1 204 No Content\r\n\r\n".getBytes(ISO_8859_1)
    sink.write(nextResponse)
    val source = new ByteArrayInputStream(sink.toByteArray())
    val decoded = new ChunkedInputStream(source)
    val actual = new ByteArrayOutputStream()
    val buffer = new Array[Byte](11)
    var count = decoded.read(buffer)
    while (count != -1) {
      assertTrue(count > 0)
      actual.write(buffer, 0, count)
      count = decoded.read(buffer)
    }
    assertArrayEquals(expected.toByteArray(), actual.toByteArray())
    assertTrue(decoded.isComplete)
    assertEquals(nextResponse.length, source.available())
    val following = new Array[Byte](nextResponse.length)
    new DataInputStream(source).readFully(following)
    assertArrayEquals(nextResponse, following)
  }

  private class FailingOutputStream extends OutputStream {
    val bytes = new ByteArrayOutputStream()
    val error = new IOException("sink failed")
    var failAt = -1
    var failFlush = false
    var writes = 0
    var flushes = 0
    var closes = 0

    override def write(value: Int): Unit = {
      writes += 1
      if (bytes.size() == failAt) throw error
      bytes.write(value)
    }

    override def flush(): Unit = {
      flushes += 1
      if (failFlush) throw error
    }

    override def close(): Unit = closes += 1
  }
}
