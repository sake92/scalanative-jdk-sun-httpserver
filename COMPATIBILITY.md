# Compatibility

Target: [JDK 8 HTTP API](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html),
with measured Temurin 8u482 runtime behavior taking precedence over contradictory
documentation. Full package and Java binary compatibility are unverified.

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

## Protocol and lifecycle

| Area | Behavior / limit |
| --- | --- |
| Requests | HTTP/1.0 and HTTP/1.1; rejects duplicate Content-Length, conflicting framing and malformed Connection options; only single `chunked` transfer coding on HTTP/1.1 |
| Parsing bounds | Request/header/chunk lines: 8 KiB; header/trailer sections and cumulative chunk extensions: 64 KiB; header/trailer counts: 100; trailers discarded |
| Routing | Longest case-sensitive raw-path prefix; percent encoding preserved; URI/Host spellings must be accepted by `java.net.URI` |
| Responses | Final status 200–599; automatic lazy `100 Continue`; CONNECT receives 501; 101/upgrades rejected |
| Body framing | Positive length is exact; zero is chunked on HTTP/1.1 or connection-delimited on HTTP/1.0; negative length is bodyless; HEAD/bodyless status rules override framing |
| Persistence | HTTP/1.1 defaults to keep-alive; HTTP/1.0 requires opt-in; pipelined responses retain order |
| Unread bodies | Reuse requires successful drain within 64 KiB of wire bytes and a one-second total deadline; otherwise connection closes |
| Timeouts | Next request head: 30-second socket read timeout, not an absolute deadline; initial head and application body reads: no server timeout |
| Capacity | No configurable upload-size, connection-count or worker limit |
| Execution | Default executor uses the accept thread; idle/slow connections occupy it. Concurrent dispatch requires a caller-owned executor, which the server never shuts down or interrupts |
| Shutdown | Terminal; closes idle sockets, gives active handlers `delay` seconds, closes remaining sockets, then joins the owned thread for up to 500 ms. Uncooperative handlers may outlive cleanup |
| Exchanges | Attributes are local overrides over context defaults; filters are snapshotted; handler return triggers cleanup; missing response produces 500 |

## Validation

Latest local results: Linux, Scala Native 0.5.12; Temurin 21.0.10 and 8u482.

| Target | Scala | Passing tests |
| --- | --- | --- |
| Native | 2.12.21, 2.13.18, 3.9.0 | 135 per version |
| JDK 8 reference | 2.12.21, 2.13.18 | 45 per version |
| JDK 21 reference | 2.12.21, 2.13.18, 3.9.0 | 45 per version |

No skipped tests. Shared JUnit contracts cover the public API and real exchanges;
Native-only tests cover framing, malformed input, pipelining, drain limits and
shutdown. Passing cases do not establish complete conformance. Commands are in
[README.md](README.md#build-and-test).

Unverified: macOS/Windows, release-mode builds, load testing, external HTTP
conformance suites, Java binary compatibility and direct protected-realm mutation
on Scala 2 (the host JDK 21 final field shadows the Native declaration).
Upstream placement/acceptance remains undecided.

Source policy: independently written from published API contracts and
[RFC 9110](https://www.rfc-editor.org/rfc/rfc9110)/[9112](https://www.rfc-editor.org/rfc/rfc9112);
OpenJDK implementation code/tests must not be copied or translated. Adapted
third-party code requires source/revision/license attribution and notices in
`PROVENANCE.md`; none has been included.
