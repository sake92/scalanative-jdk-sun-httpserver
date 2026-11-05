# Blocking HTTP server for Scala Native

## Goal and scope

Implement the HTTP portion of `com.sun.net.httpserver` in Scala, with synchronous,
blocking socket I/O and an Apache-2.0 license. Develop it here as a standalone
Scala Native library before proposing incorporation into Scala Native.

Implement one class at a time when dependencies permit. Each implementation
step starts only when requested, includes its behavior tests, and ends with a
passing test run and a short account of limitations. Work directly and maintain
current decisions, behavior checklists, and validation results in this file.
Repository setup is
complete; headers, principals, authentication, the initial exchange/context/
filter API, request-head parsing/framing validation, and fixed-length/chunked
request input, fixed-length/chunked/no-body response output, and response-head
serialization/framing selection are implemented, along with working default
server factories, contexts and concrete exchanges. HTTP persistence, sequential
pipelining and bounded unread-body draining are implemented. Compatibility and
lifecycle audit, including provider-discovery feasibility, is next.

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
- Shared contract tests are direct JUnit classes (`HeadersTest`,
  `HttpPrincipalTest`, `FilterTest`, and `BasicAuthenticatorTest`) with test
  bodies and `org.junit.Assert` assertions. The isolated JVM reference project registers
  those same sources in its `Test` configuration against the JDK classes; it
  does not load this library's production classes.
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
equality/hash behavior, and documented invalid inputs. Immutable request headers
are already required by JDK 8's `HttpExchange` contract. Implement their read-only
map behavior in the concrete exchange phase; do not import newer header APIs.

Add the JVM reference project and run the same contract cases on both targets.
Distinguish specified behavior from version-specific JDK observations.

The implementation normalizes ASCII header names and exposes the backing map's
standard live key, value, and entry views. Retain iterator removal and entry
`setValue` semantics; structural removal outside an active iterator previously
broke continued iteration. JDK 8 documents null keys and values as accepted;
Native covers this separately because the local JDK 21 rejects null operations.

### 2. Principal and authentication values

Implement `HttpPrincipal`, then `Authenticator` and its nested result classes,
then `BasicAuthenticator`. Test principal identity, success/failure/retry results,
challenge headers, valid Basic credentials, missing and malformed credentials,
and the documented encoding rules. Classes depending on `HttpExchange` wait
until its declaration is available; avoid temporary fake public contracts.

`BasicAuthenticator` retains the one-argument constructor and protected mutable
realm from JDK 8. Credentials use the platform default charset. Native follows
the documented NPE for an empty realm and emits the baseline Basic realm
challenge without the newer charset parameter. Scheme names are case-insensitive;
malformed Base64 or decoded credentials without a colon return Failure 401 rather
than leaking parsing exceptions. These intentional differences from the JDK 21
reference are covered separately in Native-only tests.

Authentication uses the first Authorization value and splits decoded credentials
at the first colon. Empty credential components and further password colons reach
`checkCredentials`; credentials are checked for every exchange without caching,
trimming, or Unicode normalization. Only Base64 decoding failures are converted
to authentication failure; callback exceptions, including IllegalArgumentException,
propagate unchanged. Native's decoder tolerates surplus padding, so authenticate
checks padding shape first while accepting valid omitted padding.

Missing credentials produce Retry 401; rejected credentials produce Failure 401.
Both replace the WWW-Authenticate challenge and preserve other response headers.
Unsupported schemes or malformed encodings produce Failure 401 without calling
the credential checker or mutating response headers. Shared challenge assertions
accept only the exact baseline form or the newer UTF-8 form; Native-only checks
require `Basic realm="users"`. The newer charset constructor and realm quoting
validation remain deferred.

`HttpPrincipal` equality and hashing use immutable constructor identity even when
subclasses override accessors. `toString` delegates to virtual `getName`. The JDK
8 hash documentation has malformed parentheses; the interpretation as the Java
String hash of `username + realm` matches the local JDK probe and shared tests.
Pairs with identical rendered names due to colon placement remain distinct.

### 3. Exchange, handler, context, and filter API

Declare `HttpHandler`, `HttpExchange`, `HttpContext`, `Filter`, and `Filter.Chain`.
These types refer to each other, so their initial declarations may require one
small batch; implement and test behavior class by class afterwards.

Preserve baseline Java method signatures, return types, abstract classes, and
extension points. Test filter order, wrapped streams, context/exchange
attributes, authenticator integration, and handler exceptions. Keep test-only
exchange fixtures small; use real exchanges once the server is available.

`Filter.Chain` rejects a null exchange before advancing the chain, as documented
by JDK 8. JDK 21 accepts null, so that baseline check is Native-only; ordinary
shared chain tests use a non-null inert exchange.

The API step also owns the complete JDK 8 `Authenticator` result hierarchy and
the permanent abstract instance contract of `HttpServer`, because
`HttpContext` exposes both types. Default `HttpServer` factories and concrete
socket behavior are now implemented in section 5; custom provider discovery
remains deferred. No throwing public placeholders are used.
`Authenticator` and `Authenticator.Result` have public constructors,
matching the JDK 8 declarations. `HttpExchange.getResponseCode()` is part of the JDK 8 baseline;
newer `Request` and `AutoCloseable` additions are deferred.

### 4. Blocking HTTP framing and streams

Completed slice: no-body response output. Behavior checklist (written before
implementation), using `FixedLengthOutputStreamTest`:

| Behavior | Expected result | Test |
| --- | --- | --- |
| Header-only response | Zero-length writer close flushes buffered headers, emits no body/chunk terminator, preserves sink ownership and following response bytes | `noBodyCloseFlushesHeadersWithoutEmittingFraming` |
| Forbidden payload | Single/bulk payload writes fail before any payload/framing bytes reach sink; failure remains terminal | `noBodyWritesFailWithoutEmittingPayload` |
| Empty operations and validation | Zero-length writes do not touch sink; invalid buffers/ranges do not poison stream | Existing `emptyWritesDoNotTouchTheSink`, `rejectsInvalidArgumentsWithoutEmittingBytes` |
| Flush failure | Explicit/final flush errors on an empty body prevent completion and are never retried | `flushFailuresAreTerminal` extended to both zero and positive lengths |

