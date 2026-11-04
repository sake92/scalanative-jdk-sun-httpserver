package com.sun.net.httpserver

import java.io.{InputStream, IOException, OutputStream}
import java.net.{InetSocketAddress, URI}

/**
 * Encapsulates an HTTP request and the response generated for that request.
 *
 * This is the JDK 8-era abstract surface. In particular, `getResponseCode`
 * is part of that baseline even though later JDKs also add newer request APIs.
 */
abstract class HttpExchange protected () {
  def getRequestHeaders(): Headers

  def getResponseHeaders(): Headers

  def getRequestURI(): URI

  def getRequestMethod(): String

  def getHttpContext(): HttpContext

  def close(): Unit

  def getRequestBody(): InputStream

  def getResponseBody(): OutputStream

  @throws[IOException]
  def sendResponseHeaders(responseCode: Int, responseLength: Long): Unit

  def getRemoteAddress(): InetSocketAddress

  def getResponseCode(): Int

  def getLocalAddress(): InetSocketAddress

  def getProtocol(): String

  def getAttribute(name: String): Object

  def setAttribute(name: String, value: Object): Unit

  def setStreams(input: InputStream, output: OutputStream): Unit

  def getPrincipal(): HttpPrincipal
}
