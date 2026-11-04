package org.scalanative.testsuite.httpserver

object HeadersReferenceMain {
  def main(args: Array[String]): Unit = {
    HeadersContractScenarios.lookupIsCaseInsensitive()
    HeadersContractScenarios.addAppendsValues()
    HeadersContractScenarios.setReplacesValues()
    HeadersContractScenarios.getFirstReturnsFirstValue()
    HeadersContractScenarios.mapOperationsUseHeaderKeySemantics()
    HeadersContractScenarios.mapViewsRemainLive()
    HeadersContractScenarios.equalityAndHashCodeFollowMapContract()
    println("Passed: Total 7, Failed 0")
  }
}
