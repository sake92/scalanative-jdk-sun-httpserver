# HTTP API compatibility

The target is the [JDK 8 HTTP server API](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html).
This is a Scala Native HTTP subset. Full JDK package compatibility, Java binary
compatibility, and upstream acceptance have not been established.

## Public API inventory

All public declarations below live in `com.sun.net.httpserver`. Engine types in
`internal` have package-scoped visibility. No implemented method is an
`UnsupportedOperationException` placeholder.

| Type | Implemented constructors and declared methods |
| --- | --- |
| `Headers` | No-argument constructor; `Map[String, List[String]]` operations and views; `add`, `set`, `getFirst` |
| `HttpPrincipal` | `(username, realm)`; `getUsername`, `getRealm`, `getName`, `equals`, `hashCode`, `toString` |
| `Authenticator` | No-argument construction; abstract `authenticate` |
| `Authenticator.Result` | Abstract result base |
| `Authenticator.Success` | `(principal)`; `getPrincipal` |
| `Authenticator.Failure`, `Authenticator.Retry` | `(responseCode)`; `getResponseCode` |
| `BasicAuthenticator` | `(realm)`; protected mutable `realm`; `getRealm`, `authenticate`, abstract `checkCredentials` |
| `HttpHandler` | Interface represented as a Scala trait; `handle` |
| `Filter` | Protected no-argument constructor; abstract `description`, `doFilter` |
| `Filter.Chain` | `(filters, handler)`; `doFilter` |
| `HttpContext` | Protected no-argument constructor; abstract `getHandler`, `setHandler`, `getPath`, `getServer`, `getAttributes`, `getFilters`, `getAuthenticator`, `setAuthenticator` |
| `HttpExchange` | Protected no-argument constructor; abstract `getRequestHeaders`, `getResponseHeaders`, `getRequestURI`, `getRequestMethod`, `getHttpContext`, `getRequestBody`, `getResponseBody`, `sendResponseHeaders`, `getResponseCode`, `getRemoteAddress`, `getLocalAddress`, `getProtocol`, `getAttribute`, `setAttribute`, `setStreams`, `getPrincipal`, `close` |
| `HttpServer` | Protected no-argument constructor; factories `create()` and `create(address, backlog)`; abstract `bind`, `start`, `stop`, `getAddress`, `getExecutor`, `setExecutor`, both `createContext` overloads, both `removeContext` overloads |

`HttpServer.create` returns the built-in blocking implementation directly.
Contexts, filters, authenticators and real exchanges are functional.

## Absent API

| API | Status |
| --- | --- |
| `HttpsServer`, `HttpsExchange`, `HttpsConfigurator`, `HttpsParameters` and all their methods | Deferred with TLS |
| `com.sun.net.httpserver.spi.HttpServerProvider`, including `provider`, `createHttpServer`, `createHttpsServer` | Absent; factories do not consult the provider property or service descriptors |
| Newer `BasicAuthenticator` charset constructor, filter factories, `HttpExchange` request/response convenience methods and newer `HttpServer.create` overloads | Outside the JDK 8 baseline |
| `SimpleFileServer`, `Request` and other later package additions | Outside the JDK 8 baseline |

The [JDK provider contract](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/spi/HttpServerProvider.html)
includes an abstract factory returning `HttpsServer`. A conforming SPI therefore
requires the deferred HTTPS surface. No HTTP-only substitute SPI or TLS stubs
are supplied. Scala Native's [service-provider support](https://scala-native.org/en/stable/lib/javalib.html#support-for-discovering-service-providers)
requires providers to be selected at link time. Runtime loading of arbitrary
provider class names is an additional unresolved compatibility requirement.

## Protocol and lifecycle limits

- HTTP/1.0 and HTTP/1.1 use blocking sockets. HTTP/2, upgrades, WebSockets and
  successful CONNECT tunnels are unsupported. CONNECT receives 501; application
  attempts to send 101 or a successful CONNECT response are rejected.
