package com.sun.net.httpserver

import java.nio.charset.Charset
import java.util.Base64

/**
 * HTTP Basic authentication using the platform default character set.
 *
 * Subclasses verify credentials without caching them here. The protected,
 * mutable realm and the single constructor preserve the JDK 8 API surface.
 */
abstract class BasicAuthenticator(protected var realm: String) extends Authenticator {
  def getRealm(): String = realm

  override def authenticate(exchange: HttpExchange): Authenticator.Result = {
    val authorization = exchange.getRequestHeaders().getFirst("Authorization")
    if (authorization == null) {
      setChallenge(exchange)
      new Authenticator.Retry(401)
    } else {
      val separator = authorization.indexOf(' ')
      if (separator < 0 || authorization.substring(0, separator) != "Basic") {
        return new Authenticator.Failure(401)
      }

      val token = authorization.substring(separator + 1)
      // Native's decoder tolerates surplus padding, so check its shape first.
      val paddingStart = token.indexOf('=')
      val paddingLength = if (paddingStart < 0) 0 else token.length - paddingStart
      if (paddingLength > 2 || (paddingLength > 0 && token.length % 4 != 0)) {
        throw new IllegalArgumentException("Invalid Base64 padding")
      }
      val bytes = Base64.getDecoder().decode(token)
      val credentials = new String(bytes, Charset.defaultCharset())
      val colon = credentials.indexOf(':')
      val username = credentials.substring(0, colon)
      val password = credentials.substring(colon + 1)
      if (checkCredentials(username, password)) {
        new Authenticator.Success(new HttpPrincipal(username, realm))
      } else {
        setChallenge(exchange)
        new Authenticator.Failure(401)
      }
    }
  }

  def checkCredentials(username: String, password: String): Boolean

  private def setChallenge(exchange: HttpExchange): Unit =
    exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"" + realm + "\"")
}
