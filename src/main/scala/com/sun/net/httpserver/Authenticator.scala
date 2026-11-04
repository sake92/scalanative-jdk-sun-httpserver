package com.sun.net.httpserver

/**
 * An implementation of an HTTP authentication mechanism.
 *
 * The result classes are deliberately concrete values so an authenticator can
 * report success, a completed failure, or a response that should be retried.
 */
abstract class Authenticator {
  def authenticate(exchange: HttpExchange): Authenticator.Result
}

object Authenticator {
  /** Base class for values returned by [[Authenticator.authenticate]]. */
  abstract class Result

  /** Indicates that authentication succeeded for `principal`. */
  class Success(principal: HttpPrincipal) extends Result {
    def getPrincipal(): HttpPrincipal = principal
  }

  /** Indicates that authentication failed with `responseCode`. */
  class Failure(responseCode: Int) extends Result {
    def getResponseCode(): Int = responseCode
  }

  /** Indicates that the client must retry with `responseCode`. */
  class Retry(responseCode: Int) extends Result {
    def getResponseCode(): Int = responseCode
  }
}