No new wrapper or factory is needed: `FixedLengthOutputStream(output, 0)`
already provides strict no-body output, including empty-write handling, terminal
payload rejection, successful close/flush, and sink ownership. This reuse is now
documented and covered by focused wire-boundary tests. A no-body stream emits neither
payload nor chunk framing; headers are emitted by the later exchange layer.
It is not a discard stream: attempted payload writes fail. Public response-length
selection (positive/fixed, zero/chunked, negative/no-body), HEAD and status-code
rules, request-input closure and connection abort remain concrete-exchange work.

Completed slice: chunked response output. Behavior checklist (written before
implementation), all in `ChunkedOutputStreamTest`:

| Behavior | Expected result | Test |
| --- | --- | --- |
| Wire encoding | Each nonempty write emits hexadecimal length, CRLF, exact payload and CRLF; respect array slices and low eight bits of single-byte writes | `encodesWritesWithHexSizes` |
| Empty operations | Zero-length writes emit nothing, including no premature zero chunk; empty response ends only on close | `emptyWritesEmitNothingAndEmptyCloseTerminates` |
| Flush | Flush buffered sink without emitting a terminator or reporting completion; subsequent writes remain valid | `flushDoesNotTerminateTheBody` |
| Close/ownership | Write one terminal chunk and flush, never close the sink; repeated close is a no-op, writes/flush after close fail, next response can use sink | `closePreservesConnectionOwnership` |
| Arguments | Null sink/arrays and invalid/overflowing ranges fail without output or poisoning a valid stream | `rejectsInvalidArgumentsWithoutEmittingBytes` |
| Write failure | Inject IOException at every byte of chunk header, payload and CRLF; preserve exception and block all retries or termination output | `writeFailuresAreTerminal` |
| Termination failure | IOException at every byte of final chunk leaves completion false; close is not retried | `terminationFailuresAreTerminal` |
| Flush failure | Explicit/final flush errors are terminal, preserve the original exception, and prevent completion | `flushFailuresAreTerminal` |
| Decoder interoperability | Decode varying single/bulk write boundaries and payload sizes with the real chunked input stream; retain next response bytes | `roundTripsAcrossWriteBoundaries` |

Each nonempty write becomes one chunk, with no body buffering or copying of
bulk payloads. This favors a small streaming primitive; callers making many
small writes may buffer above it. Flush only forwards to the sink. Close emits
`0\r\n\r\n`, flushes, then marks completion. No response extensions or trailers
are emitted. As with fixed-length output, sink ownership, exchange cleanup,
headers/framing selection, and connection abort after any terminal failure
belong to the later exchange/server layer. No concurrent writes or retries
after partial output are supported. Repeated close does nothing even after a
failed first close.

Completed slice: fixed-length response output. Behavior checklist (written before
implementation), all in `FixedLengthOutputStreamTest`:

| Behavior | Expected result | Test |
| --- | --- | --- |
| Exact writes | Single-byte writes use low eight bits; array writes respect offset/length; emit raw payload only | `writesExactlyTheDeclaredBody` |
| Over-write | Reject the entire excessive write before forwarding any of it; latch terminal failure even when the declared count was already reached | `excessWritesAreTerminalAndDoNotEmitAPrefix` |
| Under-write | Close before the exact count throws IOException, does not pad/flush, and never reports completion | `shortBodiesFailOnClose` |
| Flush and completion | Flush reaches buffered sink without completing body; only successful close after exact writes and flush reports completion | `flushDoesNotCompleteTheBody` |
| Ownership | Close flushes once and never closes the sink; repeated close does nothing; later responses can use the same sink | `closePreservesConnectionOwnership` |
| Empty operations | Zero-length writes never touch sink or alter count; internally empty body still requires close/flush | `emptyWritesDoNotTouchTheSink` |
| Invalid arguments | Reject null sink, negative count, null arrays and invalid/overflowing ranges without emission or poisoning a valid stream | `rejectsInvalidArgumentsWithoutEmittingBytes` |
| Long lengths | Keep count in Long and stream without allocating body-sized storage | `supportsLongBodyLengths` |
| Sink write failure | Preserve IOException, including after partial emission; block subsequent writes/flush and prevent completion | `writeFailuresAreTerminal` |
| Sink flush failure | Preserve IOException on explicit flush or final close; no retry of failed output; completion remains false | `flushFailuresAreTerminal` |

Completion requires closing the wrapper successfully, including flushing its
caller-owned sink. Length violations and sink IOException failures are terminal;
the later exchange/connection layer must close the connection on these failures
and close the request input when the response is closed. This primitive neither
emits headers nor selects framing, handles HEAD/bodyless statuses, or owns the
socket. An internal zero-length instance is allowed, but does not change the
public `sendResponseHeaders(..., 0)` meaning of chunked output. No concurrent
writes, automatic padding, body buffering, or retry after partial output is
supported. Repeated close is a no-op even after the first close throws.

Completed slice: chunked request input. Behavior checklist (written before
implementation), all in `ChunkedInputStreamTest`:

| Behavior | Expected result | Test |
| --- | --- | --- |
| Decoding | Single/bulk reads return only payload, respect buffer boundaries, handle mixed-case hex and leading zeros, and retain next request bytes | `decodesChunksAndPreservesNextRequest` |
| Empty body | Consume terminal chunk and trailer terminator before reporting completion; constructors and zero reads do no I/O | `completionRequiresTheTrailerTerminator` |
| Extensions | Accept tokens, quoted strings/escapes and grammar-permitted whitespace; ignore unknown extensions on data/last chunks | `acceptsChunkExtensions` |
| Bad framing | Reject malformed/overflowing sizes, bad CRLF and malformed extensions; failure is terminal | `rejectsMalformedChunkFraming` |
| Large chunks | Keep size in Long and stream without allocating the whole chunk | `supportsLongChunkSizes` |
| Trailers | Validate field syntax with the same rules as headers, discard all trailers without merging them, and preserve next request | `validatesAndDiscardsTrailers` |
| Bounds | Enforce inclusive chunk-line, cumulative extension, trailer-line/section and trailer-count limits | `enforcesMetadataLimits` |
| Fragmentation | Decode across every source split, buffered short reads, and chunk boundaries | `handlesEveryFragmentBoundary` |
| Truncation | EOF at any incomplete wire prefix throws EOFException and cannot be retried | `truncationIsTerminal` |
| I/O failure | Preserve source IOException during sizes, payload, delimiters, trailers or availability; prevent subsequent source access | `sourceFailuresAreTerminal` |
| Skip | Count actual decoded bytes across chunks, including short reads; stop at completion for huge skips | `skipCountsOnlyPayloadBytes` |
| Availability | Report only available bytes within current payload; never parse framing or trailers in available | `availabilityNeverConsumesFraming` |
| Arguments | Null input/buffers and invalid/overflowing ranges fail without consuming bytes | `rejectsInvalidArgumentsWithoutConsumingInput` |
| Close and rewind | Idempotent non-draining close preserves source ownership; reject operations after close and unsupported reset | `closeAndResetPreserveConnectionOwnership` |

