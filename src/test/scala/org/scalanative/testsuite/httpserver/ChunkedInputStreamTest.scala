package com.sun.net.httpserver.internal

import java.io.{BufferedInputStream, ByteArrayInputStream, ByteArrayOutputStream, EOFException, IOException, InputStream}
import java.net.ProtocolException
import java.nio.charset.StandardCharsets.ISO_8859_1

import org.junit.Assert._
import org.junit.Test

import ChunkedInputStream.Limits

class ChunkedInputStreamTest {
  @Test def decodesChunksAndPreservesNextRequest(): Unit = {
    val source = stream("POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n" +
      "02\r\n\u00ffa\r\na\r\n0123456789\r\nB\r\nabcdefghijk\r\n000\r\n\r\n" + nextRequest)
    assertEquals(RequestHeadParser.Chunked, RequestHeadParser.read(source).get.framing)
    val body = new ChunkedInputStream(source)
    assertEquals(255, body.read())
    val buffer = Array.fill[Byte](5)(42)
    assertEquals(1, body.read(buffer, 2, 3))
    assertArrayEquals(Array[Byte](42, 42, 97, 42, 42), buffer)
    assertEquals("0123456789abcdefghijk", drain(body))
    assertTrue(body.isComplete)
    assertEquals(-1, body.read())
    assertEquals(-1, body.read(buffer))
    assertEquals(0, body.read(buffer, 5, 0))
    assertEquals("/next", RequestHeadParser.read(source).get.target)
  }

  @Test def completionRequiresTheTrailerTerminator(): Unit = {
    val source = stream("1\r\nx\r\n0\r\n\r\n" + nextRequest)
    val body = new ChunkedInputStream(source)
    assertFalse(body.isComplete)
    assertEquals(0, body.read(new Array[Byte](0)))
    assertEquals('1'.toInt, source.read()) // Constructor and empty read left source alone.
    val emptySource = stream("0\r\n\r\n" + nextRequest)
    val empty = new ChunkedInputStream(emptySource)
    assertFalse(empty.isComplete)
    assertEquals(-1, empty.read())
    assertTrue(empty.isComplete)
    assertEquals("/next", RequestHeadParser.read(emptySource).get.target)
    val exact = new ChunkedInputStream(stream("1\r\nx\r\n0\r\n\r\n"))
    assertEquals('x'.toInt, exact.read())
    assertFalse(exact.isComplete)
    assertEquals(-1, exact.read())
    assertTrue(exact.isComplete)
  }

  @Test def acceptsChunkExtensions(): Unit = {
    List(";name", ";name=token", ";a=\"\";b=foo", ";quoted=\"x;= y\\\"z\\\\\"",
      " \t; name \t= \tvalue\t; other=\"\t\u00e9\"", ";symbol=!#$%&'*+-.^_`|~").foreach { extension =>
      val body = new ChunkedInputStream(stream("1" + extension + "\r\nx\r\n0" + extension + "\r\n\r\n"))
      assertEquals(extension, "x", drain(body))
      assertTrue(body.isComplete)
    }
  }

  @Test def rejectsMalformedChunkFraming(): Unit = {
    val badSizes = List("", " 1", "+1", "-1", "0x1", "g", "1 0", "1 ",
      "8000000000000000", "FFFFFFFFFFFFFFFFF", "1;", "1;=x", "1;a=", "1;a= x y",
      "1;a=\"unterminated", "1;a=\"bad\u0000\"", "1;a=\"bad\u007f\"", "1;a=\"bad\\\u0001\"",
      "1;a=\"x\"junk", "1;a=\"x\" ", "1;a ", "1\u0000")
    val wires = badSizes.map(_ + "\r\nx\r\n0\r\n\r\n") ++ List(
      "1\nx\r\n0\r\n\r\n", "1\rXx\r\n0\r\n\r\n", "1\r\nxX\n0\r\n\r\n",
      "1\r\nx\rX0\r\n\r\n", "1\r\nx\n0\r\n\r\n", "0\r\n\n")
    wires.foreach { wire =>
      val body = new ChunkedInputStream(stream(wire))
      val error = assertThrows(classOf[ProtocolException], () => drain(body))
      assertFalse(body.isComplete)
      assertSame(error, assertThrows(classOf[IOException], () => body.read()))
    }
  }

