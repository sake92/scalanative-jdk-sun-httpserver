package com.sun.net.httpserver.internal

import java.io.{BufferedOutputStream, ByteArrayOutputStream, IOException}
import java.nio.charset.StandardCharsets.ISO_8859_1

import org.junit.Assert._
import org.junit.Test

class FixedLengthOutputStreamTest {
  @Test def noBodyCloseFlushesHeadersWithoutEmittingFraming(): Unit = {
    val sink = new RecordingOutputStream()
    val connection = new BufferedOutputStream(sink, 128)
    try {
      val headers = "HTTP/1.1 204 No Content\r\n\r\n".getBytes(ISO_8859_1)
      connection.write(headers)
      val body = new FixedLengthOutputStream(connection, 0)
      body.write(new Array[Byte](0))
      assertEquals(0, sink.size())
      assertFalse(body.isComplete)
      body.close()
      body.close()
      assertTrue(body.isComplete)
      assertArrayEquals(headers, sink.toByteArray())
      assertEquals(1, sink.flushes)
      assertEquals(0, sink.closes)
      assertThrows(classOf[IOException], () => body.write('x'))
      assertThrows(classOf[IOException], () => body.flush())

      val nextHeaders = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n".getBytes(ISO_8859_1)
      connection.write(nextHeaders)
      val next = new FixedLengthOutputStream(connection, 2)
      next.write("ok".getBytes(ISO_8859_1))
      next.close()
      val expected = headers ++ nextHeaders ++ "ok".getBytes(ISO_8859_1)
      assertArrayEquals(expected, sink.toByteArray())
      assertTrue(next.isComplete)
      assertEquals(2, sink.flushes)
      assertEquals(0, sink.closes)
    } finally connection.close()
  }

  @Test def noBodyWritesFailWithoutEmittingPayload(): Unit = {
    for (mode <- List("single", "bulk", "slice")) {
      val sink = new RecordingOutputStream()
      val headers = "HTTP/1.1 204 No Content\r\n\r\n".getBytes(ISO_8859_1)
      sink.write(headers)
      val body = new FixedLengthOutputStream(sink, 0)
      val error = assertThrows(classOf[IOException], () => {
        if (mode == "single") body.write('x')
        else if (mode == "bulk") body.write("xy".getBytes(ISO_8859_1))
        else body.write("_xy_".getBytes(ISO_8859_1), 1, 2)
      })
      assertArrayEquals(headers, sink.toByteArray())
      assertFalse(body.isComplete)
      assertSame(error, assertThrows(classOf[IOException], () => body.write('y')))
      assertSame(error, assertThrows(classOf[IOException], () => body.flush()))
      assertSame(error, assertThrows(classOf[IOException], () => body.close()))
      body.close()
      assertFalse(body.isComplete)
      assertEquals(0, sink.flushes)
      assertEquals(0, sink.closes)
      assertArrayEquals(headers, sink.toByteArray())
    }
  }

  @Test def writesExactlyTheDeclaredBody(): Unit = {
    val sink = new ByteArrayOutputStream()
    val body = new FixedLengthOutputStream(sink, 5)
    body.write(0x1ff)
    body.write(-2)
    val bytes = "_abc_".getBytes(ISO_8859_1)
    body.write(bytes, 1, 2)
    body.write(Array[Byte](99))
    assertArrayEquals(Array[Byte](-1, -2, 97, 98, 99), sink.toByteArray())
    assertArrayEquals("_abc_".getBytes(ISO_8859_1), bytes)
    assertFalse(body.isComplete)
    body.close()
    assertTrue(body.isComplete)
    assertArrayEquals(Array[Byte](-1, -2, 97, 98, 99), sink.toByteArray())
  }

  @Test def excessWritesAreTerminalAndDoNotEmitAPrefix(): Unit = {
    for (mode <- List("single", "bulk"); remaining <- List(0, 1)) {
      val sink = new RecordingOutputStream()
      val body = new FixedLengthOutputStream(sink, remaining + 1)
      body.write('a')
      if (mode == "single" && remaining == 1) body.write('b')
      val before = sink.toByteArray()
      val error = assertThrows(classOf[IOException], () => {
        if (mode == "single") body.write('x')
        else body.write("xy".getBytes(ISO_8859_1))
      })
      assertArrayEquals(before, sink.toByteArray())
      assertFalse(body.isComplete)
      assertSame(error, assertThrows(classOf[IOException], () => body.write('z')))
      assertSame(error, assertThrows(classOf[IOException], () => body.flush()))
      assertSame(error, assertThrows(classOf[IOException], () => body.close()))
      body.close()
      assertFalse(body.isComplete)
      assertEquals(0, sink.flushes)
      assertEquals(0, sink.closes)
      assertArrayEquals(before, sink.toByteArray())
    }
  }