The stream retains no trailer fields: syntax-valid trailers, including fields
that would be unsafe to merge, are discarded and cannot alter request headers,
authentication, routing or framing. There is no JDK 8 public trailer API. Unknown
well-formed extensions are ignored. Chunk lines default to 8192 octets excluding
CRLF, cumulative extension text to 65536 octets, and trailer lines/sections/counts
to the same bounds as request headers. No whole-body or whole-chunk buffering,
decoded-body size cap, automatic draining, socket timeouts or server integration
is introduced. `isComplete` requires consuming the zero chunk, trailers and final
blank line; reading/skipping exactly the payload length alone is insufficient.
Common field/token syntax is shared with the existing request-head parser
to keep trailer validation consistent.

Completed slice: fixed-length request input. Behavior checklist (written before
implementation), all in `FixedLengthInputStreamTest`:

| Behavior | Expected result | Test |
| --- | --- | --- |
| Bounded reads | Single/bulk reads stop at declared length, preserve unsigned byte values and caller buffer boundaries, and leave the next request untouched | `readsExactlyTheDeclaredBody` |
| Short source reads | Account for actual bytes read across short refills without whole-body buffering | `handlesFragmentedInput` |
| Empty body and zero reads | Empty bodies are complete immediately; zero-length reads return zero without touching source | `emptyBodiesAndZeroLengthReadsDoNotConsumeSource` |
| Invalid arguments | Reject null source, negative length, null buffers and invalid/overflowing ranges without consuming bytes | `rejectsInvalidArgumentsWithoutConsumingInput` |
| Long lengths | Keep remaining count in Long without narrowing or allocating the whole body | `supportsLongBodyLengths` |
| Skip | Skip actual bytes within the body, including short refills; nonpositive skips do nothing; huge skips preserve next message | `skipIsBoundedAndHandlesShortReads` |
| Availability | Clamp source estimate to remaining body; do not probe source after completion | `availabilityIsBoundedByRemainingBody` |
| Truncation | Single/bulk reads and skip throw EOFException; completion remains false; retry cannot resume after a framing failure | `truncationIsTerminal` |
| Source failure | Preserve the source IOException and prevent later reads from reusing a damaged stream | `sourceFailuresAreTerminal` |
| Close | Idempotent, non-draining close leaves shared source open and rejects subsequent stream operations; completion reflects actual consumption | `closePreservesConnectionOwnership` |
| Mark/reset | Do not allow rewinding shared connection bytes | `doesNotSupportMarkOrReset` |

The wrapper owns only body-read state. It exposes a read-only `isComplete` for
later connection-reuse decisions. `close` does not drain unread data or close
the source; the later connection/exchange layer must drain under its own limits
or close the connection when incomplete. Reads are blocking and use the source's
timeout/cancellation policy. No concurrent reads are supported. Skip must count
actual read results: the inspected Native base InputStream skip implementation
can stop early on fragmented input, so this wrapper implements its own bounded
read-and-discard loop. No general stream hierarchy or server integration is
introduced in this slice.

Completed slice: request-head parsing and framing validation. Behavior checklist
(written before implementation):

| Behavior | Expected result | Test in `RequestHeadParserTest` |
| --- | --- | --- |
| Request metadata and fields | Preserve method/target/version and ordered repeated fields; trim only field OWS, retain Latin-1 bytes | `parsesMetadataAndRepeatedFields` |
| Target forms | Accept origin, absolute, CONNECT authority and OPTIONS asterisk forms without decoding paths | `acceptsRequestTargetForms` |
| Request syntax | Reject malformed tokens, targets, spacing, versions and line endings | `rejectsMalformedRequestLines` |
| Header syntax and Host | Reject folding, whitespace before colon, controls, missing/duplicate/invalid HTTP/1.1 Host | `rejectsMalformedHeadersAndHost` |
| Length framing | Absent length means no body; decimal length supports zero and Long.MaxValue | `selectsFixedLengthOrNoBody` |
| Ambiguous length | Reject repeated/comma lengths, signs, non-digits and overflow | `rejectsInvalidOrRepeatedLengths` |
| Transfer framing | Accept only a single case-insensitive chunked coding on HTTP/1.1; reject CL+TE and unsupported codings | `validatesTransferEncoding` |
| Limits | Enforce inclusive request-line/header-line octet, header-section octet and field-count limits | `enforcesLimitsAtBoundaries` |
| EOF and I/O errors | Clean EOF is None; truncated heads fail; source IOException propagates unchanged | `distinguishesCleanEofFromTruncation`, `propagatesInputFailure` |
| Fragmentation and retained bytes | Parsing works across short refills and leaves body/next request bytes on the same stream | `preservesBodiesAndFollowingRequests`, `handlesEveryFragmentBoundary` |

This slice uses strict CRLF and single-SP request-line syntax. Duplicate
Content-Length fields (including identical values) are rejected, an allowed
strict policy under RFC 9112 section 6.3. Only `chunked` transfer coding is
supported; compression transfer codings remain unsupported. The parser reads
from a caller-owned stream without read-ahead; a connection may supply one
BufferedInputStream shared with subsequent body readers. The parsed fields are
immutable and no public API is added. Remaining body streams, persistence decisions,
Expect processing and emitting `100 Continue` wait for their later slices.
Host and absolute/CONNECT authorities currently use Java URI server-host syntax;
unusual registered names such as underscore-containing hosts and IPvFuture
literals are not supported. Parse errors carry the prospective HTTP response
code (400, 414, 431, 501 or 505); the later connection processor must close the
connection on failure. No error responses are emitted by this slice.

