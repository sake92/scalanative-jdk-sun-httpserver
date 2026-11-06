package com.sun.net.httpserver.internal

import java.net.ProtocolException
import com.sun.net.httpserver.Headers

private[httpserver] object ConnectionPolicy {
  val drainBytes = 65536
  val drainNanos = 1000000000L

  def headerTimeoutMillis(): Int = {
    val value = System.getProperty("org.scalanative.httpserver.headerTimeoutMillis", "30000").toInt
    require(value > 0, "headerTimeoutMillis must be positive")
    value
  }

  def bodyReadTimeoutMillis(): Int = ioTimeoutMillis("bodyReadTimeoutMillis")

  def writeTimeoutMillis(): Int = ioTimeoutMillis("writeTimeoutMillis")

  def requestPersistent(head: RequestHeadParser.RequestHead): Boolean = {
    val values = head.headers.collect { case (name, value) if name.equalsIgnoreCase("Connection") => value }
    val options = tokens(values).getOrElse(throw new ProtocolException("Invalid Connection options"))
    !options.exists(_.equalsIgnoreCase("close")) &&
      (head.version == "HTTP/1.1" || options.exists(_.equalsIgnoreCase("keep-alive")))
  }

  def responseCloses(headers: Headers): Boolean = {
    val values = headers.get("Connection")
    if (values == null) false
    else {
      val builder = Vector.newBuilder[String]
      val iterator = values.iterator()
      while (iterator.hasNext()) builder += iterator.next()
      tokens(builder.result()) match {
        case Some(options) => options.exists(_.equalsIgnoreCase("close"))
        case None => true
      }
    }
  }

  private def ioTimeoutMillis(name: String): Int = {
    val value = System.getProperty("org.scalanative.httpserver." + name, "30000").toInt
    require(value >= 0, name + " must be nonnegative")
    value
  }

  private def tokens(values: Vector[String]): Option[Vector[String]] = {
    if (values.exists(_ == null)) return None
    val parts = values.flatMap(_.split(",", -1)).map(HttpSyntax.trimOws)
    val options = parts.filter(_.nonEmpty)
    if (options.forall(_.forall(HttpSyntax.isToken))) Some(options) else None
  }
}
