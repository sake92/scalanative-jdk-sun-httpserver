package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.Authenticator

import org.junit.Assert._
import org.junit.Test

/** JDK 8 baseline and protocol choices that differ from the JDK 21 reference. */
class BasicAuthenticatorBaselineTest {
  @Test def emptyRealmIsRejectedWithNullPointerException(): Unit = {
    try {
      new RecordingAuthenticator("")
      fail("expected NullPointerException")
    } catch {
      case _: NullPointerException => ()
    }
  }

  @Test def challengeUsesJdk8Form(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    val exchange = new HeaderExchange()

    assertTrue(authenticator.authenticate(exchange).isInstanceOf[Authenticator.Retry])
    assertEquals("users", authenticator.getRealm())
    assertEquals("Basic realm=\"users\"", exchange.getResponseHeaders().getFirst("WWW-Authenticate"))

    exchange.getRequestHeaders().set("Authorization", "Basic YWxpY2U6d3Jvbmc=")
    assertTrue(authenticator.authenticate(exchange).isInstanceOf[Authenticator.Failure])
    assertEquals("Basic realm=\"users\"", exchange.getResponseHeaders().getFirst("WWW-Authenticate"))
  }

  @Test def schemeIsCaseInsensitiveAndAllowsMultipleSpaces(): Unit = {
    for (scheme <- Seq("basic ", "BASIC ", "bAsIc ", "Basic  ", "basic   ")) {
      val authenticator = new RecordingAuthenticator("users")
      val exchange = new HeaderExchange()
      exchange.getRequestHeaders().set("Authorization", scheme + "YWxpY2U6c2VjcmV0")

      val result = authenticator.authenticate(exchange)

      assertTrue(scheme, result.isInstanceOf[Authenticator.Success])
      assertEquals(Seq(("alice", "secret")), authenticator.checked.toArray().toSeq)
      assertTrue(exchange.getResponseHeaders().isEmpty())
    }
  }

  @Test def malformedCredentialsFailWithoutCheckingCredentials(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    for (header <- Seq(
        "Basic !!!", "Basic YWxpY2U=", "Basic ", "Basic    ",
        "Basic YWxpY2U6c2VjcmV0=", "Basic YWxpY2U6c2VjcmV0 ",
        "Basic\tYWxpY2U6c2VjcmV0", "Basic O", "Basic YTpi====",
        "Basic YTo==", "Basic YW=x"
      )) {
      val exchange = new HeaderExchange()
      exchange.getRequestHeaders().set("Authorization", header)
      exchange.getResponseHeaders().set("X-Trace", "preserved")

      val result = authenticator.authenticate(exchange)

      assertTrue(header, result.isInstanceOf[Authenticator.Failure])
      assertEquals(401, result.asInstanceOf[Authenticator.Failure].getResponseCode())
      assertEquals(1, exchange.getResponseHeaders().size())
      assertEquals("preserved", exchange.getResponseHeaders().getFirst("X-Trace"))
    }
    assertTrue(authenticator.checked.isEmpty())
  }
}