Implement internal request-line/header parsing and body framing before server
integration. Do not tie parsing to application routing or buffer whole bodies.

Implement this phase in separately requested slices: request-line/header parsing
and framing validation; fixed-length request input; chunked request input; then
response streams. The first slice is complete and stops at a parsed request
head and framing decision, with byte-level tests for fragmented input, limits,
ambiguous framing, and preservation of bytes belonging to the body or next
request. Fixed-length and chunked request input are also complete, including
chunk extensions, trailer syntax validation/discarding, and retention of
following-request bytes. Fixed-length and chunked response output are also
complete, and no-body output reuses the fixed-length writer with length zero.
Stream primitives and their first concrete server/exchange integration are
complete. HEAD/bodyless response selection, lazy `100 Continue`, persistence,
sequential pipelining and bounded unread-body draining are tested through real
exchanges in section 5. The next separately requested step is the compatibility
and lifecycle audit in section 6, including HTTP-only provider feasibility.

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

Completed slice: persistence, ordered pipelining and bounded unread-body draining.
Checklist before implementation, in `HttpServerTest`:

| Behavior | Expected result | Test |
| --- | --- | --- |
| HTTP/1.1 persistence | Reuse a socket by default; case-insensitive close tokens on either side override persistence; exchanges get fresh state | `reusesHttp11ConnectionsAndHonorsCloseTokens` |
| Pipelining | Parse and respond sequentially in arrival order across fixed/chunked/no-body boundaries, preserving buffered bytes | `pipelinesMixedFramingInOrder` |
| HTTP/1.0 | Require explicit keep-alive, and close for unknown-length output regardless of keep-alive | `http10RequiresExplicitKeepAlive` |
| Unread/closed bodies | Drain small fixed/chunked bodies and trailers even after application close; cap-inclusive success preserves next request | `drainsUnreadAndClosedBodies` |
| Unsafe remainder | Oversized wire remainder, malformed/truncated framing, or unsent Expect body prevents reuse; never dispatch buffered next request | `closesWhenDrainBudgetOrBodyFramingFails` |
| Absolute drain deadline | Slow trickling data cannot renew the drain budget; close within bounded time | `slowUnreadBodiesHaveAnAbsoluteDrainDeadline` |
| Stop and idle state | Remove finished exchanges from active state before waiting for another head; stop closes idle sockets and does not dispatch queued requests | `stopClosesIdlePersistentConnectionsAndSuppressesQueuedRequests` |
| Output failure | Over/under-writing, handler failures and explicit response close prevent further dispatch on that connection | `doesNotReuseOnResponseFailure` |

Policy for this slice: at most 65536 wire bytes and one second total are spent
draining a request body. Both payload and chunk metadata count; time is checked
before every source read, including chunk metadata. Socket read timeouts shrink
to the remaining deadline and are restored afterwards. Application body reads
retain their prior blocking behavior. A 30-second socket timeout applies while
waiting for another request head. Close-delimited HTTP/1.0 output always closes.
An early final response with an unsent 100-continue body closes without draining.
Protocol errors and uncaught handler/output failures never permit reuse.
Completing a non-reusable response closes the socket immediately, even if the
handler continues running afterwards; handler return is not the close deadline.
Early routing/protocol rejections conservatively close the connection. If a
drain fails after the response head has already advertised persistence, the
connection is still closed and no further request is dispatched.

One buffered input/output pair belongs to the connection; each exchange gets
new framing streams and attributes. Closing an application request stream closes
that view, while the exchange may finish draining its framing decoder before
closing it. Response completion includes closing wrappers, finishing output,
and resolving the unread request body. Pipelining is sequential; there is no
parallel execution of requests on a single connection. Idle persistent sockets
occupy a worker (the accept thread with the default executor), so callers should
provide a concurrent executor when serving multiple long-lived connections.

Completed slice: first concrete server/context/exchange integration. These types
depend on one another and are implemented together so factories return a working
server rather than placeholders. Its initial policy was one exchange per
connection with `Connection: close`; that policy is superseded by the persistence
slice above. Original checklist before implementation, `HttpServerTest`:

| Behavior | Expected result | Test |
| --- | --- | --- |
| Factories/binding | Unbound and port-zero factories, delayed bind, duplicate/failed bind, terminal lifecycle and argument checks | `factoriesAndLifecycle` |
| Contexts/routing | Literal raw-path longest prefix, case and percent encoding preserved, query excluded; unknown/removed routes 404, deferred handler assignment works | `routesLiteralPathsAndUpdatesContexts` |
| Real framing | Fixed/chunked requests reach handlers; fixed/chunked/no-body/HTTP1.0 outputs work; HEAD/bodyless statuses emit no payload | `servesRequestAndResponseFraming` |
| Read-only request headers | Reject basic/default map mutations, list/view/entry/iterator mutations; response headers remain writable | `requestHeadersAreDeeplyReadOnly` |
| Filters/attributes/wrapping | Real filter order, inherited context attributes and per-exchange overrides, null handling, wrapped streams and exception propagation boundaries | `filtersAttributesAndWrappedStreams` |
| Authentication | Retry/failure bypass handler, success supplies principal; authenticator replacement/removal takes effect | `authenticatesRealExchanges` |
| Continue | Emit 100 only on first body read after authentication; unsupported expectations get 417; early final responses never emit 100 | `handlesExpectContinue` |
| Failure boundaries | Malformed heads/bodies, handler failures, repeated headers, response over/under-writes and disconnects do not corrupt later connections | `isolatesProtocolAndHandlerFailures` |
| Executor ownership | Dispatch uses supplied executor; rejection closes connection; stop never shuts caller executor down | `usesCallerExecutorWithoutOwningIt` |
| Stop | Close idle/header/body reads, reject queued work after stop, allow active handlers a bounded grace period, preserve interrupts and terminal state | `stopClosesBlockedConnections`, `stopHonorsGraceAndCallerOwnership` |

