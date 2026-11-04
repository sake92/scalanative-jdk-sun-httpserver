package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.{Filter, HttpExchange, HttpHandler}
import java.io.IOException
import java.util.{ArrayList, List => JavaList}

object ExchangeContractScenarios {
  def filtersRunInOrderAroundHandler(): Unit = {
    val events = new ArrayList[String]()
    val filters = new ArrayList[Filter]()
    filters.add(new RecordingFilter("first", events))
    filters.add(new RecordingFilter("second", events))
    val handler = new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = events.add("handler")
    }

    new Filter.Chain(filters, handler).doFilter(null)
    expectListEquals(
      Array("first-before", "second-before", "handler", "second-after", "first-after"),
      events
    )
  }

  def filterAndHandlerExceptionsPropagate(): Unit = {
    val filterFailure = new IOException("filter")
    val filter = new Filter {
      override def doFilter(exchange: HttpExchange, chain: Filter.Chain): Unit =
        throw filterFailure

      override def description(): String = "failure"
    }
    val filters = new ArrayList[Filter]()
    filters.add(filter)
    expectSame(filterFailure, expectIOException(new Filter.Chain(filters, noOpHandler).doFilter(null)))

    val handlerFailure = new IOException("handler")
    val handler = new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = throw handlerFailure
    }
    expectSame(handlerFailure, expectIOException(new Filter.Chain(new ArrayList[Filter](), handler).doFilter(null)))
  }

  def emptyFilterChainInvokesHandler(): Unit = {
    val calls = new ArrayList[String]()
    val handler = new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = calls.add("called")
    }

    new Filter.Chain(new ArrayList[Filter](), handler).doFilter(null)
    expectListEquals(Array("called"), calls)
  }

  def filterMayShortCircuitWithoutCallingDownstream(): Unit = {
    val events = new ArrayList[String]()
    val shortCircuit = new Filter {
      override def doFilter(exchange: HttpExchange, chain: Filter.Chain): Unit =
        events.add("short-circuit")

      override def description(): String = "short-circuit"
    }
    val downstream = new RecordingFilter("downstream", events)
    val handler = new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = events.add("handler")
    }
    val filters = new ArrayList[Filter]()
    filters.add(shortCircuit)
    filters.add(downstream)

    new Filter.Chain(filters, handler).doFilter(null)
    expectListEquals(Array("short-circuit"), events)
  }

  def nonEmptyChainPropagatesHandlerIOExceptionUnchanged(): Unit = {
    val events = new ArrayList[String]()
    val filter = new RecordingFilter("before-handler", events)
    val failure = new IOException("handler failure")
    val handler = new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = throw failure
    }
    val filters = new ArrayList[Filter]()
    filters.add(filter)

    val propagated = expectIOException(new Filter.Chain(filters, handler).doFilter(null))
    expectSame(failure, propagated)
    expectListEquals(Array("before-handler-before"), events)
  }

  private val noOpHandler = new HttpHandler {
    override def handle(exchange: HttpExchange): Unit = ()
  }

  private def expectEquals(expected: Any, actual: Any): Unit =
    if (expected != actual) throw new AssertionError(s"expected <$expected> but was <$actual>")

  private def expectSame(expected: AnyRef, actual: AnyRef): Unit =
    if (expected ne actual) throw new AssertionError(s"expected same <$expected> but was <$actual>")

  private def expectListEquals(expected: Array[String], actual: JavaList[String]): Unit = {
    expectEquals(expected.length, actual.size())
    var index = 0
    while (index < expected.length) {
      expectEquals(expected(index), actual.get(index))
      index += 1
    }
  }

  private def expectIOException(action: => Any): IOException = {
    try {
      action
      throw new AssertionError("expected IOException")
    } catch {
      case error: IOException => error
    }
  }

  private final class RecordingFilter(name: String, events: ArrayList[String]) extends Filter {
    override def doFilter(exchange: HttpExchange, chain: Filter.Chain): Unit = {
      events.add(s"$name-before")
      chain.doFilter(exchange)
      events.add(s"$name-after")
    }

    override def description(): String = name
  }
}
