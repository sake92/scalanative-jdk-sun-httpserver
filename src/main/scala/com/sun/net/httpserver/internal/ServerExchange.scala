package com.sun.net.httpserver.internal

import java.io.{FilterInputStream, IOException, InputStream, OutputStream}
import java.net.{InetSocketAddress, Socket, URI}
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.HashMap
import com.sun.net.httpserver.{Headers, HttpContext, HttpExchange, HttpPrincipal}

private[httpserver] final class ServerExchange(
    private val socket: Socket,
    private val head: RequestHeadParser.RequestHead,
    private val context: ServerContext,
    input: InputStream,
    private val output: OutputStream,
    expectContinue: Boolean
) extends HttpExchange {
  private val requestHeaders = new ReadOnlyHeaders(head.headers)
  private val responseHeaders = new Headers()
  private val attributes = new HashMap[String, Object]()
  private val uri = new URI(head.target)
  private val framedInput: InputStream = head.framing match {
    case RequestHeadParser.NoBody => new FixedLengthInputStream(input, 0)
    case RequestHeadParser.FixedLength(length) => new FixedLengthInputStream(input, length)
    case RequestHeadParser.Chunked => new ChunkedInputStream(input)
  }
  private var pendingContinue = expectContinue
  private var committed = false
  private var closed = false
  private var responseClosed = false
  private var requestClosed = false
  private var responseCode = -1
  private var principal: HttpPrincipal = null
  private var wireBody: OutputStream = null
  private var userInput: InputStream = new FilterInputStream(framedInput) {
    override def read(): Int = { continueIfNeeded(); super.read() }
    override def read(bytes: Array[Byte]): Int = read(bytes, 0, bytes.length)
    override def read(bytes: Array[Byte], offset: Int, length: Int): Int = {
      if (bytes == null) throw new NullPointerException("buffer")
      if (offset < 0 || length < 0 || offset > bytes.length || length > bytes.length - offset)
        throw new IndexOutOfBoundsException()
      if (length > 0) continueIfNeeded()
      super.read(bytes, offset, length)
    }
    override def skip(length: Long): Long = {
      if (length > 0) continueIfNeeded()
      super.skip(length)
    }
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
    responseHeaders.set("Connection", "close")
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
  def setPrincipal(value: HttpPrincipal): Unit = principal = value

  def abort(): Unit = {
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
    if (requestClosed) return
    requestClosed = true
    try userInput.close()
    finally framedInput.close()
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