Default dispatch runs synchronously on the owned accept thread, matching the
baseline's simple default executor; slow/idle clients serialize default work.
Custom executors are caller-owned and control concurrency. Accepted sockets are
registered before dispatch. Stop closes intake and idle/queued connections,
waits up to the requested delay for active handlers, closes remaining sockets,
interrupts the owned accept thread and joins it within a separate 500 ms cleanup
budget. It does not interrupt/shut down caller executor threads. Arbitrary user
code that ignores both interruption and closed sockets cannot be forcibly
terminated; incomplete owned-thread cleanup is reported to stderr. Socket reads
retain blocking application body reads; stop owns their cancellation by socket
shutdown/close. The persistence slice adds bounded draining and a timeout while
waiting for another request head. Asynchronous handler continuation, ServiceLoader
SPI discovery, TLS, CONNECT tunnels, upgrades and manually emitted final 1xx
responses remain unsupported. Attributes are per-exchange overrides with context
fallback. HEAD/no-body payload writes are rejected rather than silently discarded.

Completed slice: response-head serialization and framing selection, the missing
wire boundary needed before a concrete exchange can use the completed body
streams. Keep it internal; no public server factories or simulated exchanges
are added yet. Checklist (before implementation), `ResponseHeadWriterTest`:

| Behavior | Expected result | Test |
| --- | --- | --- |
| Status/header bytes | Emit HTTP/1.x status line, ordered repeated Latin-1 fields and exactly one blank line, without flushing/closing sink | `serializesStatusAndRepeatedFields` |
| Framing | Positive length replaces CL/removes TE; zero selects HTTP/1.1 chunking; negative selects no body with CL 0 | `selectsAndNormalizesFraming` |
| HEAD/304 metadata | No body/TE; positive length is representation metadata; nonpositive preserves a valid explicit CL | `headAndNotModifiedSuppressBody` |
| Bodyless statuses | 1xx/204 suppress CL and TE; 205 suppresses body and uses CL 0 regardless of supplied length | `bodylessStatusesOverrideRequestedLength` |
| HTTP/1.0 | Fixed/no-body work normally; unknown length is close-delimited with Connection close and no TE/CL | `http10NeverUsesChunking` |
| Date | Add a GMT HTTP date when absent; preserve caller Date | `addsDateWhenAbsent` |
| UTC calendar | Locale-independent four-digit UTC dates, including negative epochs, leap centuries and date bounds | `formatsUtcDatesAcrossCalendarBoundaries` |
| Validation | Reject injection, controls, invalid/non-Latin-1 fields, nulls and invalid arguments before output or header-map mutation | `rejectsInvalidHeadsBeforeEmission` |
| Representation length | Reject repeated, negative, nondecimal or overflowing preserved CL before output | `rejectsInvalidRepresentationLengths` |
| Sink failure | Propagate exact IOException even after partial header output; caller must abort, never retry | `propagatesSinkFailures` |
| Real wire | Serialize successive heads/body bytes over a loopback socket with finite timeouts and preserve boundaries | `writesResponsesOverLoopback` |

The writer validates a private header snapshot, normalizes framing fields,
then updates the caller's response Headers and writes the head. Failed input
validation leaves both headers and output unchanged. I/O failure can leave a
partial head and normalized headers; the future exchange must mark response
commit before attempting output and close the connection on failure. Unknown
valid status codes use an empty reason phrase. HEAD/304 positive lengths are
representation metadata; 1xx/204/205 force the status-specific no-body rules.
Date is generated unless supplied. Native 0.5.12 lacks SimpleDateFormat,
Locale.US and TimeZone; an initial Native link established those APIs are
unavailable. Date generation therefore uses a small pure UTC calendar formatter,
bounded to four-digit years and checked against independent epoch/date vectors.
No dependency or platform-specific native calls were added.
This slice returns a framing decision;
it does not construct body streams, dispatch requests, manage connection reuse,
send 100 Continue automatically, or provide tunnels/upgrades. Successful CONNECT
and 101 upgrades remain unsupported and are rejected before output. HTTP/1.0
close-delimited output will be connected to the exchange in the server slice.
Concrete server/context/exchange behavior is now wired into a real request-to-
response path, with port-zero binding, context dispatch, stream selection and
terminal shutdown tested over sockets. Default factories return working servers.

Implement `HttpServer` creation/binding, contexts, executor selection, start,
address lookup, and stop, backed by the framing engine and concrete exchanges.
Provide read-only request header maps, including map views and Java Map default
mutation methods; keep response headers writable. Cover this using real exchanges.
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
Include host-JDK symbol resolution in that audit: Scala 2 resolves the newer
final `BasicAuthenticator.realm` field when compiling Native tests on JDK 21,
so direct subclass-mutation coverage for the JDK 8 mutable declaration is deferred.

Review Scala Native module/source placement, Scala-version compatibility,
JUnit conventions, dependency footprint, licensing/provenance, and CI costs with
maintainers. Decide whether to upstream an HTTP subset or continue publishing a
standalone library. Upstream acceptance is not assumed.

## Test discipline

For each class, add a compact behavior/expected-result/test-name checklist before
implementation in this file. Inspect relevant examples in the Scala Native
checkout before designing tests. Tests should exercise public contracts and meaningful protocol
boundaries, not private implementation structure. Reuse Scala Native's direct
JUnit style (`org.junit.Test` and `org.junit.Assert`) without copying upstream
tests. Do not add a separate scenarios layer, forwarding-only wrappers, or a
hand-written runner and manual pass count. Keep minimal test doubles only where
they exercise a real production branch; defer fixture-only coverage until the
corresponding production implementation exists. Abstract declarations do not
justify simulated exchange/context/server storage tests, handler-only assertions,
or trivial authentication-result getter tests. The shared `HeaderExchange`
fixture supplies two real Headers objects to authentication branches and serves
as an inert non-null exchange for filter flow; every other fixture method fails
if invoked. Use real exchanges for integration coverage when available.

