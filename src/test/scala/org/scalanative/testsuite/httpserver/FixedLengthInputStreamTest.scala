package com.sun.net.httpserver.internal

import java.io.{BufferedInputStream, ByteArrayInputStream, EOFException, IOException, InputStream}
import java.nio.charset.StandardCharsets.ISO_8859_1

import org.junit.Assert._
import org.junit.Test

class FixedLengthInputStreamTest {
  @Test def readsExactlyTheDeclaredBody(): Unit = {
    val source = stream("\u00ffabcGET /next HTTP/1.0\r\n\r\n")
    val body = new FixedLengthInputStream(source, 4)
    assertFalse(body.isComplete)
    assertEquals(255, body.read())
    val buffer = Array.fill[Byte](8)(42)
    assertEquals(3, body.read(buffer, 2, 6))
    assertArrayEquals(Array[Byte](42, 42, 97, 98, 99, 42, 42, 42), buffer)
    assertTrue(body.isComplete)
    assertEquals(-1, body.read())
    assertEquals(-1, body.read(buffer))
    assertEquals(0, body.read(buffer, 8, 0))
    assertEquals("/next", RequestHeadParser.read(source).get.target)
  }

  @Test def handlesFragmentedInput(): Unit = {
    val payload = "abcdefghijklmnopqrstuvwxyz"
    val source = new ByteArrayInputStream((payload + "GET / HTTP/1.0\r\n\r\n").getBytes(ISO_8859_1)) {
      override def read(b: Array[Byte], off: Int, len: Int): Int =
        super.read(b, off, math.min(len, 2))
    }
    val input = new BufferedInputStream(source, 5)
    try {
      val body = new FixedLengthInputStream(input, payload.length)
      val buffer = new Array[Byte](7)
      val actual = new java.lang.StringBuilder()
      var count = body.read(buffer)
      while (count != -1) {
        assertTrue(count > 0)
        actual.append(new String(buffer, 0, count, ISO_8859_1))
        count = body.read(buffer)
      }
      assertEquals(payload, actual.toString())
      assertTrue(body.isComplete)
      assertEquals("/", RequestHeadParser.read(input).get.target)
    } finally input.close()
  }

  @Test def emptyBodiesAndZeroLengthReadsDoNotConsumeSource(): Unit = {
    val source = new InputStream {
      override def read(): Int = throw new AssertionError("Source must not be read")
      override def available(): Int = throw new AssertionError("Source must not be probed")
    }
    val empty = new FixedLengthInputStream(source, 0)
    assertTrue(empty.isComplete)
    assertEquals(-1, empty.read())
    assertEquals(-1, empty.read(new Array[Byte](1)))
    assertEquals(0, empty.read(new Array[Byte](0)))
    assertEquals(0, empty.available())
    assertEquals(0L, empty.skip(Long.MaxValue))
    val nonempty = new FixedLengthInputStream(source, 1)
    assertEquals(0, nonempty.read(new Array[Byte](0)))
    assertEquals(0L, nonempty.skip(0))
    assertEquals(0L, nonempty.skip(-1))
    assertFalse(nonempty.isComplete)
  }

  @Test def rejectsInvalidArgumentsWithoutConsumingInput(): Unit = {
    assertThrows(classOf[NullPointerException], () => new FixedLengthInputStream(null, 1))
    val source = stream("abcNEXT")
    assertThrows(classOf[IllegalArgumentException], () => new FixedLengthInputStream(source, -1))
    val body = new FixedLengthInputStream(source, 3)
    val buffer = new Array[Byte](4)
    assertThrows(classOf[NullPointerException], () => body.read(null))
    assertThrows(classOf[NullPointerException], () => body.read(null, 0, 0))
    List((-1, 1), (0, -1), (5, 0), (3, 2), (1, Int.MaxValue), (Int.MaxValue, 1)).foreach {
      case (offset, length) =>
        assertThrows(classOf[IndexOutOfBoundsException], () => body.read(buffer, offset, length))
    }
    assertEquals(3L, body.skip(3))
    assertTrue(body.isComplete)
    assertEquals('N'.toInt, source.read())
    assertThrows(classOf[IndexOutOfBoundsException], () => body.read(buffer, 0, -1))
  }

  @Test def supportsLongBodyLengths(): Unit = {
    val body = new FixedLengthInputStream(stream("abc"), Long.MaxValue)
    assertEquals('a'.toInt, body.read())
    val buffer = new Array[Byte](8)
    assertEquals(2, body.read(buffer))
    assertEquals('b'.toByte, buffer(0))
    assertEquals('c'.toByte, buffer(1))
    assertFalse(body.isComplete)
    assertThrows(classOf[EOFException], () => body.read())
  }

  @Test def skipIsBoundedAndHandlesShortReads(): Unit = {
    val source = new ByteArrayInputStream(("a" * 5000 + "zNEXT").getBytes(ISO_8859_1)) {
      override def read(b: Array[Byte], off: Int, len: Int): Int =
        super.read(b, off, math.min(len, 3))
      override def skip(count: Long): Long = throw new AssertionError("Must verify skipped body bytes")
    }
    val body = new FixedLengthInputStream(source, 5001)
    assertEquals(0L, body.skip(Long.MinValue))
    assertEquals(5000L, body.skip(5000))
    assertFalse(body.isComplete)
    assertEquals('z'.toInt, body.read())
    assertEquals(0L, body.skip(1))
    assertTrue(body.isComplete)
    assertEquals('N'.toInt, source.read())
    val shortSource = stream("abcNEXT")
    val shortBody = new FixedLengthInputStream(shortSource, 3)
    assertEquals(3L, shortBody.skip(Long.MaxValue))
    assertEquals('N'.toInt, shortSource.read())
  }

