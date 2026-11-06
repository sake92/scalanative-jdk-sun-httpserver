package com.sun.net.httpserver.internal

import java.io.{BufferedInputStream, BufferedOutputStream, EOFException, IOException, InputStream}
import java.net.{BindException, InetSocketAddress, ProtocolException, ServerSocket, Socket, SocketTimeoutException, URI}
import java.util.{ArrayList, HashMap, HashSet}
import java.util.concurrent.Executor
import scala.util.control.NonFatal
import com.sun.net.httpserver.{Headers, HttpContext, HttpHandler, HttpServer}

/** Blocking server with sequential exchanges on each persistent connection. */
private[httpserver] final class BlockingHttpServer extends HttpServer {
  private val headerTimeoutMillis = ConnectionPolicy.headerTimeoutMillis()
  private val bodyReadTimeoutMillis = ConnectionPolicy.bodyReadTimeoutMillis()
  private val writeTimeouts = new WriteTimeouts(ConnectionPolicy.writeTimeoutMillis())
  private val maxConnections = ConnectionPolicy.maxConnections()
  private val maxPendingConnections = ConnectionPolicy.maxPendingConnections()
  private val maxRequestBodyBytes = ConnectionPolicy.maxRequestBodyBytes()
  private val lifecycle = new Object()
  private val contexts = new ArrayList[ServerContext]()
  private val connections = new HashSet[Socket]()
  private val pendingConnections = new HashSet[Socket]()
  private val active = new HashMap[Socket, Thread]()
  private var listener: ServerSocket = null
  private var address: InetSocketAddress = null
  private var acceptThread: Thread = null
  private var writeTimeoutThread: Thread = null
  private var executor: Executor = null
  private var started = false
  private var stopped = false

  override def bind(value: InetSocketAddress, backlog: Int): Unit = lifecycle.synchronized {
    if (value == null) throw new NullPointerException("address")
    if (stopped) throw new IllegalStateException("Server is stopped")
    if (listener != null) throw new BindException("Server is already bound")
    val socket = new ServerSocket()
    try {
      socket.bind(value, backlog)
      socket.setSoTimeout(250)
      address = socket.getLocalSocketAddress().asInstanceOf[InetSocketAddress]
      listener = socket
    } finally {
      if (listener == null) socket.close()
    }
  }

  override def getAddress(): InetSocketAddress = lifecycle.synchronized { address }
  override def getExecutor(): Executor = lifecycle.synchronized { executor }

  override def setExecutor(value: Executor): Unit = lifecycle.synchronized {
    if (stopped) throw new IllegalStateException("Cannot set executor: server is stopped")
    if (started) throw new IllegalStateException("Cannot set executor: server is already started")
    executor = value
  }

  override def start(): Unit = lifecycle.synchronized {
    if (stopped) throw new IllegalStateException("Cannot start server: server is stopped")
    if (started) throw new IllegalStateException("Cannot start server: server is already started")
    if (listener == null) throw new IllegalStateException("Cannot start server: server is not bound")
    if (executor == null) executor = new Executor { override def execute(task: Runnable): Unit = task.run() }
    acceptThread = new Thread(new Runnable { override def run(): Unit = acceptLoop() }, "httpserver-accept")
    acceptThread.setDaemon(false)
    started = true
    try {
      writeTimeoutThread = writeTimeouts.start()
      acceptThread.start()
    }
    catch {
      case NonFatal(error) =>
        stop(0)
        throw error
    }
  }

  override def createContext(path: String, handler: HttpHandler): HttpContext = {
    if (handler == null) throw new NullPointerException("handler")
    addContext(path, handler)
  }

  override def createContext(path: String): HttpContext = addContext(path, null)

  override def removeContext(path: String): Unit = lifecycle.synchronized {
    if (path == null) throw new NullPointerException("path")
    val iterator = contexts.iterator()
    while (iterator.hasNext()) {
      if (iterator.next().getPath() == path) { iterator.remove(); return }
    }
    throw new IllegalArgumentException(s"No context is registered for path: $path")
  }

  override def removeContext(context: HttpContext): Unit = lifecycle.synchronized {
    if (context == null) throw new NullPointerException("context")
    if (!contexts.remove(context)) throw new IllegalArgumentException("Context is not registered with this server")
  }

  override def stop(delay: Int): Unit = {
    require(delay >= 0, "Negative stop delay")
    var interrupted = Thread.interrupted()
    val self = Thread.currentThread()
    val thread = lifecycle.synchronized {
      if (stopped) {
        if (interrupted) self.interrupt()
        return
      }
      stopped = true
      closeListener()
      val iterator = connections.iterator()
      while (iterator.hasNext()) {
        val socket = iterator.next()
        if (!active.containsKey(socket)) closeSocket(socket)
      }
      acceptThread
    }
    val deadline = System.nanoTime() + delay.toLong * 1000000000L
    lifecycle.synchronized {
      while (!interrupted && hasOtherHandlers(self) && deadline - System.nanoTime() > 0) {
        val remaining = deadline - System.nanoTime()
        try lifecycle.wait(math.max(1L, remaining / 1000000L))
        catch { case _: InterruptedException => interrupted = true }
      }
      val iterator = connections.iterator()
      while (iterator.hasNext()) closeSocket(iterator.next())
    }
    writeTimeouts.stop()
    val cleanupDeadline = System.nanoTime() + 500000000L
    val ownedThreads = List(thread, writeTimeoutThread).filter(owned => owned != null && (owned ne self))
    ownedThreads.foreach { owned =>
      owned.interrupt()
      while (owned.isAlive() && cleanupDeadline - System.nanoTime() > 0) {
        val remaining = cleanupDeadline - System.nanoTime()
        try owned.join(math.max(1L, remaining / 1000000L))
        catch { case _: InterruptedException => interrupted = true }
      }
      if (owned.isAlive()) System.err.println("HTTP server owned thread exceeded shutdown cleanup budget")
    }
    if (interrupted) self.interrupt()
  }

  private def addContext(path: String, handler: HttpHandler): HttpContext = lifecycle.synchronized {
    if (path == null) throw new NullPointerException("path")
    require(path.startsWith("/"), "Context path must start with /")
    if (stopped) throw new IllegalStateException("Server is stopped")
    val context = new ServerContext(this, path, handler)
    contexts.add(context)
    context
  }

  private def isStopped: Boolean = lifecycle.synchronized { stopped }

  private def acceptLoop(): Unit = {
    try {
      while (!isStopped && !Thread.currentThread().isInterrupted()) {
        try {
          val socket = listener.accept()
          val dispatch = lifecycle.synchronized {
            if (stopped ||
                (maxConnections > 0 && connections.size() >= maxConnections) ||
                (maxPendingConnections > 0 && pendingConnections.size() >= maxPendingConnections)) null
            else {
              connections.add(socket)
              pendingConnections.add(socket)
              executor
            }
          }
          if (dispatch == null) closeSocket(socket)
          else {
            try dispatch.execute(new Runnable { override def run(): Unit = process(socket) })
            catch { case NonFatal(_) => release(socket) }
          }
        } catch {
          case _: SocketTimeoutException => ()
          case error: IOException => if (!isStopped) throw error
        }
      }
    } finally {
      if (!isStopped) stop(0)
    }
  }

  private def process(socket: Socket): Unit = {
    try {
      val admitted = lifecycle.synchronized {
        pendingConnections.remove(socket)
        !stopped && !socket.isClosed()
      }
      if (!admitted) return
      val input = new BufferedInputStream(socket.getInputStream())
      val output = new BufferedOutputStream(writeTimeouts.output(socket))
      while (!isStopped && !socket.isClosed() && !Thread.currentThread().isInterrupted()) {
        if (!processExchange(socket, input, output)) return
      }
    } catch { case _: IOException => () }
    finally release(socket)
  }

  private def processExchange(socket: Socket, input: BufferedInputStream, output: BufferedOutputStream): Boolean = {
    var exchange: ServerExchange = null
    try {
      val head = try readHead(socket, input)
      catch {
        case _: SocketTimeoutException => return false
        case error: RequestHeadParser.ParseException => errorResponse(output, error.responseCode); return false
      }
      if (head.isEmpty) return false
      socket.setSoTimeout(bodyReadTimeoutMillis)
      val request = head.get
      if (request.method == "CONNECT") { errorResponse(output, 501); return false }
      val persistent = try ConnectionPolicy.requestPersistent(request)
      catch { case _: ProtocolException => errorResponse(output, 400); return false }
      val expectations = request.headers.collect {
        case (name, value) if name.equalsIgnoreCase("Expect") => value
      }.flatMap(_.split(",", -1)).map(HttpSyntax.trimOws)
      if (expectations.nonEmpty && (request.version != "HTTP/1.1" ||
          !expectations.forall(_.equalsIgnoreCase("100-continue")))) {
        errorResponse(output, 417)
        return false
      }
      request.framing match {
        case RequestHeadParser.FixedLength(length) if maxRequestBodyBytes > 0 && length > maxRequestBodyBytes =>
          errorResponse(output, 413)
          return false
        case _ => ()
      }
      if (request.target == "*") {
        val headers = new Headers()
        headers.set("Connection", "close")
        ResponseHeadWriter.write(output, request.method, request.version, 200, -1, headers)
        output.flush()
        return false
      }
      val path = if (request.target.startsWith("/")) request.target.takeWhile(_ != '?')
      else Option(new URI(request.target).getRawPath()).getOrElse("")
      val context = lifecycle.synchronized {
        var selected: ServerContext = null
        val iterator = contexts.iterator()
        while (iterator.hasNext()) {
          val candidate = iterator.next()
          if (path.startsWith(candidate.getPath()) &&
              (selected == null || candidate.getPath().length > selected.getPath().length)) selected = candidate
        }
        selected
      }
      if (context == null) { errorResponse(output, 404); return false }
      val hasBody = request.framing match {
        case RequestHeadParser.NoBody => false
        case RequestHeadParser.FixedLength(length) => length > 0
        case RequestHeadParser.Chunked => true
      }
      exchange = new ServerExchange(socket, request, context, input, output,
        expectations.nonEmpty && hasBody, persistent, () => !isStopped, maxRequestBodyBytes)
      val admitted = lifecycle.synchronized {
        if (stopped) false
        else { active.put(socket, Thread.currentThread()); true }
      }
      if (!admitted) return false
      context.handle(exchange)
      if (!exchange.hasResponse && !exchange.isClosed) exchange.sendResponseHeaders(500, -1)
      exchange.close()
      exchange.canReuse
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); false
      case _: ProtocolException => failExchange(exchange, 400); false
      case _: EOFException => failExchange(exchange, 400); false
      case NonFatal(_) => failExchange(exchange, 500); false
    } finally {
      lifecycle.synchronized {
        active.remove(socket)
        lifecycle.notifyAll()
      }
    }
  }

  private def readHead(socket: Socket, input: BufferedInputStream): Option[RequestHeadParser.RequestHead] = {
    val deadline = System.nanoTime() + headerTimeoutMillis.toLong * 1000000L
    val bounded = new InputStream {
      private var lastTimeout = -1

      override def read(): Int = {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) throw new SocketTimeoutException("Request head deadline exceeded")
        val timeout = math.max(1L, (remaining + 999999L) / 1000000L).toInt
        if (timeout != lastTimeout) {
          socket.setSoTimeout(timeout)
          lastTimeout = timeout
        }
        input.read()
      }
    }
    val head = RequestHeadParser.read(bounded)
    if (deadline - System.nanoTime() <= 0) throw new SocketTimeoutException("Request head deadline exceeded")
    head
  }

  private def failExchange(exchange: ServerExchange, code: Int): Unit = {
    if (exchange != null && !exchange.hasResponse && !exchange.isClosed) {
      try {
        exchange.getResponseHeaders().set("Connection", "close")
        exchange.sendResponseHeaders(code, -1)
        exchange.close()
      }
      catch { case NonFatal(_) => exchange.abort() }
    } else if (exchange != null) exchange.abort()
  }

  private def errorResponse(output: BufferedOutputStream, code: Int): Unit = {
    val headers = new Headers()
    headers.set("Connection", "close")
    ResponseHeadWriter.write(output, "GET", "HTTP/1.1", code, -1, headers)
    output.flush()
  }

  private def release(socket: Socket): Unit = {
    closeSocket(socket)
    lifecycle.synchronized {
      active.remove(socket)
      pendingConnections.remove(socket)
      connections.remove(socket)
      lifecycle.notifyAll()
    }
  }

  private def hasOtherHandlers(self: Thread): Boolean = {
    val iterator = active.values().iterator()
    while (iterator.hasNext()) if (iterator.next() ne self) return true
    false
  }

  private def closeSocket(socket: Socket): Unit = try socket.close() catch { case _: IOException => () }

  private def closeListener(): Unit = if (listener != null) {
    try listener.close() catch { case _: IOException => () }
  }
}
