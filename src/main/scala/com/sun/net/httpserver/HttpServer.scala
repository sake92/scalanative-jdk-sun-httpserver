package com.sun.net.httpserver

import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executor

/**
 * The abstract instance contract for a blocking HTTP server.
 *
 * Static provider factories and the concrete socket implementation belong to
 * the later server phase. This class exists now because `HttpContext` exposes
 * its owning server as part of the JDK 8 API.
 */
abstract class HttpServer protected () {
  @throws[IOException]
  def bind(address: InetSocketAddress, backlog: Int): Unit

  def start(): Unit

  def setExecutor(executor: Executor): Unit

  def getExecutor(): Executor

  def stop(delay: Int): Unit

  def createContext(path: String, handler: HttpHandler): HttpContext

  def createContext(path: String): HttpContext

  def removeContext(path: String): Unit

  def removeContext(context: HttpContext): Unit

  def getAddress(): InetSocketAddress
}