  @Test def availabilityIsBoundedByRemainingBody(): Unit = {
    val body = new FixedLengthInputStream(stream("abcNEXT"), 3)
    assertEquals(3, body.available())
    assertEquals('a'.toInt, body.read())
    assertEquals(2, body.available())
    assertEquals(2L, body.skip(2))
    assertEquals(0, body.available())
    val source = new InputStream {
      override def read(): Int = 'x'.toInt
      override def available(): Int = 0
    }
    val unknown = new FixedLengthInputStream(source, 1)
    assertEquals(0, unknown.available())
    assertFalse(unknown.isComplete)
    assertEquals('x'.toInt, unknown.read())
    assertTrue(unknown.isComplete)
  }

  @Test def truncationIsTerminal(): Unit = {
    List("single", "bulk", "skip").foreach { operation =>
      val body = new FixedLengthInputStream(stream("ab"), 3)
      assertEquals('a'.toInt, body.read())
      assertEquals('b'.toInt, body.read())
      val error = assertThrows(classOf[EOFException], () => {
        operation match {
          case "single" => body.read()
          case "bulk" => body.read(new Array[Byte](4))
          case "skip" => body.skip(1)
        }
        ()
      })
      assertFalse(body.isComplete)
      assertSame(error, assertThrows(classOf[IOException], () => body.read()))
      assertSame(error, assertThrows(classOf[IOException], () => body.available()))
      body.close()
      assertFalse(body.isComplete)
    }
  }

  @Test def sourceFailuresAreTerminal(): Unit = {
    List("single", "bulk", "skip", "available").foreach { operation =>
      val error = new IOException("source failure")
      var broken = false
      var calls = 0
      val source = new ByteArrayInputStream("abcNEXT".getBytes(ISO_8859_1)) {
        override def read(): Int = {
          calls += 1
          if (broken) throw error
          super.read()
        }
        override def read(b: Array[Byte], off: Int, len: Int): Int = {
          calls += 1
          if (broken) throw error
          super.read(b, off, len)
        }
        override def available(): Int = {
          calls += 1
          if (broken) throw error
          super.available()
        }
      }
      val body = new FixedLengthInputStream(source, 3)
      assertEquals('a'.toInt, body.read())
      broken = true
      assertSame(error, assertThrows(classOf[IOException], () => {
        operation match {
          case "single" => body.read()
          case "bulk" => body.read(new Array[Byte](4))
          case "skip" => body.skip(1)
          case "available" => body.available()
        }
        ()
      }))
      val callsAtFailure = calls
      broken = false
      assertSame(error, assertThrows(classOf[IOException], () => body.read()))
      assertSame(error, assertThrows(classOf[IOException], () => body.read(new Array[Byte](4))))
      assertSame(error, assertThrows(classOf[IOException], () => body.skip(1)))
      assertSame(error, assertThrows(classOf[IOException], () => body.available()))
      assertEquals(callsAtFailure, calls)
      assertFalse(body.isComplete)
      assertEquals('b'.toInt, source.read())
    }
  }

  @Test def closePreservesConnectionOwnership(): Unit = {
    var sourceClosed = false
    val source = new ByteArrayInputStream("abcNEXT".getBytes(ISO_8859_1)) {
      override def close(): Unit = sourceClosed = true
    }
    val body = new FixedLengthInputStream(source, 3)
    assertEquals('a'.toInt, body.read())
    body.close()
    body.close()
    assertFalse(body.isComplete)
    assertFalse(sourceClosed)
    assertThrows(classOf[IOException], () => body.read())
    assertThrows(classOf[IOException], () => body.read(new Array[Byte](4)))
    assertThrows(classOf[IOException], () => body.read(new Array[Byte](0)))
    assertThrows(classOf[IOException], () => body.skip(0))
    assertThrows(classOf[IOException], () => body.available())
    assertEquals('b'.toInt, source.read()) // Closing did not drain unread bytes.
    val complete = new FixedLengthInputStream(source, 1)
    assertEquals('c'.toInt, complete.read())
    complete.close()
    assertTrue(complete.isComplete)
    assertFalse(sourceClosed)
    assertEquals('N'.toInt, source.read())
  }

  @Test def doesNotSupportMarkOrReset(): Unit = {
    val body = new FixedLengthInputStream(stream("abcNEXT"), 3)
    assertFalse(body.markSupported())
    assertEquals('a'.toInt, body.read())
    body.mark(10)
    assertEquals('b'.toInt, body.read())
    assertThrows(classOf[IOException], () => body.reset())
    assertEquals('c'.toInt, body.read())
    assertTrue(body.isComplete)
  }

  private def stream(value: String): ByteArrayInputStream =
    new ByteArrayInputStream(value.getBytes(ISO_8859_1))
}
