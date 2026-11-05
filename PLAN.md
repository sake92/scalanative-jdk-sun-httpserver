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
request input and fixed-length response output are implemented. Chunked response
output is next; no-body responses and the concrete server remain deferred.

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
`HttpContext` exposes both types. `HttpServer` provider factories and concrete
socket behavior remain deferred to section 5; no throwing public placeholders
are used. `Authenticator` and `Authenticator.Result` have public constructors,
matching the JDK 8 declarations. `HttpExchange.getResponseCode()` is part of the JDK 8 baseline;
newer `Request` and `AutoCloseable` additions are deferred.

### 4. Blocking HTTP framing and streams

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
following-request bytes. Fixed-length response output is also complete. The next
separately requested slice is chunked response output, followed by no-body output.

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
- [ ] Chunked response output (next) and no-body output.
- [ ] Blocking server and socket integration tests.
- [ ] Compatibility audit and upstream discussion.

The shared header, principal, filter, and authentication JUnit contracts run as 32
tests on the JVM reference project and, together with the seven Native-only
baseline/socket tests, 12 request-head parser tests, 11 fixed-length input
tests, 14 chunked input tests, and 10 fixed-length output tests, as 86 tests on Native.
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

Latest implementation validation (2026-11-05, Linux, Temurin JDK 21.0.10):

| Target | Command | Result on Scala 2.12.21, 2.13.18, and 3.9.0 |
| --- | --- | --- |
| Native fixed-length output slice | `sbt -batch +testFull` | 86 tests passed per version, zero failures |
| Native chunked input slice (previous) | `sbt -batch +testFull` | 76 tests passed per version, zero failures |
| Native fixed-length input slice (previous) | `sbt -batch +testFull` | 62 tests passed per version, zero failures |
| Native request-head slice (previous) | `sbt -batch +testFull` | 51 tests passed per version, zero failures |
| JVM reference (previous API slice) | `sbt -batch +testFull` in `jvm-reference/` | 32 tests passed per version, zero failures |
| Refined shared tests (previous API slice) | Cross-version `+testOnly` for FilterTest and BasicAuthenticatorTest in `jvm-reference/` | 5 filter and 10 authentication tests passed per version after subsequent test refinements |

The fixed-length output slice adds 10 Native-only tests. All 86 tests passed
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

Concrete exchange/authenticator integration, immutable request headers, socket
serving, direct mutable-realm extension coverage, and macOS/Windows validation
remain deferred as described above. No upstream readiness or acceptance is claimed.