  @Test def supportsLongChunkSizes(): Unit = {
    val body = new ChunkedInputStream(stream("7fffffffffffffff\r\nabc"))
    assertEquals('a'.toInt, body.read())
    val buffer = new Array[Byte](8)
    assertEquals(2, body.read(buffer))
    assertEquals('b'.toByte, buffer(0))
    assertEquals('c'.toByte, buffer(1))
    assertFalse(body.isComplete)
    assertThrows(classOf[EOFException], () => body.read())
  }

  @Test def validatesAndDiscardsTrailers(): Unit = {
    val source = stream("POST / HTTP/1.1\r\nHost: original\r\nTransfer-Encoding: chunked\r\n\r\n" +
      "1\r\nx\r\n0\r\nX-Checksum: one\r\nx-checksum: two\r\nX-Note:\t caf\u00e9 \t\r\n" +
      "Empty:\r\nHost: attacker\r\nContent-Length: 999\r\nAuthorization: fake\r\n\r\n" + nextRequest)
    val head = RequestHeadParser.read(source).get
    val body = new ChunkedInputStream(source)
    assertEquals("x", drain(body))
    assertTrue(body.isComplete)
    assertEquals(Vector("Host" -> "original", "Transfer-Encoding" -> "chunked"), head.headers)
    assertEquals("/next", RequestHeadParser.read(source).get.target)
    List(" Folded: x", "\tfolded", "Bad : value", ": value", "NoColon", "Bad(name): x",
      "X: bad\u0000", "X: bad\u007f", "X: bad\u000b").foreach { trailer =>
      assertThrows(classOf[ProtocolException], () => drain(new ChunkedInputStream(
        stream("0\r\n" + trailer + "\r\n\r\n"))))
    }
  }

  @Test def enforcesMetadataLimits(): Unit = {
    val wire = "1;a=b\r\nx\r\n0;c\r\nX: y\r\n\r\n"
    val limits = Limits(chunkLineBytes = 5, extensionBytes = 6,
      trailerLineBytes = 4, trailerSectionBytes = 8, trailerCount = 1)
    assertEquals("x", drain(new ChunkedInputStream(stream(wire), limits)))
    List(limits.copy(chunkLineBytes = 4), limits.copy(extensionBytes = 5),
      limits.copy(trailerLineBytes = 3), limits.copy(trailerSectionBytes = 7),
      limits.copy(trailerCount = 0)).foreach { tooSmall =>
      assertThrows(classOf[ProtocolException], () => drain(new ChunkedInputStream(stream(wire), tooSmall)))
    }
    assertEquals("", drain(new ChunkedInputStream(stream("0\r\n\r\n"),
      Limits(chunkLineBytes = 1, extensionBytes = 0, trailerLineBytes = 0,
        trailerSectionBytes = 2, trailerCount = 0))))
    assertThrows(classOf[ProtocolException], () => drain(new ChunkedInputStream(
      stream("0\r\nX: " + ("a" * 8190) + "\r\n\r\n"))))
    assertThrows(classOf[ProtocolException], () => drain(new ChunkedInputStream(
      stream("0\r\n" + ("X: y\r\n" * 101) + "\r\n"))))
    assertThrows(classOf[ProtocolException], () => drain(new ChunkedInputStream(
      stream("0\r\n" + (("X: " + ("a" * 8000) + "\r\n") * 9) + "\r\n"))))
    assertThrows(classOf[ProtocolException], () => drain(new ChunkedInputStream(
      stream("1;" + ("a" * 8191) + "\r\nx\r\n0\r\n\r\n"))))
    val extendedChunk = "1;" + ("a" * 8000) + "\r\nx\r\n"
    assertThrows(classOf[ProtocolException], () => drain(new ChunkedInputStream(
      stream((extendedChunk * 9) + "0\r\n\r\n"))))
  }