Use OS-assigned loopback ports, finite socket timeouts and bounded thread joins,
and cleanup in `finally`. Avoid sleep-based synchronization and shared mutable
test state. Raw sockets are necessary for malformed HTTP, framing, persistence,
and disconnect tests that higher-level clients hide.

### Implemented behavior checklist

| Behavior | Expected result | Test |
| --- | --- | --- |
| Header lookup | Header names compare case-insensitively with canonical ASCII key spelling | `HeadersTest.lookupIsCaseInsensitive` |
| Repeated header values | `add` appends values in insertion order | `HeadersTest.addAppendsValues` |
| Header replacement | `set` replaces all values for a case-insensitive key | `HeadersTest.setReplacesValues` |
| First header value | `getFirst` returns the first value, or null when absent | `HeadersTest.getFirstReturnsFirstValue` |
| Header map operations | `put`, `putAll`, `remove`, and `containsKey` use header-key semantics | `HeadersTest.mapOperationsUseHeaderKeySemantics` |
| Live header views | Key, value, and entry views observe insertion and support contract-preserving mutation/removal | `HeadersTest.mapViewsRemainLive` |
| Header equality/hash | Equal case-insensitive mappings compare equal and have equal hashes | `HeadersTest.equalityAndHashCodeFollowMapContract` |
| Principal identity | Username and realm are retained by their accessors | `HttpPrincipalTest.identityFieldsAreRetained` |
| Principal rendering | Name and string form render as `realm:username` | `HttpPrincipalTest.nameAndToStringUseRealmThenUsername` |
| Empty and Unicode principals | Empty strings and non-ASCII values round-trip unchanged | `HttpPrincipalTest.emptyAndNonAsciiValuesRoundTrip` |
| Principal equality identity | Equality uses username and realm, not an ambiguous rendered name | `HttpPrincipalTest.equalityUsesUsernameAndRealmRatherThanRenderedName` |
| Principal equality rejection | Null and unrelated objects are rejected while equal principals compare equal | `HttpPrincipalTest.equalityRejectsNullAndUnrelatedValues` |
| Principal hash consistency | Equal principals have equal hashes, including Unicode values | `HttpPrincipalTest.equalPrincipalsHaveEqualHashes` |
| Principal hash formula | Ordinary principal values follow the documented username-plus-realm formula | `HttpPrincipalTest.hashCodeFollowsDocumentedUsernameRealmFormula` |
| Inherited principal equality | Subclasses retain constructor-based equality and hash behavior | `HttpPrincipalTest.inheritedEqualityUsesConstructorIdentity` |
| Virtual principal string form | `toString` delegates to an overridden `getName` | `HttpPrincipalTest.toStringDelegatesToOverriddenGetName` |
| Principal null validation | Null username or realm is rejected with `NullPointerException` | `HttpPrincipalTest.nullArgumentsAreRejected` |
| Filter order | Filters run in list order around the handler | `FilterTest.filtersRunInOrderAroundHandler` |
| Filter/handler failure propagation | Filter and handler `IOException` values escape unchanged | `FilterTest.filterAndHandlerExceptionsPropagate` |
| Empty filter chain | An empty chain invokes its handler | `FilterTest.emptyFilterChainInvokesHandler` |
| Filter short circuit | A filter may stop the chain without invoking later filters or the handler | `FilterTest.filterMayShortCircuitWithoutCallingDownstream` |
| Nonempty-chain handler failure | A handler failure through a nonempty chain remains the same exception and unwinds normally | `FilterTest.nonEmptyChainPropagatesHandlerIOExceptionUnchanged` |
| No Authorization | Retry 401 with Basic realm challenge; no credential callback | `BasicAuthenticatorTest.missingCredentialsRequestRetryWithChallenge` |
| Unsupported scheme or absent payload separator | Failure 401; callback not invoked | `BasicAuthenticatorTest.unsupportedOrIncompleteSchemeDoesNotCheckCredentials` |
| Valid credentials | Callback receives username/password in order, Success contains the matching principal; response headers untouched | `BasicAuthenticatorTest.validCredentialsProducePrincipal` |
| Invalid credentials | Failure 401 and challenge; callback sees supplied credentials | `BasicAuthenticatorTest.invalidCredentialsProduceFailureWithChallenge` |
| First colon and empty components | Password retains later colons; empty username/password reach the callback; padded and unpadded tokens work | `BasicAuthenticatorTest.firstColonSeparatesUsernameFromPassword` |
| Platform character encoding | Credentials encoded in the default charset decode to the expected non-ASCII strings | `BasicAuthenticatorTest.credentialsUseDefaultCharset` |
| Repeated requests | Each call rechecks credentials; no success/failure cache | `BasicAuthenticatorTest.credentialsAreCheckedForEveryExchange` |
| Callback exception | Same exception propagates, including IllegalArgumentException; no synthesized response | `BasicAuthenticatorTest.credentialCheckerExceptionPropagates` |
| Header selection | First Authorization value is used, with case-insensitive header lookup | `BasicAuthenticatorTest.firstAuthorizationValueIsUsed` |
| Null realm | NPE at construction | `BasicAuthenticatorTest.nullRealmIsRejected` |
| Empty realm | NPE as documented by JDK 8; differs from JDK 21 IAE | `BasicAuthenticatorBaselineTest.emptyRealmIsRejectedWithNullPointerException` |
| Baseline challenge | Exact `Basic realm="users"`, with no newer charset parameter | `BasicAuthenticatorBaselineTest.challengeUsesJdk8Form` |
| Scheme case/spacing | Case-insensitive Basic and one or more ASCII spaces accepted (RFC 7617/HTTP authentication grammar) | `BasicAuthenticatorBaselineTest.schemeIsCaseInsensitiveAndAllowsMultipleSpaces` |
| Malformed credential encoding | Failure 401 without invoking the callback; decoder errors and missing colons do not escape | `BasicAuthenticatorBaselineTest.malformedCredentialsFailWithoutCheckingCredentials` |
| Null header keys/values (Native-only) | Documented JDK 8 null mappings and list elements are accepted | `HeadersNullContractTest.nullKeysAndValuesAreAccepted` |
| Null filter exchange (Native-only) | NPE occurs before chain advancement or handler invocation; a later valid call still works | `FilterNullContractTest.nullExchangeDoesNotAdvanceChain` |
| Native socket binding | Loopback binding gets a positive OS-assigned port; close marks the socket closed | `NativeSocketSmokeTest.bindsLoopbackSocketAndReleasesIt` |

