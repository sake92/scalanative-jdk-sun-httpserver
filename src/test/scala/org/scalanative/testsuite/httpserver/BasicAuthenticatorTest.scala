package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.{Authenticator, BasicAuthenticator, HttpPrincipal}
import java.nio.charset.Charset
import java.util.{ArrayList, Base64}

import org.junit.Assert._
import org.junit.Test

class BasicAuthenticatorTest {
  @Test def missingCredentialsRequestRetryWithChallenge(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    val exchange = new HeaderExchange()
    exchange.getResponseHeaders().set("X-Trace", "preserved")
    exchange.getResponseHeaders().add("WWW-Authenticate", "old challenge")

    val result = authenticator.authenticate(exchange)

    assertTrue(result.isInstanceOf[Authenticator.Retry])
    assertEquals(401, result.asInstanceOf[Authenticator.Retry].getResponseCode())
    assertChallenge(exchange)
    assertEquals("preserved", exchange.getResponseHeaders().getFirst("X-Trace"))
    assertTrue(authenticator.checked.isEmpty())
  }

  @Test def unsupportedOrIncompleteSchemeDoesNotCheckCredentials(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    for (header <- Seq("Bearer YWxpY2U6c2VjcmV0", "Basic", "")) {
      val exchange = new HeaderExchange()
      exchange.getRequestHeaders().set("Authorization", header)

      val result = authenticator.authenticate(exchange)

      assertTrue(result.isInstanceOf[Authenticator.Failure])
      assertEquals(401, result.asInstanceOf[Authenticator.Failure].getResponseCode())
      assertTrue(exchange.getResponseHeaders().isEmpty())
    }
    assertTrue(authenticator.checked.isEmpty())
  }

  @Test def validCredentialsProducePrincipal(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    val exchange = new HeaderExchange()
    // A literal wire example prevents the encoder and decoder from hiding the same bug.
    exchange.getRequestHeaders().set("Authorization", "Basic YWxpY2U6c2VjcmV0")
    exchange.getResponseHeaders().set("X-Trace", "preserved")

    val result = authenticator.authenticate(exchange)

    assertTrue(result.isInstanceOf[Authenticator.Success])
    val principal = result.asInstanceOf[Authenticator.Success].getPrincipal()
    assertEquals(new HttpPrincipal("alice", "users"), principal)
    assertEquals(Seq(("alice", "secret")), authenticator.checked.toArray().toSeq)
    assertEquals(1, exchange.getResponseHeaders().size())
    assertEquals("preserved", exchange.getResponseHeaders().getFirst("X-Trace"))
  }

  @Test def invalidCredentialsProduceFailureWithChallenge(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    val exchange = new HeaderExchange()
    exchange.getRequestHeaders().set("Authorization", "Basic YWxpY2U6d3Jvbmc=")

    val result = authenticator.authenticate(exchange)

    assertTrue(result.isInstanceOf[Authenticator.Failure])
    assertEquals(401, result.asInstanceOf[Authenticator.Failure].getResponseCode())
    assertEquals(Seq(("alice", "wrong")), authenticator.checked.toArray().toSeq)
    assertChallenge(exchange)
  }

  @Test def firstColonSeparatesUsernameFromPassword(): Unit = {
    for ((username, password, token) <- Seq(
        ("alice", "one:two", "YWxpY2U6b25lOnR3bw=="),
        ("alice", "one:two", "YWxpY2U6b25lOnR3bw"),
        ("a", "bc", "YTpiYw=="),
        ("a", "bc", "YTpiYw"),
        ("", "secret", "OnNlY3JldA=="),
        ("alice", "", "YWxpY2U6"),
        ("", "", "Og==")
      )) {
      val authenticator = new RecordingAuthenticator("users", username, password)
      val exchange = new HeaderExchange()
      exchange.getRequestHeaders().set("Authorization", "Basic " + token)

      val result = authenticator.authenticate(exchange)

      assertTrue(result.isInstanceOf[Authenticator.Success])
      assertEquals(Seq((username, password)), authenticator.checked.toArray().toSeq)
      assertEquals(
        new HttpPrincipal(username, "users"),
        result.asInstanceOf[Authenticator.Success].getPrincipal()
      )
    }
  }

