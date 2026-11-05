package org.scalanative.testsuite.httpserver

import com.sun.net.httpserver.Authenticator
import org.junit.Assert._
import org.junit.Test

/** Runtime compatibility, including explicitly different newer-JDK constructors. */
class BasicAuthenticatorBaselineTest {
  @Test def emptyRealmFollowsRuntimeBaseline(): Unit = {
    if (ReferenceRuntime.jdk8) assertEquals("", new RecordingAuthenticator("").getRealm())
    else assertThrows(classOf[IllegalArgumentException], () => new RecordingAuthenticator(""))
  }

  @Test def challengeUsesRuntimeForm(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    val exchange = new HeaderExchange()
    val expected = if (ReferenceRuntime.jdk8) "Basic realm=\"users\""
      else "Basic realm=\"users\", charset=\"UTF-8\""
    assertTrue(authenticator.authenticate(exchange).isInstanceOf[Authenticator.Retry])
    assertEquals(expected, exchange.getResponseHeaders().getFirst("WWW-Authenticate"))
    exchange.getRequestHeaders().set("Authorization", "Basic YWxpY2U6d3Jvbmc=")
    assertTrue(authenticator.authenticate(exchange).isInstanceOf[Authenticator.Failure])
    assertEquals(expected, exchange.getResponseHeaders().getFirst("WWW-Authenticate"))
  }

  @Test def schemeIsCaseSensitiveAndPayloadHasOneSeparator(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    for (scheme <- Seq("basic ", "BASIC ", "bAsIc ", "Basic\t")) {
      val exchange = new HeaderExchange()
      exchange.getRequestHeaders().set("Authorization", scheme + "YWxpY2U6c2VjcmV0")
      val result = authenticator.authenticate(exchange)
      assertTrue(scheme, result.isInstanceOf[Authenticator.Failure])
      assertEquals(401, result.asInstanceOf[Authenticator.Failure].getResponseCode())
      assertTrue(exchange.getResponseHeaders().isEmpty())
    }
    val exchange = new HeaderExchange()
    exchange.getRequestHeaders().set("Authorization", "Basic  YWxpY2U6c2VjcmV0")
    assertThrows(classOf[IllegalArgumentException], () => authenticator.authenticate(exchange))
    assertTrue(authenticator.checked.isEmpty())
  }

  @Test def malformedCredentialsPropagateParsingExceptions(): Unit = {
    val authenticator = new RecordingAuthenticator("users")
    for (header <- Seq("Basic !!!", "Basic    ", "Basic YWxpY2U6c2VjcmV0=",
        "Basic YWxpY2U6c2VjcmV0 ", "Basic O", "Basic YTpi====", "Basic YTo==", "Basic YW=x")) {
      val exchange = new HeaderExchange()
      exchange.getRequestHeaders().set("Authorization", header)
      exchange.getResponseHeaders().set("X-Trace", "preserved")
      assertThrows(header, classOf[IllegalArgumentException], () => authenticator.authenticate(exchange))
      assertEquals(1, exchange.getResponseHeaders().size())
      assertEquals("preserved", exchange.getResponseHeaders().getFirst("X-Trace"))
    }
    for (header <- Seq("Basic YWxpY2U=", "Basic ")) {
      val exchange = new HeaderExchange()
      exchange.getRequestHeaders().set("Authorization", header)
      assertThrows(header, classOf[StringIndexOutOfBoundsException], () => authenticator.authenticate(exchange))
    }
    assertTrue(authenticator.checked.isEmpty())
  }
}
