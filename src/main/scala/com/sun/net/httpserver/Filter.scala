package com.sun.net.httpserver

import java.io.IOException
import java.util.{List, ListIterator}

/** A pre/post-processing filter associated with an HTTP context. */
abstract class Filter protected () {
  @throws[IOException]
  def doFilter(exchange: HttpExchange, chain: Filter.Chain): Unit

  def description(): String
}

object Filter {
  /** A chain that invokes each filter and finally the exchange handler. */
  class Chain(filters: List[Filter], handler: HttpHandler) {
    if (filters == null) throw new NullPointerException("filters")
    if (handler == null) throw new NullPointerException("handler")

    private val iterator: ListIterator[Filter] = filters.listIterator()

    @throws[IOException]
    def doFilter(exchange: HttpExchange): Unit = {
      if (iterator.hasNext()) iterator.next().doFilter(exchange, this)
      else handler.handle(exchange)
    }
  }
}
