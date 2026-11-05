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
| `com.sun.net.httpserver.spi.HttpServerProvider`, including `provider`, `createHttpServer`, `createHttpsServer` | Intentionally out of scope; factories do not consult the provider property or service descriptors |
| Newer `BasicAuthenticator` charset constructor, filter factories, `HttpExchange` request/response convenience methods and newer `HttpServer.create` overloads | Outside the JDK 8 baseline |
| `SimpleFileServer`, `Request` and other later package additions | Outside the JDK 8 baseline |

The [JDK provider contract](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/spi/HttpServerProvider.html)
includes an abstract factory returning `HttpsServer`. A conforming SPI therefore
requires the deferred HTTPS surface. No HTTP-only substitute SPI or TLS stubs
are supplied. Scala Native's [service-provider support](https://scala-native.org/en/stable/lib/javalib.html#support-for-discovering-service-providers)
requires providers to be selected at link time. Runtime loading of arbitrary
provider class names is not supported. Provider discovery is intentionally
excluded from this project's scope.

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

Runtime compatibility follows the measured Temurin 8u482 implementation, including
behaviors that contradict its published documentation. The shared suite contains
45 tests, all passing on Native and on JDK 8 with Scala 2.12.21/2.13.18. The same
suite passes on JDK 21 with all three configured Scala versions; expected version
differences are asserted explicitly, with no skipped tests.

| Case | Native and measured Temurin 8u482 behavior |
| --- | --- |
| Principal rendering | `getName()` and `toString()` return the username; equality/hash still include the realm |
| Header bulk insertion | `putAll` preserves input key spelling; a noncanonical `X-Other` entry is not retrievable by normalized lookup |
| Direct header writes | Null names, lists and elements throw NPE; malformed line breaks throw IAE before mutation |
| Header reads | Null lookup/removal returns null; `containsKey(null)` is false; get/remove with non-string keys throw CCE |
| Folded header values | CRLF followed by space/tab is accepted by Headers; the stricter response serializer still rejects it on the wire |
| Authentication realms | Null and empty realms are accepted at construction |
| Duplicate contexts | Separate registrations retain insertion order; the first matching registration wins; identity removal preserves the others |
| Null filter exchange | Passes through the chain to callbacks, advancing the chain normally |
| Basic scheme casing | Only exact `Basic` is accepted |
| Malformed Basic credentials | Invalid Base64 throws IAE; missing decoded colon throws StringIndexOutOfBoundsException; extra separator spaces are not trimmed |
| Basic challenge | `Basic realm="users"`, without the newer charset advertisement |

JDK 21 differs in principal rendering, bulk header normalization, null header
reads, null/empty realm construction, challenge advertisement and duplicate
context registration. The shared tests assert those observed alternatives using
runtime version checks. Scala Native's local JUnit suites use the same approach
for behavior that varies across host JDKs; Native reports Java specification 1.8.

The initial comparison exposed six failures in 36 shared tests, plus five in six
supplemental baseline checks. After the user's runtime-alignment decision,
assertions were updated against the JDK 8 reference first, then demonstrated 12
failures against the old Native implementation, then passed with the fixes.
Response-writer tests inject malformed bulk entries and confirm rejection before
any bytes are emitted. No OpenJDK implementation or tests were inspected/copied.

Details and reproduction commands are recorded in [PLAN.md](PLAN.md). Direct
mutation of the protected realm remains unverified in Native tests on Scala 2
because the JDK 21 compiler sees its final field, despite the field being mutable
in the JDK 8 runtime's public signature.

Validation currently covers Linux, Scala Native 0.5.12, Scala 2.12.21, 2.13.18
and 3.9.0, using Temurin JDK 21.0.10, plus the JDK 8 runtime comparisons above.
Scala 3.9.0 emits Java 17 classfiles and cannot execute on Java 8; its JDK
reference validation remains on JDK 21. macOS, Windows, release-mode builds and
load testing remain unverified. No remote CI result is claimed.

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
