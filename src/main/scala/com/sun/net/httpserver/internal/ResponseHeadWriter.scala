package com.sun.net.httpserver.internal

import java.io.OutputStream
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.ArrayList

import com.sun.net.httpserver.Headers

private[httpserver] object ResponseHeadWriter {
  sealed trait Framing
  final case class FixedLength(length: Long) extends Framing
  case object Chunked extends Framing
  case object NoBody extends Framing
  case object CloseDelimited extends Framing

  /** Validates before emitting anything, normalizes the response Headers, then
    * writes the head without flushing or closing output. Any output failure
    * requires aborting the connection; this operation must never be retried.
    */
  def write(
      output: OutputStream,
      method: String,
      version: String,
      status: Int,
      responseLength: Long,
      headers: Headers
  ): Framing = {
    if (output == null || headers == null) throw new NullPointerException()
    require(method != null && method.nonEmpty && method.forall(HttpSyntax.isToken), "Invalid method")
    require(version == "HTTP/1.0" || version == "HTTP/1.1", "Unsupported HTTP version")
    require(status >= 100 && status <= 599, "Invalid response status")
    require(status != 101, "Protocol upgrades are not supported")
    require(method != "CONNECT" || status < 200 || status >= 300, "Tunnels are not supported")

    val snapshot = copyValidated(headers)
    val framing = selectFraming(method, version, status, responseLength, snapshot)
    if (snapshot.getFirst("Date") == null) {
      snapshot.set("Date", httpDate(System.currentTimeMillis()))
    }
    val head = new java.lang.StringBuilder()
    head.append(version).append(' ').append(status).append(' ').append(reason(status)).append("\r\n")
    val fields = snapshot.entrySet().iterator()
    while (fields.hasNext()) {
      val field = fields.next()
      val values = field.getValue().iterator()
      while (values.hasNext()) {
        head.append(field.getKey()).append(": ").append(values.next()).append("\r\n")
      }
    }
    head.append("\r\n")
    val bytes = head.toString().getBytes(ISO_8859_1)
    headers.clear()
    headers.putAll(snapshot)
    output.write(bytes)
    framing
  }

  def usesCloseDelimited(method: String, version: String, status: Int, length: Long): Boolean =
    version == "HTTP/1.0" && length == 0 && !forbidsLength(status) &&
      status != 205 && !metadataOnly(method, status)

  private def copyValidated(headers: Headers): Headers = {
    val snapshot = new Headers()
    val fields = headers.entrySet().iterator()
    while (fields.hasNext()) {
      val field = fields.next()
      val name = field.getKey()
      require(name != null && name.nonEmpty && name.forall(HttpSyntax.isToken), "Invalid header name")
      val values = field.getValue()
      require(values != null, "Null header values")
      val iterator = values.iterator()
      while (iterator.hasNext()) {
        require(HttpSyntax.isFieldValue(iterator.next()), "Invalid header value")
      }
      snapshot.put(name, new ArrayList[String](values))
    }
    snapshot
  }

  private def selectFraming(
      method: String,
      version: String,
      status: Int,
      length: Long,
      headers: Headers
  ): Framing = {
    headers.remove("Transfer-Encoding")
    if (forbidsLength(status)) {
      headers.remove("Content-Length")
      NoBody
    } else if (status == 205) {
      headers.set("Content-Length", "0")
      NoBody
    } else if (metadataOnly(method, status)) {
      if (length > 0) headers.set("Content-Length", length.toString)
      else normalizeRepresentationLength(headers)
      NoBody
    } else if (length < 0) {
      headers.set("Content-Length", "0")
      NoBody
    } else if (length > 0) {
      headers.set("Content-Length", length.toString)
      FixedLength(length)
    } else {
      headers.remove("Content-Length")
      if (usesCloseDelimited(method, version, status, length)) {
        headers.set("Connection", "close")
        CloseDelimited
      } else {
        headers.set("Transfer-Encoding", "chunked")
        Chunked
      }
    }
  }

  private def forbidsLength(status: Int): Boolean = status < 200 || status == 204

  private def metadataOnly(method: String, status: Int): Boolean = method == "HEAD" || status == 304

  private def normalizeRepresentationLength(headers: Headers): Unit = {
    val values = headers.get("Content-Length")
    if (values != null) {
      require(values.size() == 1, "Repeated or empty representation Content-Length")
      val value = HttpSyntax.trimOws(values.get(0))
      require(value.nonEmpty && value.forall(c => c >= '0' && c <= '9'), "Invalid Content-Length")
      val length = try java.lang.Long.parseLong(value)
      catch { case _: NumberFormatException => throw new IllegalArgumentException("Content-Length overflow") }
      headers.set("Content-Length", length.toString)
    }
  }

  // Native 0.5.12 has no java.text date formatter. Bound the conversion to the
  // four-digit HTTP year range; this calendar walk is at most 8030 years.
  private[internal] def httpDate(milliseconds: Long): String = {
    require(milliseconds >= -62135596800000L && milliseconds <= 253402300799999L, "HTTP date year out of range")
    val seconds = Math.floorDiv(milliseconds, 1000L)
    val epochDay = Math.floorDiv(seconds, 86400L)
    var days = epochDay
    var year = 1970
    while (days < 0) {
      year -= 1
      days += daysInYear(year)
    }
    while (days >= daysInYear(year)) {
      days -= daysInYear(year)
      year += 1
    }
    val monthLengths = Array(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    if (isLeapYear(year)) monthLengths(1) = 29
    var month = 0
    while (days >= monthLengths(month)) {
      days -= monthLengths(month)
      month += 1
    }
    val weekdays = Array("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
    val months = Array("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val weekday = weekdays(Math.floorMod(epochDay, 7L).toInt)
    val monthName = months(month)
    val day = twoDigits(days.toInt + 1)
    val rawYear = year.toString
    val yearText = "0000".substring(rawYear.length) + rawYear
    val time = Math.floorMod(seconds, 86400L)
    val hour = twoDigits((time / 3600).toInt)
    val minute = twoDigits((time / 60 % 60).toInt)
    val second = twoDigits((time % 60).toInt)
    s"$weekday, $day $monthName $yearText $hour:$minute:$second GMT"
  }

  private def isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

  private def daysInYear(year: Int): Int = if (isLeapYear(year)) 366 else 365

  private def twoDigits(value: Int): String = if (value < 10) "0" + value else value.toString

  private def reason(status: Int): String = status match {
    case 100 => "Continue"
    case 200 => "OK"
    case 201 => "Created"
    case 202 => "Accepted"
    case 204 => "No Content"
    case 205 => "Reset Content"
    case 206 => "Partial Content"
    case 301 => "Moved Permanently"
    case 302 => "Found"
    case 303 => "See Other"
    case 304 => "Not Modified"
    case 307 => "Temporary Redirect"
    case 308 => "Permanent Redirect"
    case 400 => "Bad Request"
    case 401 => "Unauthorized"
    case 403 => "Forbidden"
    case 404 => "Not Found"
    case 405 => "Method Not Allowed"
    case 408 => "Request Timeout"
    case 411 => "Length Required"
    case 413 => "Content Too Large"
    case 414 => "URI Too Long"
    case 417 => "Expectation Failed"
    case 431 => "Request Header Fields Too Large"
    case 500 => "Internal Server Error"
    case 501 => "Not Implemented"
    case 503 => "Service Unavailable"
    case 505 => "HTTP Version Not Supported"
    case _ => ""
  }
}
