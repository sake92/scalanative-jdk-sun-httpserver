package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.HttpPrincipal

import org.junit.Assert._
import org.junit.Test

class HttpPrincipalTest {
  @Test def identityFieldsAreRetained(): Unit = {
    val principal = new HttpPrincipal("alice", "users")

    assertEquals("alice", principal.getUsername())
    assertEquals("users", principal.getRealm())
  }

  @Test def nameAndToStringUseRealmThenUsername(): Unit = {
    val principal = new HttpPrincipal("alice", "users")

    assertEquals("users:alice", principal.getName())
    assertEquals(principal.getName(), principal.toString())
  }

  @Test def emptyAndNonAsciiValuesRoundTrip(): Unit = {
    val empty = new HttpPrincipal("", "")
    assertEquals("", empty.getUsername())
    assertEquals("", empty.getRealm())
    assertEquals(":", empty.getName())

    val unicode = new HttpPrincipal("ž用户", "област")
    assertEquals("ž用户", unicode.getUsername())
    assertEquals("област", unicode.getRealm())
    assertEquals("област:ž用户", unicode.getName())
  }

  @Test def equalityUsesUsernameAndRealmRatherThanRenderedName(): Unit = {
    val first = new HttpPrincipal("b:c", "a")
    val second = new HttpPrincipal("c", "a:b")

    assertEquals(first.getName(), second.getName())
    assertNotEquals(first, second)
  }

  @Test def equalityRejectsNullAndUnrelatedValues(): Unit = {
    val principal = new HttpPrincipal("alice", "users")

    assertNotEquals(principal, null)
    assertNotEquals(principal, "users:alice")
    assertEquals(principal, new HttpPrincipal("alice", "users"))
    assertNotEquals(principal, new HttpPrincipal("other", "users"))
    assertNotEquals(principal, new HttpPrincipal("alice", "other"))
  }

  @Test def equalPrincipalsHaveEqualHashes(): Unit = {
    val first = new HttpPrincipal("ž用户", "област")
    val second = new HttpPrincipal("ž用户", "област")

    assertEquals(first.hashCode(), second.hashCode())
  }

  @Test def hashCodeFollowsDocumentedUsernameRealmFormula(): Unit = {
    val principal = new HttpPrincipal("alice", "users")

    assertEquals("aliceusers".hashCode(), principal.hashCode())
  }

  @Test def inheritedEqualityUsesConstructorIdentity(): Unit = {
    val base = new HttpPrincipal("alice", "users")
    val subclass = new OverridingHttpPrincipal(
      "alice",
      "users",
      "different-user",
      "different-realm",
      "different-name"
    )

    assertEquals(base, subclass)
    assertEquals(subclass, base)
    assertEquals(base.hashCode(), subclass.hashCode())
  }

  @Test def toStringDelegatesToOverriddenGetName(): Unit = {
    val subclass = new OverridingHttpPrincipal(
      "alice",
      "users",
      "alice",
      "users",
      "overridden-name"
    )

    assertEquals("overridden-name", subclass.getName())
    assertEquals(subclass.getName(), subclass.toString())
  }

  @Test def nullArgumentsAreRejected(): Unit = {
    assertNullPointerException(new HttpPrincipal(null, "users"))
    assertNullPointerException(new HttpPrincipal("alice", null))
  }

  private def assertNullPointerException(action: => Any): Unit = {
    try {
      action
      fail("expected NullPointerException")
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
