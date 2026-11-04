package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.Headers
import java.util.{ArrayList, LinkedHashMap}

import org.junit.Assert._
import org.junit.Test

class HeadersTest {
  @Test def lookupIsCaseInsensitive(): Unit = {
    val headers = new Headers()
    headers.add("Content-Type", "text/plain")

    assertEquals("text/plain", headers.getFirst("content-type"))
    assertTrue(headers.containsKey("CONTENT-TYPE"))
    assertEquals(Seq("Content-type"), headers.keySet().toArray().toSeq)
  }

  @Test def addAppendsValues(): Unit = {
    val headers = new Headers()
    headers.add("Set-Cookie", "a=1")
    headers.add("set-cookie", "b=2")

    assertEquals(Seq("a=1", "b=2"), headers.get("SET-COOKIE").toArray().toSeq)
  }

  @Test def setReplacesValues(): Unit = {
    val headers = new Headers()
    headers.add("Cache-Control", "no-cache")
    headers.add("cache-control", "private")
    headers.set("CACHE-CONTROL", "public")

    assertEquals(Seq("public"), headers.get("cache-control").toArray().toSeq)
  }

  @Test def getFirstReturnsFirstValue(): Unit = {
    val headers = new Headers()
    assertNull(headers.getFirst("Missing"))
    headers.add("Warning", "one")
    headers.add("Warning", "two")

    assertEquals("one", headers.getFirst("warning"))
  }

  @Test def mapOperationsUseHeaderKeySemantics(): Unit = {
    val headers = new Headers()
    val previous = new ArrayList[String]()
    previous.add("first")
    assertNull(headers.put("X-Trace", previous))
    val replacement = new ArrayList[String]()
    replacement.add("replacement")
    assertEquals(previous, headers.put("x-trace", replacement))

    val additional = new LinkedHashMap[String, java.util.List[String]]()
    val values = new ArrayList[String]()
    values.add("second")
    additional.put("X-Other", values)
    headers.putAll(additional)

    assertTrue(headers.containsKey("X-TRACE"))
    assertEquals(values, headers.get("x-other"))
    assertEquals(replacement, headers.remove("X-TRACE"))
    assertFalse(headers.containsKey("x-trace"))
  }

  @Test def mapViewsRemainLive(): Unit = {
    val headers = new Headers()
    headers.add("X-Test", "one")
    headers.add("X-Other", "other")
    val keys = headers.keySet()
    val values = headers.values()
    val entries = headers.entrySet()

    assertTrue(keys.contains("X-test"))
    assertTrue(values.contains(headers.get("x-test")))
    assertEquals(2, entries.size())

    headers.add("X-Later", "later")
    assertEquals(3, keys.size())
    assertEquals(3, values.size())
    assertEquals(3, entries.size())

    val replacement = new ArrayList[String]()
    replacement.add("replacement")
    val firstEntry = entries.iterator().next()
    firstEntry.setValue(replacement)
    assertEquals(replacement, headers.get(firstEntry.getKey()))

    val removalIterator = entries.iterator()
    removalIterator.next()
    removalIterator.remove()
    assertTrue(removalIterator.hasNext())
    removalIterator.next()
    removalIterator.remove()
    assertEquals(1, entries.size())

    val survivor = entries.iterator().next().getValue()
    assertTrue(values.remove(survivor))
    assertTrue(headers.isEmpty())

    headers.add("X-Test", "again")
    headers.add("X-Other", "again")
    keys.clear()
    assertTrue(headers.isEmpty())
  }

  @Test def equalityAndHashCodeFollowMapContract(): Unit = {
    val first = new Headers()
    first.add("x-example", "one")
    val second = new Headers()
    second.add("X-EXAMPLE", "one")

    assertEquals(first, second)
    assertEquals(first.hashCode(), second.hashCode())
  }
}