## Progress

- [x] Bootstrap builds and Native smoke test passes (1 test, 0 failures).
- [x] Git repository initialized at `/home/sake/projects/sake92/scalanative-jdk-sun-httpserver`.
- [x] Headers and shared JVM/Native contract tests.
- [x] HttpPrincipal and shared JVM/Native contract tests.
- [x] Authenticator and its result hierarchy.
- [x] BasicAuthenticator and shared JVM/Native authentication contract tests.
- [x] Handler/exchange/context/filter API and permanent abstract `HttpServer` instance contract.
- [x] Request-line/header parsing and request-framing validation.
- [x] Fixed-length request input.
- [x] Chunked request input.
- [x] Fixed-length response output.
- [x] Chunked response output.
- [x] No-body response output via the zero-length fixed-length writer.
- [x] Response-head serialization, validation and framing selection.
- [x] Initial blocking server, contexts, real exchanges and socket integration tests.
- [x] HTTP persistence, pipelining and bounded unread-body draining.
- [ ] Lifecycle/compatibility audit and HTTP-only provider-discovery feasibility (next).
- [ ] Compatibility audit and upstream discussion.

The shared header, principal, filter, and authentication JUnit contracts run as 32
tests on the JVM reference project and, together with the seven Native-only
baseline/socket tests, 12 request-head parser tests, 11 fixed-length input
tests, 14 chunked input tests, 12 fixed-length/no-body output tests, and 9 chunked
output tests, 11 response-head writer tests and 19 server integration tests,
as 127 tests on Native.
Internal engine tests use the engine package to access package-scoped code;
they stay in the Native test tree and are not shared with the JDK reference.
Run `testFull` (and `+testFull`
for all configured Scala versions) when validating the complete matrix.


## Reference observations and latest validation

The independent black-box probes used Temurin JDK 21.0.10 with default charset
UTF-8. No OpenJDK implementation or test code was inspected. No JDK 8 runtime
was available; these runtime observations do not establish JDK 8 behavior.

- JDK 21 Headers rejected `getFirst(null)`, `add(null, ...)`, `set(null, ...)`,
  and `put(null, null)` with NPE, despite the documented JDK 8 null baseline.
- JDK 21 BasicAuthenticator returned Retry 401 for missing credentials and
  Failure 401 for rejected credentials, both with
  `Basic realm="users", charset="UTF-8"`. Unsupported or lowercase schemes
  returned Failure 401 without a challenge. Invalid Base64 threw IAE; decoded
  text without a colon threw StringIndexOutOfBoundsException. Empty realm
  construction threw IAE. Empty credential components and further password
  colons reached the credential checker.
- JDK 21 Filter.Chain accepted null exchanges; Native follows the documented
  JDK 8 NPE instead. Shared filter tests use non-null exchanges.

Primary references for these decisions:

- https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/Headers.html
- https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpPrincipal.html
- https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/BasicAuthenticator.html
- https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/Filter.Chain.html
- https://docs.oracle.com/en/java/javase/25/docs/api/jdk.httpserver/com/sun/net/httpserver/BasicAuthenticator.html
- https://www.rfc-editor.org/rfc/rfc7617.html
- https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpExchange.html#getResponseBody--
- https://docs.oracle.com/javase/8/docs/api/java/io/OutputStream.html
- https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpServer.html
- https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpContext.html

Latest implementation validation (2026-11-05, Linux, Temurin JDK 21.0.10):

| Target | Command | Result on Scala 2.12.21, 2.13.18, and 3.9.0 |
| --- | --- | --- |
| Native persistence slice | `sbt -batch +testFull` | 127 tests passed per version, zero failures |
| Native server integration slice (previous) | `sbt -batch +testFull` | 119 tests passed per version, zero failures |
| Native response-head writer slice (previous) | `sbt -batch +testFull` | 108 tests passed per version, zero failures |
| Native no-body output slice (previous) | `sbt -batch +testFull` | 97 tests passed per version, zero failures |
| Native chunked output slice (previous) | `sbt -batch +testFull` | 95 tests passed per version, zero failures |
| Native fixed-length output slice (previous) | `sbt -batch +testFull` | 86 tests passed per version, zero failures |
| Native chunked input slice (previous) | `sbt -batch +testFull` | 76 tests passed per version, zero failures |
| Native fixed-length input slice (previous) | `sbt -batch +testFull` | 62 tests passed per version, zero failures |
| Native request-head slice (previous) | `sbt -batch +testFull` | 51 tests passed per version, zero failures |
| JVM reference (previous server slice) | `sbt -batch +testFull` in `jvm-reference/` | 32 shared tests passed per version, zero failures |
| Refined shared tests (previous API slice) | Cross-version `+testOnly` for FilterTest and BasicAuthenticatorTest in `jvm-reference/` | 5 filter and 10 authentication tests passed per version after subsequent test refinements |

The persistence slice adds 8 Native-only tests, bringing the server suite to 19.
Focused server tests passed, then the final `sbt -batch +testFull` passed all
127 Native tests on each configured Scala version. Tests exercise sequential
reuse, pipelining of mixed framing/HEAD responses, close-token precedence,
HTTP/1.0 keep-alive and close delimiting, fresh exchange state, stale stream
handles, exact/over-limit drains, malformed/truncated bodies, early Expect
rejection, slow trickling peers, output failures and stop during persistent work.
No public API signatures or shared JVM-reference tests changed; the reference
matrix was not rerun for this internal-engine slice.

Regression evidence: the slow-trickle test initially timed out at the client
while data kept arriving every 100 ms. Native BufferedInputStream's bulk read
can perform multiple blocking refills before returning, hiding the absolute
deadline. Drain reads now stop at currently available bytes (or one blocking
byte), so the deadline is rechecked between refills. Tightening the same test to
hold the handler alive after response close exposed delayed transport cleanup;
non-reusable response completion now closes its socket immediately. The final
cross-version matrix includes both checks. No dependencies or adapted sources
were added. `git diff --check` passed; existing linker warnings remained non-failing.

