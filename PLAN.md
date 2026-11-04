# Blocking HTTP server for Scala Native

## Goal and scope

Implement the HTTP portion of `com.sun.net.httpserver` in Scala, with synchronous,
blocking socket I/O and an Apache-2.0 license. Develop it here as a standalone
Scala Native library before proposing incorporation into Scala Native.

Implement one class at a time when dependencies permit. Each implementation
step starts only when requested, includes its behavior tests, and ends with a
passing test run and a short account of limitations. Repository setup is
complete; `Headers` and `HttpPrincipal` are implemented.

The initial compatibility target is the JDK 8-era public HTTP API. Use current
published API documentation to identify subsequent changes, without silently
expanding the baseline. HTTPS (`HttpsServer`, `HttpsExchange`, TLS configuration),
newer convenience APIs such as `SimpleFileServer`, HTTP/2, WebSockets, and Sharaf
integration are deferred. This is not initially full-package compatibility.

## Licensing and references

- Implement independently from published API contracts and HTTP specifications.
  Do not copy or translate OpenJDK implementation code or tests.
- New project code is Apache-2.0. Retain attribution and required notices for
  any deliberately adapted permissive source; record its repository, revision,
  files, original license, and adaptations in `PROVENANCE.md` when first used.
- Inspect NanoHTTPD (BSD-3-Clause) selectively for protocol and stream design.
  Check the actual file licenses before adapting anything.
- Uni's Native server is Apache-2.0 and provides useful blocking-I/O examples,
  but its Rx model, whole-body buffering, WebSockets, and unsupported chunked
  requests are not the intended architecture here.
- Harmony has HTTP helpers but no implementation of this package was found.
- Follow Scala Native's contribution policy. Its guide permits copied
  Scala.js/Harmony code case by case, and does not preapprove other permissive
  projects. Discuss provenance and placement with maintainers before upstreaming.

Reference checkouts:

- `/home/sake/projects/sake92/scala-native`
- `/home/sake/projects/sake92/uni/uni/.native/src/main/scala/wvlet/uni/http`
- `/home/sake/projects/forks/harmony`
- NanoHTTPD: https://github.com/NanoHttpd/nanohttpd (inspect when needed).

Primary contracts:

- https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html
- https://www.rfc-editor.org/rfc/rfc9110
- https://www.rfc-editor.org/rfc/rfc9112
- Scala Native: `docs/contrib/contributing.md`, `docs/user/testing.md`,
  `unit-tests/shared/src/test/scala/org/scalanative/testsuite/javalib/net/`.

## Build and source layout

- Pin sbt 2.0.12 and Scala Native 0.5.12. Build and test on Scala 2.12.21,
  2.13.18, and 3.9.0, with Scala 3.9.0 as the default.
- Enable `ScalaNativePlugin` and `ScalaNativeJUnitPlugin`, with multithreading.
- Public implementation: `src/main/scala/com/sun/net/httpserver/`.
- Private engine: an internal package with package-scoped implementation types.
- JUnit tests: `src/test/scala/org/scalanative/testsuite/httpserver/`.
- Keep production dependencies empty initially; use `java.net.ServerSocket`,
  `Socket`, Java streams, collections, and concurrency primitives already in SN.
- Prefer Java-compatible API declarations and conservative Scala syntax to ease
  a later port into Scala Native's cross-Scala source tree. Verify all
  configured Scala 2.12, 2.13, and 3 lines before claiming upstream readiness.
- Add a separate JVM reference-test project with the first HTTP contract tests.
  Reuse those tests against the real JDK classes, without loading this library's
  classes on the JVM or copying JDK tests. Keep Native-only engine tests separate.

## Incremental implementation

### 0. Repository bootstrap (completed)

Initialize Git, add the license, pinned sbt build, ignore rules, README, and
this plan. Run one Native JUnit smoke test of binding and closing a loopback
socket; this establishes a working native toolchain, not HTTP support.

### 1. Headers

Implement the Java `Map[String, java.util.List[String]]` contract and the
baseline constructors and convenience methods (`add`, `set`, `getFirst`).
Test case-insensitive lookup, repeated values, replacement/removal, map views,
equality/hash behavior, and documented invalid inputs. Decide immutable request
header handling according to the selected API baseline rather than assuming
every newer JDK behavior belongs in JDK 8 compatibility.

Add the JVM reference project and run the same contract cases on both targets.
Distinguish specified behavior from version-specific JDK observations.

### 2. Principal and authentication values

Implement `HttpPrincipal`, then `Authenticator` and its nested result classes,
then `BasicAuthenticator`. Test principal identity, success/failure/retry results,
challenge headers, valid Basic credentials, missing and malformed credentials,
and the documented encoding rules. Classes depending on `HttpExchange` wait
until its declaration is available; avoid temporary fake public contracts.

