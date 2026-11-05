package com.sun.net.httpserver

import java.util.{ArrayList, Collection, LinkedHashMap, List, Map, Set}

/**
 * HTTP request and response headers.
 *
 * Direct insertion normalizes ASCII names; JDK 8 bulk insertion preserves
 * the supplied spelling. Map views remain live. Direct writes validate
 * nulls and line breaks as the JDK 8 runtime does.
 */
class Headers private[httpserver] (private val entries: Map[String, List[String]])
    extends Map[String, List[String]] {
  def this() = this(new LinkedHashMap[String, List[String]]())

  override def size(): Int = entries.size()

  override def isEmpty(): Boolean = entries.isEmpty()

  override def containsKey(key: Object): Boolean = key match {
    case null => false
    case key: String => entries.containsKey(normalizeKey(key))
    case _ => false
  }

  override def containsValue(value: Object): Boolean = entries.containsValue(value)

  override def get(key: Object): List[String] = key match {
    case null => null
    case key: String => entries.get(normalizeKey(key))
    case _ => throw new ClassCastException("Header key must be a String")
  }

  override def put(key: String, value: List[String]): List[String] = {
    if (key == null || value == null) throw new NullPointerException("header")
    val iterator = value.iterator()
    while (iterator.hasNext()) validateValue(iterator.next())
    entries.put(normalizeKey(key), value)
  }

  override def remove(key: Object): List[String] = key match {
    case null => null
    case key: String => entries.remove(normalizeKey(key))
    case _ => throw new ClassCastException("Header key must be a String")
  }

  override def putAll(map: Map[_ <: String, _ <: List[String]]): Unit = entries.putAll(map)

  override def clear(): Unit = entries.clear()

  override def keySet(): Set[String] = entries.keySet()

  override def values(): Collection[List[String]] = entries.values()

  override def entrySet(): Set[Map.Entry[String, List[String]]] = entries.entrySet()

  override def equals(other: Any): Boolean = entries.equals(other.asInstanceOf[Object])

  override def hashCode(): Int = entries.hashCode()

  override def toString(): String = s"Headers { $entries }"

  /** Returns the first value associated with `key`, or null when absent. */
  def getFirst(key: String): String = {
    val values = get(key)
    if (values == null || values.isEmpty()) null else values.get(0)
  }

  /** Adds one value to the list associated with `key`. */
  def add(key: String, value: String): Unit = {
    validateValue(value)
    var values = get(key)
    if (values == null) {
      values = new ArrayList[String]()
      put(key, values)
    }
    values.add(value)
  }

  /** Replaces all values associated with `key` with one value. */
  def set(key: String, value: String): Unit = {
    val values = new ArrayList[String]()
    values.add(value)
    put(key, values)
  }

  private def normalizeKey(key: String): String = {
    if (key != null && (key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0))
      throw new IllegalArgumentException("Line break in header name")
    if (key == null || key.isEmpty()) key
    else {
      val normalized = key.toCharArray()
      var index = 0
      while (index < normalized.length) {
        val character = normalized(index)
        if (character >= 'A' && character <= 'Z') {
          normalized(index) = (character + ('a' - 'A')).toChar
        }
        index += 1
      }
      if (normalized(0) >= 'a' && normalized(0) <= 'z') {
        normalized(0) = (normalized(0) - ('a' - 'A')).toChar
      }
      new String(normalized)
    }
  }

  private def validateValue(value: String): Unit = {
    if (value == null) throw new NullPointerException("header value")
    var index = 0
    while (index < value.length) {
      val character = value.charAt(index)
      if (character == '\r') {
        if (index + 2 >= value.length || value.charAt(index + 1) != '\n' ||
            (value.charAt(index + 2) != ' ' && value.charAt(index + 2) != '\t'))
          throw new IllegalArgumentException("Invalid header line break")
        index += 1
      } else if (character == '\n') throw new IllegalArgumentException("Invalid header line break")
      index += 1
    }
  }
}
