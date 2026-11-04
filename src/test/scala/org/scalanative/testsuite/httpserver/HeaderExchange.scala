package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.{Headers, HttpContext, HttpExchange, HttpPrincipal}
import java.io.{InputStream, OutputStream}
import java.net.{InetSocketAddress, URI}

/** Only headers participate in authentication; other exchange behavior is deferred. */
private[httpserver] final class HeaderExchange extends HttpExchange {
  private val requestHeaders = new Headers()
  private val responseHeaders = new Headers()

  override def getRequestHeaders(): Headers = requestHeaders
  override def getResponseHeaders(): Headers = responseHeaders

  override def getRequestURI(): URI = unexpected()
  override def getRequestMethod(): String = unexpected()
  override def getHttpContext(): HttpContext = unexpected()
  override def close(): Unit = unexpected()
  override def getRequestBody(): InputStream = unexpected()
  override def getResponseBody(): OutputStream = unexpected()
  override def sendResponseHeaders(code: Int, length: Long): Unit = unexpected()
  override def getRemoteAddress(): InetSocketAddress = unexpected()
  override def getResponseCode(): Int = unexpected()
  override def getLocalAddress(): InetSocketAddress = unexpected()
  override def getProtocol(): String = unexpected()
  override def getAttribute(name: String): Object = unexpected()
  override def setAttribute(name: String, value: Object): Unit = unexpected()
  override def setStreams(input: InputStream, output: OutputStream): Unit = unexpected()
  override def getPrincipal(): HttpPrincipal = unexpected()

  private def unexpected(): Nothing =
    throw new AssertionError("exchange behavior outside the header fixture")
}