  @Test def shortBodiesFailOnClose(): Unit = {
    for (written <- List(0, 2)) {
      val sink = new RecordingOutputStream()
      val body = new FixedLengthOutputStream(sink, 3)
      body.write("ab".getBytes(ISO_8859_1), 0, written)
      val error = assertThrows(classOf[IOException], () => body.close())
      assertEquals(written, sink.size())
      assertFalse(body.isComplete)
      assertSame(error, assertThrows(classOf[IOException], () => body.write('x')))
      assertSame(error, assertThrows(classOf[IOException], () => body.flush()))
      body.close()
      assertEquals(0, sink.flushes)
      assertEquals(0, sink.closes)
    }
  }

  @Test def flushDoesNotCompleteTheBody(): Unit = {
    val sink = new ByteArrayOutputStream()
    val buffered = new BufferedOutputStream(sink, 16)
    try {
      val body = new FixedLengthOutputStream(buffered, 3)
      body.write('a')
      assertEquals(0, sink.size())
      body.flush()
      assertArrayEquals(Array[Byte](97), sink.toByteArray())
      assertFalse(body.isComplete)
      body.write("bc".getBytes(ISO_8859_1))
      assertEquals(1, sink.size())
      body.flush()
      assertEquals(3, sink.size())
      assertFalse(body.isComplete)
      body.close()
      assertTrue(body.isComplete)
      assertArrayEquals("abc".getBytes(ISO_8859_1), sink.toByteArray())
    } finally buffered.close()
  }

  @Test def closePreservesConnectionOwnership(): Unit = {
    val sink = new RecordingOutputStream()
    val body = new FixedLengthOutputStream(sink, 2)
    body.write("ab".getBytes(ISO_8859_1))
    body.close()
    body.close()
    assertTrue(body.isComplete)
    assertEquals(1, sink.flushes)
    assertEquals(0, sink.closes)
    assertThrows(classOf[IOException], () => body.write('x'))
    assertThrows(classOf[IOException], () => body.write(new Array[Byte](0)))
    assertThrows(classOf[IOException], () => body.flush())
    val next = new FixedLengthOutputStream(sink, 2)
    next.write("cd".getBytes(ISO_8859_1))
    next.close()
    assertArrayEquals("abcd".getBytes(ISO_8859_1), sink.toByteArray())
    assertEquals(2, sink.flushes)
    assertEquals(0, sink.closes)

    val bytes = new ByteArrayOutputStream()
    val buffered = new BufferedOutputStream(bytes, 16)
    try {
      val bufferedBody = new FixedLengthOutputStream(buffered, 2)
      bufferedBody.write("xy".getBytes(ISO_8859_1))
      assertEquals(0, bytes.size())
      bufferedBody.close()
      assertArrayEquals("xy".getBytes(ISO_8859_1), bytes.toByteArray())
      assertTrue(bufferedBody.isComplete)
    } finally buffered.close()
  }

  @Test def emptyWritesDoNotTouchTheSink(): Unit = {
    val sink = new ByteArrayOutputStream() {
      override def write(bytes: Array[Byte], offset: Int, length: Int): Unit =
        throw new AssertionError("Empty write must not reach sink")
    }
    val body = new FixedLengthOutputStream(sink, 0)
    body.write(new Array[Byte](0))
    body.write(new Array[Byte](4), 4, 0)
    assertFalse(body.isComplete)
    body.close()
    assertTrue(body.isComplete)
    assertEquals(0, sink.size())
    val nonempty = new FixedLengthOutputStream(sink, 1)
    nonempty.write(new Array[Byte](0))
    nonempty.write('x')
    nonempty.close()
    assertTrue(nonempty.isComplete)
    assertEquals(1, sink.size())
  }

