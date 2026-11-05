package org.scalanative.testsuite.httpserver

/** Native reports specification 1.8; newer host JDK differences stay explicit. */
private[httpserver] object ReferenceRuntime {
  val jdk8: Boolean = System.getProperty("java.specification.version") == "1.8"
}
