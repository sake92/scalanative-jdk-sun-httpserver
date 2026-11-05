package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.Headers
import java.util.Arrays
import org.junit.Assert._
import org.junit.Test

class HeadersNullContractTest {
  @Test def directWritesRejectNullMappingsAndElements(): Unit = {
    val headers = new Headers()
    assertThrows(classOf[NullPointerException], () => headers.put(null, Arrays.asList("x")))
    assertThrows(classOf[NullPointerException], () => headers.put("X", null))
    assertThrows(classOf[NullPointerException], () => headers.put("X", Arrays.asList(null: String)))
    assertThrows(classOf[NullPointerException], () => headers.add("X", null))
    assertThrows(classOf[NullPointerException], () => headers.set("X", null))
    assertTrue(headers.isEmpty())
    assertFalse(headers.containsKey(42))
    assertThrows(classOf[ClassCastException], () => headers.get(42))
    assertThrows(classOf[ClassCastException], () => headers.remove(42))
    if (ReferenceRuntime.jdk8) {
      assertNull(headers.getFirst(null))
      assertFalse(headers.containsKey(null))
      assertNull(headers.remove(null))
    } else {
      assertThrows(classOf[NullPointerException], () => headers.getFirst(null))
      assertThrows(classOf[NullPointerException], () => headers.containsKey(null))
      assertThrows(classOf[NullPointerException], () => headers.remove(null))
    }
  }

  @Test def validatesLineBreaksBeforeMutatingHeaders(): Unit = {
    val headers = new Headers()
    headers.add("X", "original")
    for (value <- Seq("x\ny", "x\r y", "x\r\ny", "x\n y")) {
      assertThrows(classOf[IllegalArgumentException], () => headers.add("X", value))
      assertThrows(classOf[IllegalArgumentException], () => headers.set("X", value))
      assertEquals(1, headers.get("X").size())
      assertEquals("original", headers.getFirst("X"))
    }
    assertThrows(classOf[IllegalArgumentException], () => headers.put("X\rBad", Arrays.asList("x")))
    assertThrows(classOf[IllegalArgumentException], () => headers.get("X\nBad"))
    headers.add("X", "folded\r\n continuation")
    headers.add("X", "folded\r\n\tcontinuation")
    assertEquals(3, headers.get("X").size())
  }
}
