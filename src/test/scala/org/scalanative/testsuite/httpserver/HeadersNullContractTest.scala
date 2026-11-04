package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.Headers
import org.junit.Assert._
import org.junit.Test

class HeadersNullContractTest {
  @Test def nullKeysAndValuesAreAccepted(): Unit = {
    val headers = new Headers()
    val nullValues: java.util.List[String] = null
    headers.put(null, nullValues)

    assertTrue(headers.containsKey(null))
    assertFalse(headers.containsKey(42))
    assertNull(headers.get(42))
    assertNull(headers.remove(42))
    assertNull(headers.getFirst(null))
    headers.add(null, null)
    assertTrue(headers.get(null).contains(null))
    headers.set(null, "present")
    assertEquals("present", headers.getFirst(null))
  }
}