### 3. Exchange, handler, context, and filter API

Declare `HttpHandler`, `HttpExchange`, `HttpContext`, `Filter`, and `Filter.Chain`.
These types refer to each other, so their initial declarations may require one
small batch; implement and test behavior class by class afterwards.

Preserve baseline Java method signatures, return types, abstract classes, and
extension points. Test filter order, wrapped streams, context/exchange
attributes, authenticator integration, and handler exceptions. Keep test-only
exchange fixtures small; use real exchanges once the server is available.

The API step also owns the complete JDK 8 `Authenticator` result hierarchy and
the permanent abstract instance contract of `HttpServer`, because
`HttpContext` exposes both types. `HttpServer` provider factories and concrete
socket behavior remain deferred to section 5; no throwing public placeholders
are used.

### 4. Blocking HTTP framing and streams

Implement internal request-line/header parsing and body framing before server
integration. Do not tie parsing to application routing or buffer whole bodies.

Support Content-Length and chunked requests, chunk extensions/trailers as
appropriate, and retained bytes between exchanges. Bound line/header sizes and
counts and reject malformed or ambiguous framing. Support interim
`100 Continue` at the correct point in request processing.

Implement fixed-length request/response streams, chunked streams, and no-body
responses. Enforce the `sendResponseHeaders` distinction: positive length is
exact, zero selects chunking, negative length means no body. Cover HEAD,
bodyless status codes, partial reads, over/under-writing, flush/close, and errors.
Use byte-level tests with deliberately fragmented input and multiple messages.

### 5. HttpServer and real exchanges

Implement `HttpServer` creation/binding, contexts, executor selection, start,
address lookup, and stop, backed by the framing engine and concrete exchanges.
Implement default provider behavior; add `spi.HttpServerProvider` discovery
separately after checking SN's AOT-aware ServiceLoader constraints. HTTPS
provider methods depend on deferred HTTPS types; do not invent TLS stubs merely
to claim complete SPI coverage.

Use an accept thread and blocking connection processing. Document how idle
keep-alive connections consume workers in this first design. Preserve caller
executor ownership; only shut down executors the library creates itself.
Track sockets so stop can unblock reads and close idle/active connections.
Implement bounded graceful shutdown and terminal lifecycle states.

Test port-zero binding, literal longest-prefix context selection, unknown paths,
custom executor dispatch, HTTP/1.0 versus HTTP/1.1 persistence, sequential and
pipelined requests, unread bodies, client disconnects, handler failures, and
stop during idle and active exchanges. No async networking engine is planned.

### 6. Compatibility and upstream assessment

Complete the API inventory and document unsupported methods explicitly. Run
shared contract tests on the JDK and Native, plus Native raw-socket integration
tests. Validate Linux first, then macOS and Windows before claiming portability.

Review Scala Native module/source placement, Scala-version compatibility,
JUnit conventions, dependency footprint, licensing/provenance, and CI costs with
maintainers. Decide whether to upstream an HTTP subset or continue publishing a
standalone library. Upstream acceptance is not assumed.

## Test discipline

For each class, add a compact behavior/expected-result/test-name checklist before
implementation. Tests should exercise public contracts and meaningful protocol
boundaries, not private implementation structure. Reuse Scala Native's JUnit
assertion style without copying upstream tests.

Use OS-assigned loopback ports, finite socket timeouts and bounded thread joins,
and cleanup in `finally`. Avoid sleep-based synchronization and shared mutable
test state. Raw sockets are necessary for malformed HTTP, framing, persistence,
and disconnect tests that higher-level clients hide.

| Bootstrap behavior | Expected result | Test |
| --- | --- | --- |
| Bind and close a real Native socket | Loopback binding gets a positive OS-assigned port; close marks the socket closed | `NativeSocketSmokeTest.bindsLoopbackSocketAndReleasesIt` |

## Progress

- [x] Bootstrap builds and Native smoke test passes (1 test, 0 failures).
- [x] Git repository initialized at `/home/sake/projects/sake92/scalanative-jdk-sun-httpserver`.
- [x] Headers and shared JVM/Native contract tests.
- [x] HttpPrincipal and shared JVM/Native contract tests.
- [x] Authenticator and its result hierarchy.
- [ ] BasicAuthenticator.
- [x] Handler/exchange/context/filter API and permanent abstract `HttpServer` instance contract.
- [ ] Framing and body streams.
- [ ] Blocking server and socket integration tests.
- [ ] Compatibility audit and upstream discussion.
