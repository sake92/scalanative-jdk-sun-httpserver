# Compatibility

Target: [JDK 8 HTTP API](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html),
with measured Temurin 8u482 runtime behavior used to clarify Java API contracts.
HTTP wire behavior follows [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110) and
[RFC 9112](https://www.rfc-editor.org/rfc/rfc9112). Clear protocol bugs, especially
security defects, are fixed rather than copied from the JDK; intentional
differences are documented and tested. Where the RFC permits alternatives, a
test suite's preferred result does not by itself require a change.
This is this repository's policy, not a claim about Scala Native upstream policy.
Full package and Java binary compatibility are unverified.

## API and runtime behavior

Implemented: `HttpServer`, `HttpContext`, `HttpExchange`, `HttpHandler`, `Headers`,
`HttpPrincipal`, `Authenticator` and its results, `BasicAuthenticator`, `Filter`
and `Filter.Chain`. Factories directly create the built-in blocking server.
HTTPS, provider discovery, HTTP/2, upgrades/WebSockets, CONNECT tunnels and newer
JDK convenience APIs are outside the current scope.

| Area | Native / Temurin 8u482 behavior |
| --- | --- |
| Principal | Name/string form is the username; equality/hash include the realm |
| Header insertion | Direct writes normalize names; `putAll` preserves raw spelling, so noncanonical keys can be unreachable through normalized lookup |
| Header validation | Direct writes reject null names/lists/elements and malformed line breaks; folded CRLF+space/tab values are accepted in the map but rejected by response serialization |
| Header reads | Null lookup/removal returns null; `containsKey(null)` is false; non-string get/remove throws CCE |
| Basic authentication | Null/empty realm accepted; default charset; exact `Basic` scheme and one separator; challenge is `Basic realm="users"` |
| Malformed credentials | Invalid Base64 throws IAE; missing decoded colon throws StringIndexOutOfBoundsException |
| Filters | Null exchanges pass through and advance the chain normally |
| Duplicate contexts | Separate registrations; first matching registration wins; identity removal preserves the others |

JDK 21 differences in these areas are asserted explicitly by the shared tests.

## Protocol and security limits

| Area | Behavior / limit |
| --- | --- |
| Requests | HTTP/1.0 and HTTP/1.1; rejects duplicate Content-Length, conflicting framing and malformed Connection options; only single `chunked` transfer coding on HTTP/1.1 |
| Parsing bounds | Request/header/chunk lines: 8 KiB; header/trailer sections and cumulative chunk extensions: 64 KiB; header/trailer counts: 100; trailers discarded |
| Routing | Longest case-sensitive raw-path prefix; percent encoding preserved; URI/Host spellings must be accepted by `java.net.URI` |
| Server-wide OPTIONS | `OPTIONS *` returns an empty 200 and closes the connection without invoking a resource handler; measured JDK 8/21 behavior returns 404 |
| Oversized method | A method that fills the request-line limit receives 501; oversized targets receive 414 |
| Empty Host | Accepted using the bound server as the connection-based default authority; no Host-based virtual-server selection |
| Responses | Final status 200–599; automatic lazy `100 Continue`; CONNECT receives 501; 101/upgrades rejected |
| Body framing | Positive length is exact; zero is chunked on HTTP/1.1 or connection-delimited on HTTP/1.0; negative length is bodyless; HEAD/bodyless status rules override framing |
| Persistence | HTTP/1.1 defaults to keep-alive; HTTP/1.0 requires opt-in; pipelined responses retain order |
| Unread bodies | Reuse requires successful drain within 64 KiB of wire bytes and a one-second total deadline; otherwise connection closes |
| Timeouts | Every request head has a 30-second absolute deadline by default, including request line, headers and idle wait; expiry closes without a response. Body socket reads and socket writes default to 30 seconds per operation; expiry closes the connection and fails I/O |
| Capacity | Configurable admission caps default to 256 connection tasks and 64 pending tasks; overload closes the new socket. Upload size is not limited; applications own worker configuration |
| Execution | Default executor uses the accept thread; idle/slow connections occupy it. Concurrent dispatch requires a caller-owned executor, which the server never shuts down or interrupts |
| Shutdown | Terminal; closes idle sockets, gives active handlers `delay` seconds, closes remaining sockets, then joins owned accept/watchdog threads within a shared 500 ms budget. Uncooperative handlers may outlive cleanup |
| Exchanges | Attributes are local overrides over context defaults; filters are snapshotted; handler return triggers cleanup; missing response produces 500 |

### Timeout configuration

Each server snapshots these system properties at construction through
`HttpServer.create()`. Values are integer milliseconds; invalid values cause
`IllegalArgumentException`.

| Property under `org.scalanative.httpserver.` | Default | Valid range | Scope |
| --- | --- | --- | --- |
| `headerTimeoutMillis` | 30000 | 1–2147483647 | Absolute deadline for each complete request head |
| `bodyReadTimeoutMillis` | 30000 | 0–2147483647 | Each blocking body socket read; 0 disables |
| `writeTimeoutMillis` | 30000 | 0–2147483647 | Each socket output call; 0 disables |

The deadline starts when a worker begins reading each head, including subsequent
heads on persistent connections, and is not renewed by incoming bytes. Executor
queue time is excluded. This deliberately bounds header reads that previously
could continue indefinitely while a client dripped bytes. There is no minimum
ingress rate; legitimate clients must finish each head within the configured
budget. Request bodies and response streaming are outside this header deadline.

Body timeouts apply to socket reads, including chunk framing and trailers.
Successful socket reads renew that allowance; one application read or skip may
span multiple socket reads. A write deadline covers each underlying socket output
call, including output from response headers, interim/error responses and buffer
flushes. Progress within a single output call does not renew its deadline. These
limits exclude time between handler I/O calls and impose no total exchange or
streaming duration limit. Buffered output is not evidence of delivery to the peer.
Expiry closes the socket, prevents reuse and surfaces an `IOException` to a
blocked handler; exchange cleanup may consume the error. Caller-owned threads
are not interrupted. Slow progress and handlers that stop doing I/O can still
hold workers indefinitely; admission limits and handler cancellation are separate.

### Admission limits

Each server snapshots these system properties at construction. Values must be
integers from 0 to 2147483647; 0 disables the corresponding limit. Invalid values
cause `IllegalArgumentException`.

| Property under `org.scalanative.httpserver.` | Default | Counted work |
| --- | --- | --- |
| `maxConnections` | 256 | All admitted connection tasks: queued, reading, handling and idle |
| `maxPendingConnections` | 64 | Admitted executor tasks that have not started |

Admission reserves both slots before calling the executor. A task releases its
pending slot when it starts and its total slot when it finishes. Executor
rejection releases both slots. A persistent connection keeps its total slot
between requests; a handler that continues after socket closure retains its slot
until its connection task returns.

At either limit, the server closes a newly accepted socket without parsing it,
writing an HTTP response or invoking a handler. The OS listen backlog and other
users of a shared executor are outside these counts. The server neither replaces
nor shuts down a caller-owned executor, and introduces no separate work queue.
There is no queue-wait deadline: pending tasks retain their slots until they run
or submission fails. A blocking executor can still hold up the accept thread.
These limits bound admitted work, not request arrival rates or fair access.

## Validation

Latest local results: Linux, Scala Native 0.5.12; Temurin 21.0.10 and 8u482.

| Target | Scala | Passing tests |
| --- | --- | --- |
| Native | 2.12.21, 2.13.18, 3.9.0 | 162 per version |
| JDK 8 reference | 2.12.21, 2.13.18 | 45 per version |
| JDK 21 reference | 2.12.21, 2.13.18, 3.9.0 | 45 per version |

No skipped tests. Shared JUnit contracts cover the public API and real exchanges;
Native-only tests cover framing, malformed input, pipelining, drain limits,
timeouts, admission limits and shutdown. Passing cases do not establish complete
conformance. Commands are in [README.md](README.md#build-and-test).

External checks: [h1spec and Http11Probe results](conformance/REPORT.md), including
the JDK reference comparisons and the remaining disputed empty-Host verdict.
The [slow-client baseline](conformance/REPORT.md#slow-client-baseline) predates
the timeout and admission changes and recorded starvation under pressure,
recovery after disconnect and bounded shutdown. Current hardening has direct JUnit
coverage; the external baseline still needs updating and rerunning. These checks
do not establish DoS resilience.

Unverified: macOS/Windows, release-mode builds, sustained or large-scale load testing,
Java binary compatibility and direct protected-realm mutation
on Scala 2 (the host JDK 21 final field shadows the Native declaration).
Upstream placement/acceptance remains undecided. Outstanding work is tracked in
[TODO.md](TODO.md).

Source policy: independently written from published API contracts and
[RFC 9110](https://www.rfc-editor.org/rfc/rfc9110)/[9112](https://www.rfc-editor.org/rfc/rfc9112);
OpenJDK implementation code/tests must not be copied or translated. Adapted
third-party code requires source/revision/license attribution and notices in
`PROVENANCE.md`; none has been included.
