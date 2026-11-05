package com.sun.net.httpserver

import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executor

/**
 * The abstract instance contract for a blocking HTTP server.
 *
 * The default implementation uses blocking sockets and closes each connection
 * after one exchange. Custom provider discovery and HTTPS remain deferred.
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

object HttpServer {
  @throws[IOException]
  def create(): HttpServer = new internal.BlockingHttpServer()

  @throws[IOException]
  def create(address: InetSocketAddress, backlog: Int): HttpServer = {
    val server = create()
    if (address != null) server.bind(address, backlog)
    server
  }
}
