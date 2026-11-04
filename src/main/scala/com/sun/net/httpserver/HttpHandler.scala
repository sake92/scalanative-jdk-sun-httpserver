package com.sun.net.httpserver

import java.io.IOException

/** A handler invoked to process HTTP exchanges. */
trait HttpHandler {
  @throws[IOException]
  def handle(exchange: HttpExchange): Unit
}
