package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.HttpPrincipal

object HttpPrincipalContractScenarios {
  def identityFieldsAreRetained(): Unit = {
    val principal = new HttpPrincipal("alice", "users")

    expectEquals("alice", principal.getUsername())
    expectEquals("users", principal.getRealm())
  }

  def nameAndToStringUseRealmThenUsername(): Unit = {
    val principal = new HttpPrincipal("alice", "users")

    expectEquals("users:alice", principal.getName())
    expectEquals(principal.getName(), principal.toString())
  }

  def emptyAndNonAsciiValuesRoundTrip(): Unit = {
    val empty = new HttpPrincipal("", "")
    expectEquals("", empty.getUsername())
    expectEquals("", empty.getRealm())
    expectEquals(":", empty.getName())

    val unicode = new HttpPrincipal("ž用户", "област")
    expectEquals("ž用户", unicode.getUsername())
    expectEquals("област", unicode.getRealm())
    expectEquals("област:ž用户", unicode.getName())
  }

  def equalityUsesUsernameAndRealmRatherThanRenderedName(): Unit = {
    val first = new HttpPrincipal("b:c", "a")
    val second = new HttpPrincipal("c", "a:b")

    expectEquals(first.getName(), second.getName())
    expect(!first.equals(second), first.toString())
  }

  def equalityRejectsNullAndUnrelatedValues(): Unit = {
    val principal = new HttpPrincipal("alice", "users")

    expect(!principal.equals(null), principal.toString())
    expect(!principal.equals("users:alice"), principal.toString())
    expect(principal.equals(new HttpPrincipal("alice", "users")), principal.toString())
    expect(!principal.equals(new HttpPrincipal("other", "users")), principal.toString())
    expect(!principal.equals(new HttpPrincipal("alice", "other")), principal.toString())
  }

  def equalPrincipalsHaveEqualHashes(): Unit = {
    val first = new HttpPrincipal("ž用户", "област")
    val second = new HttpPrincipal("ž用户", "област")

    expectEquals(first.hashCode(), second.hashCode())
  }

  def hashCodeFollowsDocumentedUsernameRealmFormula(): Unit = {
    val principal = new HttpPrincipal("alice", "users")

    expectEquals("aliceusers".hashCode(), principal.hashCode())
  }

  def inheritedEqualityUsesConstructorIdentity(): Unit = {
    val base = new HttpPrincipal("alice", "users")
    val subclass = new OverridingHttpPrincipal(
      "alice",
      "users",
      "different-user",
      "different-realm",
      "different-name"
    )

    expect(base.equals(subclass), base.toString())
    expect(subclass.equals(base), subclass.toString())
    expectEquals(base.hashCode(), subclass.hashCode())
  }

  def toStringDelegatesToOverriddenGetName(): Unit = {
    val subclass = new OverridingHttpPrincipal(
      "alice",
      "users",
      "alice",
      "users",
      "overridden-name"
    )

    expectEquals("overridden-name", subclass.getName())
    expectEquals(subclass.getName(), subclass.toString())
  }

  def nullArgumentsAreRejected(): Unit = {
    expectNullPointerException(new HttpPrincipal(null, "users"))
    expectNullPointerException(new HttpPrincipal("alice", null))
  }

  private def expect(condition: Boolean, message: String): Unit =
    if (!condition) throw new AssertionError(message)

  private def expectEquals(expected: Any, actual: Any): Unit =
    if (expected != actual) throw new AssertionError(s"expected <$expected> but was <$actual>")

  private def expectNullPointerException(action: => Any): Unit = {
    try {
      action
      throw new AssertionError("expected NullPointerException")
    } catch {
      case _: NullPointerException => ()
    }
  }

  private class OverridingHttpPrincipal(
      constructorUsername: String,
      constructorRealm: String,
      username: String,
      realm: String,
      name: String
  ) extends HttpPrincipal(constructorUsername, constructorRealm) {
    override def getUsername(): String = username

    override def getRealm(): String = realm

    override def getName(): String = name
  }
}
