package com.sun.net.httpserver.internal

import java.util.{ArrayList, Collections, HashMap, List, Map}
import com.sun.net.httpserver.{Authenticator, Filter, HttpContext, HttpHandler, HttpServer}

private[httpserver] final class ServerContext(
    private val server: HttpServer,
    private val path: String,
    initialHandler: HttpHandler
) extends HttpContext {
  private val attributes = Collections.synchronizedMap(new HashMap[String, Object]())
  private val filters = Collections.synchronizedList(new ArrayList[Filter]())
  @volatile private var handler = initialHandler
  @volatile private var authenticator: Authenticator = null

  override def getHandler(): HttpHandler = handler
  override def getPath(): String = path
  override def getServer(): HttpServer = server
  override def getAttributes(): Map[String, Object] = attributes
  override def getFilters(): List[Filter] = filters
  override def getAuthenticator(): Authenticator = authenticator

  override def setHandler(value: HttpHandler): Unit = synchronized {
    if (value == null) throw new NullPointerException("handler")
    if (handler != null) throw new IllegalArgumentException("Handler already set")
    handler = value
  }

  override def setAuthenticator(value: Authenticator): Authenticator = synchronized {
    val previous = authenticator
    authenticator = value
    previous
  }

  def handle(exchange: ServerExchange): Unit = {
    val current = handler
    if (current == null) {
      exchange.sendResponseHeaders(404, -1)
      return
    }
    val auth = authenticator
    if (auth != null) {
      auth.authenticate(exchange) match {
        case success: Authenticator.Success => exchange.setPrincipal(success.getPrincipal())
        case failure: Authenticator.Failure =>
          exchange.sendResponseHeaders(failure.getResponseCode(), -1)
          return
        case retry: Authenticator.Retry =>
          exchange.sendResponseHeaders(retry.getResponseCode(), -1)
          return
        case _ => throw new IllegalStateException("Invalid authenticator result")
      }
    }
    val snapshot = filters.synchronized { new ArrayList[Filter](filters) }
    new Filter.Chain(snapshot, current).doFilter(exchange)
  }
}