The previous server integration slice added 11 Native-only tests. Focused
`testOnly org.scalanative.testsuite.httpserver.HttpServerTest` passed all 11 on
Scala 3.9.0. `sbt -batch +testFull` passed all 119 Native tests on each configured
Scala version, and `sbt -batch +testFull` in `jvm-reference/` reran all 32 shared
contracts successfully on each version. The JVM matrix does not include the new
server tests, so this is Native server proof plus unchanged shared API reference
coverage, not a full server comparison with the JDK.

Regression evidence: the real immutable-header test initially accepted
`entrySet().iterator().next().setValue(...)` through Native's unmodifiable-map
wrapper. `ReadOnlyHeaders` now exposes immutable entry objects as well as
unmodifiable map/list views; the same test passes. Request map default mutation
methods reject even no-op operations. Endpoint tests compare host-address/port
fields: Native equality reported identically rendered local endpoints unequal,
while those address fields matched. No production address workaround was added.

Factories, literal context routing, filters/stream wrapping, Basic authentication,
lazy 100 Continue, fixed/chunked/no-body output, malformed requests, handler
failures, over/under-writing, executor rejection/ownership and stop during pending
headers/body reads or active/queued handlers are tested over loopback sockets.
At that stage, all responses closed the connection; keep-alive/pipelining and
unread-body draining were not implemented by that slice. No dependencies or adapted sources
were added. `git diff --check` passed; Scala 3 wildcard deprecation and existing
GNU-stack linker warnings remained non-failing.

The previous response-head writer slice added 11 Native-only tests. `sbt -batch testFull`
passed 108 tests on Scala 3.9.0; `sbt -batch +testFull` then passed 108 tests
on each configured Scala version. This includes exact wire framing, method/
status suppression, HTTP/1.0 close-delimited selection, pre-emission rejection
of invalid headers without header-map changes, partial-write failures at every
head byte, UTC date vectors and successive responses over real loopback sockets.
The initial Native link failed on unavailable java.text/locale/timezone APIs;
the final implementation uses a dependency-free UTC formatter and all checks
passed. No shared API or JVM-reference tests changed or were rerun. Request-head
and trailer tests still pass with shared field-value validation. No actual
server/handler dispatch was implemented by that slice. `git diff --check` passed; existing
GNU-stack linker warnings remained non-failing.

The previous no-body output slice reuses `FixedLengthOutputStream(output, 0)` with no
additional production class or runtime behavior change. Two new Native-only
tests prove header-only wire output and rejection of forbidden payload writes;
the existing flush-failure test now covers zero as well as positive lengths.
All 97 tests passed on each configured Scala version with `sbt -batch +testFull`.
Successful close flushes buffered response headers, adds no payload/framing
bytes, and preserves a following response on the same sink. HEAD/status and
public response-length selection are still unimplemented until real exchanges.
No shared API/JVM-reference tests changed or were rerun. `git diff --check`
passed; existing GNU-stack linker warnings remained non-failing.

The previous chunked output slice added 9 Native-only tests. All 95 tests passed on each
configured Scala version with `sbt -batch +testFull`. Exact wire assertions cover
hexadecimal lengths, payload bytes, delimiters, zero-length writes and a single
final chunk. Failure injection covers every emitted byte of chunk framing and
termination, plus explicit/final flush. The existing chunked input decoder
recovers mixed binary writes and leaves following-response bytes intact. Close
flushes without closing the caller-owned sink. No shared API or JVM-reference
tests changed, so JVM reference tests were not rerun for this slice. Existing
GNU-stack linker warnings remained non-failing; `git diff --check` passed.

The previous fixed-length output slice added 10 Native-only tests. All 86 tests passed
on each configured Scala version with `sbt -batch +testFull`. The new cases
cover exact bytes, atomic rejection of excess writes, insufficient output on
close, buffered flush/close, sink ownership, long lengths, invalid arguments,
and injected sink failures before and after partial emission. No headers or
exchange/connection lifecycle behavior is implemented by this slice; the later
exchange must abort the connection on terminal output failures. JVM reference
tests were not rerun because no shared public implementation or tests changed.

The previous chunked input slice added 14 Native-only tests, including chunk/extension
syntax, cumulative extension bounds, bounded trailer syntax validation and
discarding, every fragmented input split, every truncated prefix, and injected
source failures throughout framing and payload reads. All 76 tests passed on
each configured Scala version with `sbt -batch +testFull`. An initial Scala
3.9.0 `testFull` run also passed before the final close-ownership test refinement.
Existing request-head tests pass with field/token syntax shared in `HttpSyntax`.
Trailers never merge into initial headers and no public trailer API is added.

The previous fixed-length input slice added 11 Native-only tests, including preservation
of the next request, short reads/skips, terminal truncation and source failures,
and non-draining close without closing the caller's source. All 62 tests passed
on each configured Scala version with `sbt -batch +testFull`.
The previous request-head slice added 12 Native-only tests; its Scala 3.9.0 full
suite also passed separately with `sbt -batch testFull`. JVM reference tests
were not rerun for these internal-engine slices: no shared API implementations
or shared tests changed, and the JDK exposes no corresponding internal-engine
contract. No additional dependencies or adapted third-party sources were added.
`git diff --check` passed. Existing GNU-stack linker warnings remained non-failing.

Regression evidence: before the filter fix, the Native test failed because null
did not throw. Before the padding guard, the Native malformed-credentials test
failed because `Basic YWxpY2U6c2VjcmV0=` was accepted. `git diff --check` passed.
Existing Native GNU-stack linker warnings and Scala 3 wildcard deprecation
warnings were non-failing.

An incremental Native compile can fail when generated NIR files are symlinks into
sbt's read-only cache. A scoped Native clean followed by `+testFull` resolved this
in both Headers and BasicAuthenticator work. Use `testFull` for full-suite proof:
sbt 2's `test` can skip cached passing tests, even after clean.

Provider discovery, remaining lifecycle/compatibility audit, direct mutable-realm
extension coverage, and macOS/Windows validation remain deferred as described
above. No upstream readiness or acceptance is claimed.