  @Test def rejectsInvalidArgumentsWithoutEmittingBytes(): Unit = {
    assertThrows(classOf[NullPointerException], () => new FixedLengthOutputStream(null, 1))
    val sink = new RecordingOutputStream()
    assertThrows(classOf[IllegalArgumentException], () => new FixedLengthOutputStream(sink, -1))
    val body = new FixedLengthOutputStream(sink, 3)
    assertThrows(classOf[NullPointerException], () => body.write(null))
    assertThrows(classOf[NullPointerException], () => body.write(null, 0, 0))
    val bytes = "abc".getBytes(ISO_8859_1)
    List((-1, 1), (0, -1), (4, 0), (2, 2), (1, Int.MaxValue), (Int.MaxValue, 1)).foreach {
      case (offset, length) =>
        assertThrows(classOf[IndexOutOfBoundsException], () => body.write(bytes, offset, length))
    }
    assertEquals(0, sink.size())
    assertEquals(0, sink.flushes)
    body.write(bytes)
    body.close()
    assertTrue(body.isComplete)
    assertArrayEquals(bytes, sink.toByteArray())
  }

  @Test def supportsLongBodyLengths(): Unit = {
    val sink = new ByteArrayOutputStream()
    val body = new FixedLengthOutputStream(sink, Long.MaxValue)
    body.write('a')
    body.write("bcd".getBytes(ISO_8859_1))
    body.flush()
    assertArrayEquals("abcd".getBytes(ISO_8859_1), sink.toByteArray())
    assertFalse(body.isComplete)
    assertThrows(classOf[IOException], () => body.close())
    assertFalse(body.isComplete)
  }

  @Test def writeFailuresAreTerminal(): Unit = {
    for (mode <- List("single", "bulk"); partial <- List(false, true)) {
      val error = new IOException("sink write failed")
      var broken = false
      var calls = 0
      val sink = new ByteArrayOutputStream() {
        override def write(value: Int): Unit = {
          calls += 1
          if (broken) {
            if (partial) super.write(value)
            throw error
          }
          super.write(value)
        }
        override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
          calls += 1
          if (broken) {
            if (partial) super.write(bytes, offset, 1)
            throw error
          }
          super.write(bytes, offset, length)
        }
        override def flush(): Unit = calls += 1
        override def close(): Unit = throw new AssertionError("Sink is caller-owned")
      }
      val body = new FixedLengthOutputStream(sink, 4)
      body.write('a')
      broken = true
      assertSame(error, assertThrows(classOf[IOException], () => {
        if (mode == "single") body.write('b')
        else body.write("bc".getBytes(ISO_8859_1))
      }))
      val expected = if (partial) "ab" else "a"
      assertArrayEquals(expected.getBytes(ISO_8859_1), sink.toByteArray())
      val callsAtFailure = calls
      broken = false
      assertSame(error, assertThrows(classOf[IOException], () => body.write('z')))
      assertSame(error, assertThrows(classOf[IOException], () => body.write(Array[Byte](1))))
      assertSame(error, assertThrows(classOf[IOException], () => body.flush()))
      assertSame(error, assertThrows(classOf[IOException], () => body.close()))
      body.close()
      assertFalse(body.isComplete)
      assertEquals(callsAtFailure, calls)
      assertArrayEquals(expected.getBytes(ISO_8859_1), sink.toByteArray())
    }
  }

  @Test def flushFailuresAreTerminal(): Unit = {
    for (mode <- List("flush", "close"); length <- List(0, 1)) {
      val error = new IOException("sink flush failed")
      var broken = true
      var flushes = 0
      val sink = new ByteArrayOutputStream() {
        override def flush(): Unit = {
          flushes += 1
          if (broken) throw error
        }
        override def close(): Unit = throw new AssertionError("Sink is caller-owned")
      }
      val body = new FixedLengthOutputStream(sink, length)
      if (length == 1) body.write('a')
      assertSame(error, assertThrows(classOf[IOException], () => {
        if (mode == "flush") body.flush() else body.close()
      }))
      broken = false
      assertFalse(body.isComplete)
      assertSame(error, assertThrows(classOf[IOException], () => body.flush()))
      assertSame(error, assertThrows(classOf[IOException], () => body.write('x')))
      if (mode == "flush") assertSame(error, assertThrows(classOf[IOException], () => body.close()))
      body.close()
      assertEquals(1, flushes)
      val expected = if (length == 1) Array[Byte](97) else new Array[Byte](0)
      assertArrayEquals(expected, sink.toByteArray())
      assertFalse(body.isComplete)
    }
  }

  private class RecordingOutputStream extends ByteArrayOutputStream {
    var flushes = 0
    var closes = 0
    override def flush(): Unit = flushes += 1
    override def close(): Unit = closes += 1
  }
}
