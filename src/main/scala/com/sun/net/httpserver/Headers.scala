package com.sun.net.httpserver

import java.util.{ArrayList, Collection, Iterator, LinkedHashMap, List, Map, Set}

/**
 * HTTP request and response headers.
 *
 * Header names are stored in their conventional ASCII form (the first
 * character upper-case and the remaining ASCII letters lower-case), so map
 * lookups are case-insensitive while the map views remain live.
 * Null keys and values are retained because they are part of the JDK 8
 * contract for this class; a null key is not emitted by a server.
 */
class Headers private[httpserver] (private val entries: Map[String, List[String]])
    extends Map[String, List[String]] {
  def this() = this(new LinkedHashMap[String, List[String]]())

  override def size(): Int = entries.size()

  override def isEmpty(): Boolean = entries.isEmpty()

  override def containsKey(key: Object): Boolean = key match {
    case null => entries.containsKey(null)
    case key: String => entries.containsKey(normalizeKey(key))
    case _ => false
  }

  override def containsValue(value: Object): Boolean = entries.containsValue(value)

  override def get(key: Object): List[String] = key match {
    case null => entries.get(null)
    case key: String => entries.get(normalizeKey(key))
    case _ => null
  }

  override def put(key: String, value: List[String]): List[String] =
    entries.put(normalizeKey(key), value)

  override def remove(key: Object): List[String] = key match {
    case null => entries.remove(null)
    case key: String => entries.remove(normalizeKey(key))
    case _ => null
  }

  override def putAll(map: Map[_ <: String, _ <: List[String]]): Unit = {
    val iterator = map.entrySet().iterator().asInstanceOf[Iterator[Map.Entry[String, List[String]]]]
    while (iterator.hasNext()) {
      val entry = iterator.next()
      put(entry.getKey(), entry.getValue())
    }
  }

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
}
