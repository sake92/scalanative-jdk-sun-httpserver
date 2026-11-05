package com.sun.net.httpserver.internal

import java.io.{FilterInputStream, IOException, InputStream, OutputStream}
import java.net.{InetSocketAddress, Socket, SocketTimeoutException, URI}
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.HashMap
import com.sun.net.httpserver.{Headers, HttpContext, HttpExchange, HttpPrincipal}

private[httpserver] final class ServerExchange(
    private val socket: Socket,
    private val head: RequestHeadParser.RequestHead,
    private val context: ServerContext,
    input: InputStream,
    private val output: OutputStream,
    expectContinue: Boolean,
    requestPersistent: Boolean,
    private val allowReuse: () => Boolean
) extends HttpExchange {
  private val requestHeaders = new ReadOnlyHeaders(head.headers)
  private val responseHeaders = new Headers()
  private val attributes = new HashMap[String, Object]()
  private val uri = new URI(head.target)
  private var drainRemaining = -1
  private var drainDeadline = 0L
  private var reusable = requestPersistent
  // Count wire bytes, not just decoded payload. Chunk-size lines, extensions
  // and trailers must obey the same drain deadline and budget as payload.
  private val bodySource = new InputStream {
    override def read(): Int = {
      prepareRead(1)
      val next = input.read()
      if (drainRemaining >= 0 && next >= 0) drainRemaining -= 1
      next
    }
    override def read(bytes: Array[Byte], offset: Int, length: Int): Int = {
      if (length == 0) return 0
      val permitted = prepareRead(length)
      // Native BufferedInputStream can fill the entire requested range across
      // multiple blocking refills. During draining, return after available data
      // (or one blocking byte) so the absolute deadline is checked again.
      val maximum = if (drainRemaining >= 0) math.min(permitted, math.max(1, input.available())) else permitted
      val count = input.read(bytes, offset, maximum)
      if (drainRemaining >= 0 && count > 0) drainRemaining -= count
      count
    }
    override def available(): Int = input.available()
  }
  private val framedInput: InputStream = head.framing match {
    case RequestHeadParser.NoBody => new FixedLengthInputStream(bodySource, 0)
    case RequestHeadParser.FixedLength(length) => new FixedLengthInputStream(bodySource, length)
    case RequestHeadParser.Chunked => new ChunkedInputStream(bodySource)
  }
  private var pendingContinue = expectContinue
  private var committed = false
  private var closed = false
  private var responseClosed = false
  private var requestClosed = false
  private var requestCleanupStarted = false
  private var responseCode = -1
  private var principal: HttpPrincipal = null
  private var wireBody: OutputStream = null
  private var userInput: InputStream = new FilterInputStream(framedInput) {
    override def read(): Int = {
      checkRequestOpen()
      continueIfNeeded()
      try super.read()
      catch { case error: IOException => reusable = false; throw error }
    }
    override def read(bytes: Array[Byte]): Int = read(bytes, 0, bytes.length)
    override def read(bytes: Array[Byte], offset: Int, length: Int): Int = {
      if (bytes == null) throw new NullPointerException("buffer")
      if (offset < 0 || length < 0 || offset > bytes.length || length > bytes.length - offset)
        throw new IndexOutOfBoundsException()
      checkRequestOpen()
      if (length > 0) continueIfNeeded()
      try super.read(bytes, offset, length)
      catch { case error: IOException => reusable = false; throw error }
    }
    override def skip(length: Long): Long = {
      checkRequestOpen()
      if (length > 0) continueIfNeeded()
      try super.skip(length)
      catch { case error: IOException => reusable = false; throw error }
    }
    override def close(): Unit = finishRequestBody()
  }
  private val responseStream = new OutputStream {
    override def write(value: Int): Unit = {
      val body = activeBody()
      try body.write(value)
      catch { case error: IOException => abort(); throw error }
    }
    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
      val body = activeBody()
      try body.write(bytes, offset, length)
      catch { case error: IOException => abort(); throw error }
    }
    override def flush(): Unit = {
      val body = activeBody()
      try body.flush()
      catch { case error: IOException => abort(); throw error }
    }
    override def close(): Unit = {
      if (responseClosed) return
      responseClosed = true
      try {
        activeBody().close()
        closeRequest()
        closed = true
        if (!reusable) socket.close()
      } catch { case error: IOException => abort(); throw error }
    }
  }
  private var userOutput: OutputStream = responseStream

  override def getRequestHeaders(): Headers = requestHeaders
  override def getResponseHeaders(): Headers = responseHeaders
  override def getRequestURI(): URI = uri
  override def getRequestMethod(): String = head.method
  override def getHttpContext(): HttpContext = context
  override def getRequestBody(): InputStream = userInput
  override def getResponseBody(): OutputStream = userOutput
  override def getResponseCode(): Int = responseCode
  override def getProtocol(): String = head.version
  override def getPrincipal(): HttpPrincipal = principal
  override def getLocalAddress(): InetSocketAddress = socket.getLocalSocketAddress().asInstanceOf[InetSocketAddress]
  override def getRemoteAddress(): InetSocketAddress = socket.getRemoteSocketAddress().asInstanceOf[InetSocketAddress]

  override def getAttribute(name: String): Object = {
    if (name == null) throw new NullPointerException("name")
    if (attributes.containsKey(name)) attributes.get(name) else context.getAttributes().get(name)
  }

  override def setAttribute(name: String, value: Object): Unit = {
    if (name == null) throw new NullPointerException("name")
    attributes.put(name, value)
  }

  override def setStreams(input: InputStream, output: OutputStream): Unit = {
    if (input != null) userInput = input
    if (output != null) userOutput = output
  }

  override def sendResponseHeaders(code: Int, length: Long): Unit = {
    if (closed || committed) throw new IOException("Response already committed or closed")
    require(code >= 200 && code <= 599, "A final response status is required")
    committed = true
    responseCode = code
    val closeDelimited = ResponseHeadWriter.usesCloseDelimited(head.method, head.version, code, length)
    if (!allowReuse() || closeDelimited || ConnectionPolicy.responseCloses(responseHeaders) ||
        (pendingContinue && !requestComplete)) reusable = false
    if (!reusable) responseHeaders.set("Connection", "close")
    else if (head.version == "HTTP/1.0") responseHeaders.set("Connection", "keep-alive")
    try {
      val framing = ResponseHeadWriter.write(output, head.method, head.version, code, length, responseHeaders)
      wireBody = framing match {
        case ResponseHeadWriter.FixedLength(size) => new FixedLengthOutputStream(output, size)
        case ResponseHeadWriter.Chunked => new ChunkedOutputStream(output)
        case ResponseHeadWriter.NoBody => new FixedLengthOutputStream(output, 0)
        case ResponseHeadWriter.CloseDelimited => new OutputStream {
          override def write(value: Int): Unit = output.write(value)
          override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = output.write(bytes, offset, length)
          override def flush(): Unit = output.flush()
          override def close(): Unit = output.flush()
        }
      }
    } catch {
      case error: IOException => abort(); throw error
      case error: IllegalArgumentException => abort(); throw error
    }
  }

  override def close(): Unit = {
    if (closed) return
    try {
      if (committed) {
        userOutput.close()
        responseStream.close()
      } else abort()
    } catch { case _: IOException => abort() }
    finally {
      try closeRequest()
      catch { case _: IOException => abort() }
      closed = true
    }
  }

  def hasResponse: Boolean = committed
  def isClosed: Boolean = closed
  def canReuse: Boolean = reusable && closed && responseClosed && requestComplete && !socket.isClosed()
  def setPrincipal(value: HttpPrincipal): Unit = principal = value

  def abort(): Unit = {
    reusable = false
    closed = true
    try socket.close()
    catch { case _: IOException => () }
  }

  private def activeBody(): OutputStream = {
    if (closed) throw new IOException("Exchange is closed")
    if (wireBody == null) throw new IOException("Response headers have not been sent")
    wireBody
  }

  private def closeRequest(): Unit = {
    if (requestCleanupStarted || requestClosed) return
    requestCleanupStarted = true
    try userInput.close()
    finally finishRequestBody()
  }

  private def finishRequestBody(): Unit = {
    if (requestClosed) return
    requestClosed = true
    try {
      if (!allowReuse() || (pendingContinue && !requestComplete)) reusable = false
      if (reusable && !requestComplete) drainRequest()
      if (!requestComplete) reusable = false
    } finally framedInput.close()
  }

  private def requestComplete: Boolean = framedInput match {
    case fixed: FixedLengthInputStream => fixed.isComplete
    case chunked: ChunkedInputStream => chunked.isComplete
    case _ => false
  }

  private def drainRequest(): Unit = {
    val previousTimeout = socket.getSoTimeout()
    drainRemaining = ConnectionPolicy.drainBytes
    drainDeadline = System.nanoTime() + ConnectionPolicy.drainNanos
    try {
      val buffer = new Array[Byte](4096)
      while (!requestComplete) {
        val count = framedInput.read(buffer)
        if (count == 0 || (count == -1 && !requestComplete))
          throw new IOException("Incomplete request body while draining")
      }
    } catch { case _: IOException => reusable = false }
    finally {
      drainRemaining = -1
      try socket.setSoTimeout(previousTimeout)
      catch { case _: IOException => reusable = false }
    }
  }

  private def prepareRead(length: Int): Int = {
    if (drainRemaining < 0) return length
    if (drainRemaining == 0) throw new IOException("Unread request body exceeds drain limit")
    val remaining = drainDeadline - System.nanoTime()
    if (remaining <= 0) throw new SocketTimeoutException("Unread body drain deadline exceeded")
    socket.setSoTimeout(math.max(1L, (remaining + 999999L) / 1000000L).toInt)
    math.min(length, drainRemaining)
  }

  private def checkRequestOpen(): Unit = {
    if (requestClosed) throw new IOException("Request body stream is closed")
  }

  private def continueIfNeeded(): Unit = {
    if (pendingContinue && !committed && !closed) {
      pendingContinue = false
      try {
        output.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(US_ASCII))
        output.flush()
      } catch { case error: IOException => abort(); throw error }
    }
  }
}
