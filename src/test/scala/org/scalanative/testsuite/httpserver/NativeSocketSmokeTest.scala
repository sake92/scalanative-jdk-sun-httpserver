package org.scalanative.testsuite.httpserver

import java.net.{InetAddress, InetSocketAddress, ServerSocket}

import org.junit.Assert._
import org.junit.Test

class NativeSocketSmokeTest {
  @Test def bindsLoopbackSocketAndReleasesIt(): Unit = {
    val socket = new ServerSocket()
    try {
      socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
      assertTrue(socket.isBound())
      assertTrue(socket.getLocalPort() > 0)
      assertTrue(socket.getInetAddress().isLoopbackAddress())
    } finally {
      socket.close()
    }
    assertTrue(socket.isClosed())
  }
}
