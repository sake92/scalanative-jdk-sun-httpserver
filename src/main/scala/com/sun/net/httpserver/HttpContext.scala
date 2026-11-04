package com.sun.net.httpserver

import java.util.{List, Map}

/** Maps a URI path to an HTTP handler and its filter/configuration state. */
abstract class HttpContext protected () {
  def getHandler(): HttpHandler

  def setHandler(handler: HttpHandler): Unit

  def getPath(): String

  def getServer(): HttpServer

  def getAttributes(): Map[String, Object]

  def getFilters(): List[Filter]

  def setAuthenticator(authenticator: Authenticator): Authenticator

  def getAuthenticator(): Authenticator
}