  @Test def handlesEveryFragmentBoundary(): Unit = {
    val wire = "3;a=\"x\\\"y\"\r\nabc\r\n2\r\nde\r\n0;end\r\nX: y\r\n\r\n"
    val bytes = (wire + nextRequest).getBytes(ISO_8859_1)
    (1 until wire.length).foreach { boundary =>
      val source = new ByteArrayInputStream(bytes) {
        override def read(b: Array[Byte], off: Int, len: Int): Int = {
          val maximum = if (pos < boundary) boundary - pos else 1
          super.read(b, off, math.min(len, maximum))
        }
      }
      val input = new BufferedInputStream(source, 8)
      try {
        val body = new ChunkedInputStream(input)
        assertEquals("abcde", drain(body))
        assertTrue(body.isComplete)
        assertEquals("/next", RequestHeadParser.read(input).get.target)
      } finally input.close()
    }
  }

  @Test def truncationIsTerminal(): Unit = {
    val wire = "2;ext=\"a\"\r\nab\r\n0\r\nX: y\r\n\r\n"
    (0 until wire.length).foreach { end =>
      val body = new ChunkedInputStream(stream(wire.substring(0, end)))
      val error = assertThrows(classOf[EOFException], () => drain(body))
      assertFalse(body.isComplete)
      assertSame(error, assertThrows(classOf[IOException], () => body.read()))
      assertSame(error, assertThrows(classOf[IOException], () => body.skip(1)))
      assertSame(error, assertThrows(classOf[IOException], () => body.available()))
    }
  }

  @Test def sourceFailuresAreTerminal(): Unit = {
    val wire = "2\r\nab\r\n0\r\nX: y\r\n\r\n"
    for (boundary <- 0 until wire.length; operation <- List("single", "bulk", "skip")) {
      val error = new IOException("source failure")
      var broken = true
      var calls = 0
      val source = new ByteArrayInputStream(wire.getBytes(ISO_8859_1)) {
        override def read(): Int = {
          calls += 1
          if (broken && pos == boundary) throw error
          super.read()
        }
        override def read(b: Array[Byte], off: Int, len: Int): Int = {
          calls += 1
          if (broken && pos == boundary) throw error
          val maximum = if (broken && pos < boundary) boundary - pos else len
          super.read(b, off, math.min(len, maximum))
        }
      }
      val body = new ChunkedInputStream(source)
      assertSame(error, assertThrows(classOf[IOException], () => {
        if (operation == "single") while (body.read() != -1) ()
        else if (operation == "bulk") drain(body)
        else body.skip(Long.MaxValue)
        ()
      }))
      val callsAtFailure = calls
      broken = false
      assertSame(error, assertThrows(classOf[IOException], () => body.read()))
      assertSame(error, assertThrows(classOf[IOException], () => body.read(new Array[Byte](4))))
      assertSame(error, assertThrows(classOf[IOException], () => body.available()))
      assertEquals(callsAtFailure, calls)
      assertFalse(body.isComplete)
    }
    val error = new IOException("availability failed")
    val source = new ByteArrayInputStream("2\r\nab\r\n0\r\n\r\n".getBytes(ISO_8859_1)) {
      override def available(): Int = throw error
    }
    val body = new ChunkedInputStream(source)
    assertEquals('a'.toInt, body.read())
    assertSame(error, assertThrows(classOf[IOException], () => body.available()))
    assertSame(error, assertThrows(classOf[IOException], () => body.read()))
  }

  @Test def skipCountsOnlyPayloadBytes(): Unit = {
    val source = new ByteArrayInputStream(("3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n" + nextRequest).getBytes(ISO_8859_1)) {
      override def read(b: Array[Byte], off: Int, len: Int): Int =
        super.read(b, off, math.min(len, 1))
      override def skip(count: Long): Long = throw new AssertionError("Must decode skipped bytes")
    }
    val body = new ChunkedInputStream(source)
    assertEquals(0L, body.skip(-1))
    assertEquals(0L, body.skip(0))
    assertEquals(4L, body.skip(4))
    assertEquals('e'.toInt, body.read())
    assertFalse(body.isComplete)
    assertEquals(0L, body.skip(Long.MaxValue))
    assertTrue(body.isComplete)
    assertEquals("/next", RequestHeadParser.read(source).get.target)
    val all = new ChunkedInputStream(stream("1\r\na\r\n1\r\nb\r\n0\r\n\r\n"))
    assertEquals(2L, all.skip(Long.MaxValue))
    assertTrue(all.isComplete)
  }

