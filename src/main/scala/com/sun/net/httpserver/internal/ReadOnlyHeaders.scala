package com.sun.net.httpserver.internal

import java.util.{AbstractMap, ArrayList, Collections, LinkedHashSet, List, Map, Set}
import java.util.function.{BiFunction, Function}
import com.sun.net.httpserver.Headers

private[httpserver] object ReadOnlyHeaders {
  private def backing(fields: Vector[(String, String)]): Map[String, List[String]] = {
    val headers = new Headers()
    fields.foreach { case (name, value) => headers.add(name, value) }
    val entries = headers.entrySet().iterator()
    while (entries.hasNext()) {
      val entry = entries.next()
      entry.setValue(Collections.unmodifiableList(new ArrayList[String](entry.getValue())))
    }
    Collections.unmodifiableMap(headers)
  }
}

/** Both the map and each value list are immutable, including no-op mutations. */
private[httpserver] final class ReadOnlyHeaders(fields: Vector[(String, String)])
    extends Headers(ReadOnlyHeaders.backing(fields)) {
  // Native's unmodifiableMap protects the set, but not entry.setValue.
  private val immutableEntries: Set[Map.Entry[String, List[String]]] = {
    val entries = new LinkedHashSet[Map.Entry[String, List[String]]]()
    val iterator = super.entrySet().iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      entries.add(new AbstractMap.SimpleImmutableEntry(entry.getKey(), entry.getValue()))
    }
    Collections.unmodifiableSet(entries)
  }

  override def entrySet(): Set[Map.Entry[String, List[String]]] = immutableEntries
  override def putAll(map: Map[_ <: String, _ <: List[String]]): Unit = immutable()
  override def putIfAbsent(key: String, value: List[String]): List[String] = immutable()
  override def remove(key: Object, value: Object): Boolean = immutable()
  override def replace(key: String, value: List[String]): List[String] = immutable()
  override def replace(key: String, oldValue: List[String], value: List[String]): Boolean = immutable()
  override def replaceAll(f: BiFunction[_ >: String, _ >: List[String], _ <: List[String]]): Unit = immutable()
  override def computeIfAbsent(key: String, f: Function[_ >: String, _ <: List[String]]): List[String] = immutable()
  override def computeIfPresent(key: String, f: BiFunction[_ >: String, _ >: List[String], _ <: List[String]]): List[String] = immutable()
  override def compute(key: String, f: BiFunction[_ >: String, _ >: List[String], _ <: List[String]]): List[String] = immutable()
  override def merge(key: String, value: List[String], f: BiFunction[_ >: List[String], _ >: List[String], _ <: List[String]]): List[String] = immutable()

  private def immutable(): Nothing = throw new UnsupportedOperationException("Read-only request headers")
}
