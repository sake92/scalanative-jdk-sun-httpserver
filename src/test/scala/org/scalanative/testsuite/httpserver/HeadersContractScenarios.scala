package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.Headers
import java.util.{ArrayList, LinkedHashMap}

object HeadersContractScenarios {
  def lookupIsCaseInsensitive(): Unit = {
    val headers = new Headers()
    headers.add("Content-Type", "text/plain")

    expectEquals("text/plain", headers.getFirst("content-type"))
    expect(headers.containsKey("CONTENT-TYPE"), headers.toString())
    expectEquals(Seq("Content-type"), headers.keySet().toArray().toSeq)
  }

  def addAppendsValues(): Unit = {
    val headers = new Headers()
    headers.add("Set-Cookie", "a=1")
    headers.add("set-cookie", "b=2")

    expectEquals(Seq("a=1", "b=2"), headers.get("SET-COOKIE").toArray().toSeq)
  }

  def setReplacesValues(): Unit = {
    val headers = new Headers()
    headers.add("Cache-Control", "no-cache")
    headers.add("cache-control", "private")
    headers.set("CACHE-CONTROL", "public")

    expectEquals(Seq("public"), headers.get("cache-control").toArray().toSeq)
  }

  def getFirstReturnsFirstValue(): Unit = {
    val headers = new Headers()
    expect(headers.getFirst("Missing") == null, headers.toString())
    headers.add("Warning", "one")
    headers.add("Warning", "two")

    expectEquals("one", headers.getFirst("warning"))
  }

  def mapOperationsUseHeaderKeySemantics(): Unit = {
    val headers = new Headers()
    val previous = new ArrayList[String]()
    previous.add("first")
    expect(headers.put("X-Trace", previous) == null, headers.toString())
    val replacement = new ArrayList[String]()
    replacement.add("replacement")
    expectEquals(previous, headers.put("x-trace", replacement))

    val additional = new LinkedHashMap[String, java.util.List[String]]()
    val values = new ArrayList[String]()
    values.add("second")
    additional.put("X-Other", values)
    headers.putAll(additional)

    expect(headers.containsKey("X-TRACE"), headers.toString())
    expectEquals(values, headers.get("x-other"))
    expectEquals(replacement, headers.remove("X-TRACE"))
    expect(!headers.containsKey("x-trace"), headers.toString())
  }

  def mapViewsRemainLive(): Unit = {
    val headers = new Headers()
    headers.add("X-Test", "one")
    headers.add("X-Other", "other")
    val keys = headers.keySet()
    val values = headers.values()
    val entries = headers.entrySet()

    expect(keys.contains("X-test"), keys.toString())
    expect(values.contains(headers.get("x-test")), values.toString())
    expectEquals(2, entries.size())

    headers.add("X-Later", "later")
    expectEquals(3, keys.size())
    expectEquals(3, values.size())
    expectEquals(3, entries.size())

    val replacement = new ArrayList[String]()
    replacement.add("replacement")
    val firstEntry = entries.iterator().next()
    firstEntry.setValue(replacement)
    expectEquals(replacement, headers.get(firstEntry.getKey()))

    val removalIterator = entries.iterator()
    removalIterator.next()
    removalIterator.remove()
    expect(removalIterator.hasNext(), entries.toString())
    removalIterator.next()
    removalIterator.remove()
    expectEquals(1, entries.size())

    val survivor = entries.iterator().next().getValue()
    expect(values.remove(survivor), values.toString())
    expect(headers.isEmpty(), headers.toString())

    headers.add("X-Test", "again")
    headers.add("X-Other", "again")
    keys.clear()
    expect(headers.isEmpty(), headers.toString())
  }

  def equalityAndHashCodeFollowMapContract(): Unit = {
    val first = new Headers()
    first.add("x-example", "one")
    val second = new Headers()
    second.add("X-EXAMPLE", "one")

    expectEquals(first, second)
    expectEquals(first.hashCode(), second.hashCode())
  }

  private def expect(condition: Boolean, message: String): Unit =
    if (!condition) throw new AssertionError(message)

  private def expectEquals(expected: Any, actual: Any): Unit =
    if (expected != actual) throw new AssertionError(s"expected <$expected> but was <$actual>")
}