  @Test def availabilityNeverConsumesFraming(): Unit = {
    val source = stream("3\r\nabc\r\n0\r\n\r\n" + nextRequest)
    val body = new ChunkedInputStream(source)
    val initial = source.available()
    assertEquals(0, body.available())
    assertEquals(initial, source.available())
    assertEquals('a'.toInt, body.read())
    assertEquals(2, body.available())
    assertEquals(2L, body.skip(2))
    val afterData = source.available()
    assertEquals(0, body.available())
    assertEquals(afterData, source.available())
    assertFalse(body.isComplete)
    assertEquals(-1, body.read())
    assertEquals(0, body.available())
    assertEquals("/next", RequestHeadParser.read(source).get.target)
  }

  @Test def rejectsInvalidArgumentsWithoutConsumingInput(): Unit = {
    assertThrows(classOf[NullPointerException], () => new ChunkedInputStream(null))
    val source = stream("1\r\nx\r\n0\r\n\r\n")
    val body = new ChunkedInputStream(source)
    assertThrows(classOf[NullPointerException], () => body.read(null))
    assertThrows(classOf[NullPointerException], () => body.read(null, 0, 0))
    val buffer = new Array[Byte](4)
    List((-1, 1), (0, -1), (5, 0), (3, 2), (1, Int.MaxValue), (Int.MaxValue, 1)).foreach {
      case (offset, length) =>
        assertThrows(classOf[IndexOutOfBoundsException], () => body.read(buffer, offset, length))
    }
    assertEquals("x", drain(body))
    assertTrue(body.isComplete)
  }

  @Test def closeAndResetPreserveConnectionOwnership(): Unit = {
    var sourceClosed = false
    val source = new ByteArrayInputStream("2\r\nab\r\n0\r\n\r\n".getBytes(ISO_8859_1)) {
      override def close(): Unit = sourceClosed = true
    }
    val body = new ChunkedInputStream(source)
    assertFalse(body.markSupported())
    assertEquals('a'.toInt, body.read())
    body.mark(10)
    assertThrows(classOf[IOException], () => body.reset())
    body.close()
    body.close()
    assertFalse(sourceClosed)
    assertFalse(body.isComplete)
    assertThrows(classOf[IOException], () => body.read())
    assertThrows(classOf[IOException], () => body.read(new Array[Byte](0)))
    assertThrows(classOf[IOException], () => body.skip(0))
    assertThrows(classOf[IOException], () => body.available())
    assertEquals('b'.toInt, source.read())
    val completeSource = new ByteArrayInputStream("0\r\n\r\nNEXT".getBytes(ISO_8859_1)) {
      override def close(): Unit = sourceClosed = true
    }
    val complete = new ChunkedInputStream(completeSource)
    assertEquals(-1, complete.read())
    complete.close()
    assertTrue(complete.isComplete)
    assertFalse(sourceClosed)
    assertEquals('N'.toInt, completeSource.read())
  }

  private val nextRequest = "GET /next HTTP/1.0\r\n\r\n"

  private def stream(wire: String): ByteArrayInputStream =
    new ByteArrayInputStream(wire.getBytes(ISO_8859_1))

  private def drain(input: InputStream): String = {
    val output = new ByteArrayOutputStream()
    val buffer = new Array[Byte](7)
    var count = input.read(buffer)
    while (count != -1) {
      assertTrue("Read must make progress", count > 0)
      output.write(buffer, 0, count)
      count = input.read(buffer)
    }
    new String(output.toByteArray(), ISO_8859_1)
  }
}
