package org.scalanative.testsuite.httpserver

import org.junit.Test

class ExchangeContractTest {
  @Test def filtersRunInOrderAroundHandler(): Unit =
    ExchangeContractScenarios.filtersRunInOrderAroundHandler()

  @Test def filterAndHandlerExceptionsPropagate(): Unit =
    ExchangeContractScenarios.filterAndHandlerExceptionsPropagate()

  @Test def emptyFilterChainInvokesHandler(): Unit =
    ExchangeContractScenarios.emptyFilterChainInvokesHandler()

  @Test def filterMayShortCircuitWithoutCallingDownstream(): Unit =
    ExchangeContractScenarios.filterMayShortCircuitWithoutCallingDownstream()

  @Test def nonEmptyChainPropagatesHandlerIOExceptionUnchanged(): Unit =
    ExchangeContractScenarios.nonEmptyChainPropagatesHandlerIOExceptionUnchanged()
}