  @Test def credentialsUseDefaultCharset(): Unit = {
    val username = "caf\u00e9"
    val password = "p\u00e4ss:word"
    val authenticator = new RecordingAuthenticator("users", username, password)
    val exchange = new HeaderExchange()
    val bytes = (username + ":" + password).getBytes(Charset.defaultCharset())
    val token = Base64.getEncoder().encodeToString(bytes)
    exchange.getRequestHeaders().set("Authorization", "Basic " + token)

    val result = authenticator.authenticate(exchange)

    assertTrue(result.isInstanceOf[Authenticator.Success])
    assertEquals(Seq((username, password)), authenticator.checked.toArray().toSeq)
    assertEquals(
      new HttpPrincipal(username, "users"),
      result.asInstanceOf[Authenticator.Success].getPrincipal()
    )
  }

  @Test def credentialsAreCheckedForEveryExchange(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    val exchange = new HeaderExchange()
    exchange.getRequestHeaders().set("Authorization", "Basic YWxpY2U6c2VjcmV0")
    assertTrue(authenticator.authenticate(exchange).isInstanceOf[Authenticator.Success])
    assertTrue(authenticator.authenticate(exchange).isInstanceOf[Authenticator.Success])

    exchange.getRequestHeaders().set("Authorization", "Basic YWxpY2U6d3Jvbmc=")
    assertTrue(authenticator.authenticate(exchange).isInstanceOf[Authenticator.Failure])
    assertTrue(authenticator.authenticate(exchange).isInstanceOf[Authenticator.Failure])
    assertEquals(
      Seq(("alice", "secret"), ("alice", "secret"), ("alice", "wrong"), ("alice", "wrong")),
      authenticator.checked.toArray().toSeq
    )
  }

  @Test def credentialCheckerExceptionPropagates(): Unit = {
    val failure = new IllegalArgumentException("credential backend failed")
    val authenticator = new BasicAuthenticator("users") {
      override def checkCredentials(username: String, password: String): Boolean =
        throw failure
    }
    val exchange = new HeaderExchange()
    exchange.getRequestHeaders().set("Authorization", "Basic YWxpY2U6c2VjcmV0")

    try {
      authenticator.authenticate(exchange)
      fail("expected credential checker failure")
    } catch {
      case error: IllegalArgumentException => assertSame(failure, error)
    }
    assertTrue(exchange.getResponseHeaders().isEmpty())
  }

  @Test def firstAuthorizationValueIsUsed(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    val exchange = new HeaderExchange()
    exchange.getRequestHeaders().add("authorization", "Basic YWxpY2U6d3Jvbmc=")
    exchange.getRequestHeaders().add("AUTHORIZATION", "Basic YWxpY2U6c2VjcmV0")

    val result = authenticator.authenticate(exchange)

    assertTrue(result.isInstanceOf[Authenticator.Failure])
    assertEquals(401, result.asInstanceOf[Authenticator.Failure].getResponseCode())
    assertEquals(Seq(("alice", "wrong")), authenticator.checked.toArray().toSeq)
    assertChallenge(exchange)
  }

  @Test def nullRealmIsRejected(): Unit = {
    try {
      new RecordingAuthenticator(null)
      fail("expected NullPointerException")
    } catch {
      case _: NullPointerException => ()
    }
  }

  private def assertChallenge(exchange: HeaderExchange): Unit = {
    val values = exchange.getResponseHeaders().get("WWW-Authenticate")
    assertNotNull(values)
    assertEquals(1, values.size())
    val challenge = values.get(0)
    // JDK 8 baseline and the newer JDK UTF-8 advertisement are distinct supported observations.
    assertTrue(
      challenge,
      challenge == "Basic realm=\"users\"" ||
        challenge == "Basic realm=\"users\", charset=\"UTF-8\""
    )
  }
}

private[httpserver] final class RecordingAuthenticator(
    realm: String,
    acceptedUsername: String = "alice",
    acceptedPassword: String = "secret"
) extends BasicAuthenticator(realm) {
  val checked = new ArrayList[(String, String)]()

  override def checkCredentials(username: String, password: String): Boolean = {
    checked.add((username, password))
    username == acceptedUsername && password == acceptedPassword
  }
}
