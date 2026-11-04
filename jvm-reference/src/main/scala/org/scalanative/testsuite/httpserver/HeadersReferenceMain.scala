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
    HttpPrincipalContractScenarios.identityFieldsAreRetained()
    HttpPrincipalContractScenarios.nameAndToStringUseRealmThenUsername()
    HttpPrincipalContractScenarios.emptyAndNonAsciiValuesRoundTrip()
    HttpPrincipalContractScenarios.equalityUsesUsernameAndRealmRatherThanRenderedName()
    HttpPrincipalContractScenarios.equalityRejectsNullAndUnrelatedValues()
    HttpPrincipalContractScenarios.equalPrincipalsHaveEqualHashes()
    HttpPrincipalContractScenarios.hashCodeFollowsDocumentedUsernameRealmFormula()
    HttpPrincipalContractScenarios.inheritedEqualityUsesConstructorIdentity()
    HttpPrincipalContractScenarios.toStringDelegatesToOverriddenGetName()
    HttpPrincipalContractScenarios.nullArgumentsAreRejected()
    ExchangeContractScenarios.filtersRunInOrderAroundHandler()
    ExchangeContractScenarios.filterAndHandlerExceptionsPropagate()
    ExchangeContractScenarios.emptyFilterChainInvokesHandler()
    ExchangeContractScenarios.filterMayShortCircuitWithoutCallingDownstream()
    ExchangeContractScenarios.nonEmptyChainPropagatesHandlerIOExceptionUnchanged()
    println("Passed: Total 22, Failed 0")
  }
}
