package org.scalanative.testsuite.httpserver

import org.junit.Test

class HeadersContractTest {
  @Test def lookupIsCaseInsensitive(): Unit = HeadersContractScenarios.lookupIsCaseInsensitive()

  @Test def addAppendsValues(): Unit = HeadersContractScenarios.addAppendsValues()

  @Test def setReplacesValues(): Unit = HeadersContractScenarios.setReplacesValues()

  @Test def getFirstReturnsFirstValue(): Unit = HeadersContractScenarios.getFirstReturnsFirstValue()

  @Test def mapOperationsUseHeaderKeySemantics(): Unit =
    HeadersContractScenarios.mapOperationsUseHeaderKeySemantics()

  @Test def mapViewsRemainLive(): Unit = HeadersContractScenarios.mapViewsRemainLive()

  @Test def equalityAndHashCodeFollowMapContract(): Unit =
    HeadersContractScenarios.equalityAndHashCodeFollowMapContract()
}
