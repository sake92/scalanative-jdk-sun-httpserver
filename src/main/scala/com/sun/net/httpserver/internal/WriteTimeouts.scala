package com.sun.net.httpserver.internal

import java.io.{IOException, OutputStream}
import java.net.{Socket, SocketTimeoutException}
import java.util.HashSet

/** One server-owned watcher for pending socket writes, never for handler think time. */
private[httpserver] final class WriteTimeouts(timeoutMillis: Int) {
  private final class Pending(val socket: Socket, val deadline: Long)

  private val lock = new Object()
  private val pending = new HashSet[Pending]()
  private var stopped = false

  def start(): Thread = {
    if (timeoutMillis == 0) return null
    val thread = new Thread(new Runnable { override def run(): Unit = watch() }, "httpserver-write-timeouts")
    thread.setDaemon(false)
    thread.start()
    thread
  }

  def stop(): Unit = lock.synchronized {
    stopped = true
    lock.notifyAll()
  }

  def output(socket: Socket): OutputStream = {
    val raw = socket.getOutputStream()
    if (timeoutMillis == 0) return raw
    new OutputStream {
      override def write(value: Int): Unit = timed(socket) { raw.write(value) }
      override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = timed(socket) {
        raw.write(bytes, offset, length)
      }
      override def flush(): Unit = timed(socket) { raw.flush() }
    }
  }

  private def timed(socket: Socket)(operation: => Unit): Unit = {
    val write = new Pending(socket, System.nanoTime() + timeoutMillis.toLong * 1000000L)
    lock.synchronized {
      if (stopped) throw new IOException("HTTP server write watcher is stopped")
      pending.add(write)
      lock.notifyAll()
    }
    try operation
    finally {
      val expired = lock.synchronized {
        pending.remove(write)
        lock.notifyAll()
        write.deadline - System.nanoTime() <= 0
      }
      if (expired) {
        closeSocket(socket)
        throw new SocketTimeoutException("Socket write deadline exceeded")
      }
    }
  }

  private def watch(): Unit = {
    try {
      lock.synchronized {
        while (!stopped) {
          val now = System.nanoTime()
          var remaining = Long.MaxValue
          val iterator = pending.iterator()
          while (iterator.hasNext()) {
            val write = iterator.next()
            val budget = write.deadline - now
            if (budget <= 0) {
              iterator.remove()
              closeSocket(write.socket)
            } else remaining = math.min(remaining, budget)
          }
          if (pending.isEmpty) lock.wait()
          else lock.wait(math.max(1L, (remaining + 999999L) / 1000000L))
        }
      }
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt()
    } finally {
      lock.synchronized {
        stopped = true
        val iterator = pending.iterator()
        while (iterator.hasNext()) closeSocket(iterator.next().socket)
        pending.clear()
      }
    }
  }

  private def closeSocket(socket: Socket): Unit = try socket.close() catch { case _: IOException => () }
}
