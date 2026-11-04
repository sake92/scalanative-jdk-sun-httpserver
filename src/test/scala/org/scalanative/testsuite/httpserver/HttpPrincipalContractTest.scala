package org.scalanative.testsuite.httpserver

import org.junit.Test

class HttpPrincipalContractTest {
  @Test def identityFieldsAreRetained(): Unit =
    HttpPrincipalContractScenarios.identityFieldsAreRetained()

  @Test def nameAndToStringUseRealmThenUsername(): Unit =
    HttpPrincipalContractScenarios.nameAndToStringUseRealmThenUsername()

  @Test def emptyAndNonAsciiValuesRoundTrip(): Unit =
    HttpPrincipalContractScenarios.emptyAndNonAsciiValuesRoundTrip()

  @Test def equalityUsesUsernameAndRealmRatherThanRenderedName(): Unit =
    HttpPrincipalContractScenarios.equalityUsesUsernameAndRealmRatherThanRenderedName()

  @Test def equalityRejectsNullAndUnrelatedValues(): Unit =
    HttpPrincipalContractScenarios.equalityRejectsNullAndUnrelatedValues()

  @Test def equalPrincipalsHaveEqualHashes(): Unit =
    HttpPrincipalContractScenarios.equalPrincipalsHaveEqualHashes()

  @Test def hashCodeFollowsDocumentedUsernameRealmFormula(): Unit =
    HttpPrincipalContractScenarios.hashCodeFollowsDocumentedUsernameRealmFormula()

  @Test def inheritedEqualityUsesConstructorIdentity(): Unit =
    HttpPrincipalContractScenarios.inheritedEqualityUsesConstructorIdentity()

  @Test def toStringDelegatesToOverriddenGetName(): Unit =
    HttpPrincipalContractScenarios.toStringDelegatesToOverriddenGetName()

  @Test def nullArgumentsAreRejected(): Unit =
    HttpPrincipalContractScenarios.nullArgumentsAreRejected()
}