- `sendResponseHeaders` accepts final status codes 200–599. Lazy `100 Continue`
  is automatic; applications cannot emit arbitrary informational responses.
- Request framing is deliberately strict: duplicate Content-Length fields,
  conflicting length/transfer framing and malformed Connection options are
  rejected. Transfer-Encoding supports only a single `chunked` coding on HTTP/1.1.
- Request lines and individual header lines are limited to 8 KiB, header
  sections to 64 KiB and field counts to 100. Chunk extensions and trailers have
  separate bounded parsing. Trailers are discarded and never merged into headers.
- Request-target and Host validation uses `java.net.URI`; host spellings that its
  server-authority parser rejects are unsupported. Context selection uses literal,
  case-sensitive raw path prefixes, including percent-encoded spelling.
- Bodyless responses reject payload writes. Positive response lengths are exact;
  zero selects chunking on HTTP/1.1 and connection-delimited output on HTTP/1.0.
  Negative lengths select no body. HEAD and body-forbidden statuses override framing.
- Persistence requires a completed response and a consumed or successfully drained
  request. Automatic draining has a 64 KiB wire-byte budget and a one-second
  total deadline. The next request head has a 30-second socket read timeout;
  this is not an absolute deadline against trickled headers. Initial request
  heads and application body reads have no server-imposed timeout. Upload size
  and connection/worker counts have no configurable server limit.
- Default execution runs on the owned accept thread. A persistent idle connection
  occupies that thread or a supplied executor worker. Caller executors are never
  shut down by the server. Pipelining is sequential within each connection.
- Stop is terminal. It closes idle sockets immediately, allows active handlers
  the requested grace period, then closes their sockets. Joining the owned thread
  has an additional 500 ms cleanup budget. User code that ignores interruption
  or closed sockets may outlive that budget. Caller executor threads are not
  interrupted by the server.
- Exchange attributes are local overrides with context attributes as defaults;
  updates do not mutate the context map. Filters are snapshotted for dispatch.
  Returning from a handler triggers exchange cleanup; response-less handlers
  receive a generated 500. Applications should explicitly terminate exchanges.

## Evidence and remaining assessment

Shared JUnit sources run against both this implementation and the real host JDK.
They cover headers, principal identity, filter chains, authentication, server
lifecycle, context routing/removal, exchange metadata and sequential connection
reuse. Native-only tests cover strict protocol policy, stream framing, malformed
input, pipelining, drain bounds, stream wrappers and shutdown during active,
queued and idle work. Passing shared tests establishes those cases only.

JDK 8 documented null behavior for Headers, empty authentication realms and null
filter exchanges differs from the observed JDK 21 runtime; Native follows the
chosen JDK 8 baseline. Basic authentication also deliberately handles malformed
credentials and scheme casing differently from JDK 21. Details and exact commands
are recorded in [PLAN.md](PLAN.md). Direct mutation of the protected realm is
unverified on Scala 2 because the JDK 21 final field shadows the Native symbol.

Validation currently covers Linux, Scala Native 0.5.12, Scala 2.12.21, 2.13.18
and 3.9.0, using Temurin JDK 21.0.10. JDK 8 runtime comparison, macOS, Windows,
release-mode builds and load testing remain unverified. No remote CI result is
claimed.

The standalone build has no added production library dependencies and enables
Native multithreading. Tests follow Scala Native's direct JUnit convention.
Sources and tests are independently written from published contracts; no OpenJDK
implementation/tests or adapted third-party sources are included. The repository
license is Apache-2.0.

Upstream module placement, support for this HTTP subset, required compatibility
exceptions, provenance review, contributor CLA and CI cost require maintainer
agreement. The [Scala Native contribution policy](https://github.com/scala-native/scala-native/blob/ed42ba979/docs/contrib/contributing.md)
is the reference inspected for this assessment. No maintainer discussion has
been initiated. The project remains a standalone library pending that decision.
