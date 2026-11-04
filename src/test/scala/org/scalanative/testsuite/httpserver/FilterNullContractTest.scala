package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.{Filter, HttpExchange, HttpHandler}
import java.util.ArrayList

import org.junit.Assert._
import org.junit.Test

/** Native follows the documented JDK 8 null contract; JDK 21 does not. */
class FilterNullContractTest {
  @Test def nullExchangeDoesNotAdvanceChain(): Unit = {
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

    try {
      chain.doFilter(null)
      fail("expected NullPointerException")
    } catch {
      case _: NullPointerException => ()
    }
    assertTrue(events.isEmpty())

    chain.doFilter(new HeaderExchange())
    assertEquals(Seq("filter", "handler"), events.toArray().toSeq)

    try {
      chain.doFilter(null)
      fail("expected NullPointerException for exhausted chain")
    } catch {
      case _: NullPointerException => ()
    }
    assertEquals(2, events.size())
  }
}
