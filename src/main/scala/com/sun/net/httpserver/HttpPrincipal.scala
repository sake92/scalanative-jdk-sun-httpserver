package com.sun.net.httpserver

import java.security.Principal

/**
 * Represents a user authenticated by HTTP Basic or Digest authentication.
 *
 * @param username the name of the user within the realm
 * @param realm the authentication realm
 */
class HttpPrincipal(private val username: String, private val realm: String) extends Principal {
  if (username == null) throw new NullPointerException("username")
  if (realm == null) throw new NullPointerException("realm")

  override def getName(): String = username

  def getUsername(): String = username

  def getRealm(): String = realm

  override def equals(other: Any): Boolean = other match {
    case principal: HttpPrincipal =>
      username == principal.username && realm == principal.realm
    case _ => false
  }

  override def hashCode(): Int = (username + realm).hashCode()

  override def toString(): String = getName()
}
