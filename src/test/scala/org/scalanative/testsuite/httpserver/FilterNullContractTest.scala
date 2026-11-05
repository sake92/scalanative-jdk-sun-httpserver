package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.{Filter, HttpExchange, HttpHandler}
import java.util.ArrayList

import org.junit.Assert._
import org.junit.Test

/** Both measured JDK runtimes pass null through to the callbacks. */
class FilterNullContractTest {
  @Test def nullExchangePassesThroughTheChain(): Unit = {
    val events = new ArrayList[String]()
    val filters = new ArrayList[Filter]()
    filters.add(new Filter {
      override def doFilter(exchange: HttpExchange, chain: Filter.Chain): Unit = {
        events.add("filter")
        chain.doFilter(exchange)
      }

      override def description(): String = "recording"
    })
    val handler = new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = events.add("handler")
    }
    val chain = new Filter.Chain(filters, handler)

    chain.doFilter(null)
    assertEquals(Seq("filter", "handler"), events.toArray().toSeq)
    events.clear()
    chain.doFilter(new HeaderExchange())
    assertEquals(Seq("handler"), events.toArray().toSeq)
    events.clear()
    chain.doFilter(null)
    assertEquals(Seq("handler"), events.toArray().toSeq)
  }
}
