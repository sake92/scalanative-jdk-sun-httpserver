# External HTTP/1.1 results — 2026-11-06

These are local Linux results using the shared `ConformanceServer` application.
Native uses Scala Native 0.5.12 and Scala 3.9.0. The reference application uses
Scala 2.13.18, run on Temurin 8u482 and Temurin 21.0.10. Its classpath contains
the shared application and Scala library, not this HTTP implementation.

The starting revision was `eb0a942`; the final Native results include the two
protocol fixes described below. No remote CI or upstream acceptance is implied.
[Run instructions](README.md) describe the exact application and category scope.

## Pinned tools

| Tool | Revision / runtime |
| --- | --- |
| [uNetworking/h1spec](https://github.com/uNetworking/h1spec) | `f0a5650a20c575fbea0f7179a3a9cfa50f20ba6e`; Deno 2.5.6 |
| [Http11Probe](https://github.com/MDA2AV/Http11Probe) | `d50d818a0e008e9feb3bb9860ad5d9cc40342bc3`; .NET SDK 10.0.100 |

## Results

Http11Probe executed 196 cases per target: 69 Compliance, 87 Smuggling,
26 MalformedInput, 5 Normalization and 9 Capabilities. Of these, 154 are scored
and 42 are informational. Cookies and WebSockets were excluded.

| Target | h1spec | Http11Probe scored pass | Scored warn | Scored fail |
| --- | --- | --- | --- | --- |
| Native before fixes | 33/33 | 137 | 14 | 3 |
| Native after fixes | 33/33 | 139 | 14 | 1 |
| JDK 8u482 | 26/33 | 97 | 19 | 38 |
| JDK 21.0.10 | 25/33 | 97 | 19 | 38 |

Warnings are distinct from passes and failures. Unscored cases can also have
warnings; they are not included in the scored columns above. There were no
Http11Probe execution errors or unscored failures. Neither suite establishes
complete conformance, and the reference totals are diagnostic observations,
not a claim that every suite expectation is authoritative.

All 87 Smuggling cases completed without a Native failure, before and after
the fixes. This category includes 67 scored cases: 62 passed and 5 warned.
The remaining 20 informational cases had 8 passes and 12 warnings. Warnings
include accepted leading zeros or whitespace around Content-Length; the suite
allows these alternatives. This is bounded evidence, not proof that no request
smuggling vulnerability exists.

## Confirmed defects fixed

### Oversized methods received the URI-too-long status

`MAL-LONG-METHOD` sends a 100 KB method token. The parser originally applied 414
to any request-line overflow, even before reaching the request target.
[RFC 9112 section 3](https://www.rfc-editor.org/rfc/rfc9112.html#section-3)
recommends 501 for a method longer than the server implements and requires 414
for a request target that exceeds its accepted length.

The parser now returns 501 when the method fills the request-line limit, while
retaining 414 for oversized targets. A direct parser regression reproduced
`expected 501, got 414` before the fix. The external 100 KB case then passed.
The local regression uses 9 KB to stay below the Native JUnit reporter's
exception-message size limit while still exceeding the parser's 8 KiB limit.

### Server-wide OPTIONS fell through resource routing

`COMP-OPTIONS-STAR` sends `OPTIONS *`. Parsing accepted this form, but routing
could not match `*` to a registered path prefix and returned 404. Both reference
JDKs also returned 404.

[RFC 9110 section 9.3.7](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.7)
defines this as a request about the server as a whole. The server now answers
with an empty 200 response without selecting a resource context. It closes the
connection after this response, so an unread OPTIONS body cannot be interpreted
as another request. A real-socket regression reproduced the previous 404 and
now verifies status 200, zero content length, an empty body and no resource
handler invocation.

## Remaining suite disagreement: empty Host

`COMP-HOST-EMPTY-VALUE` sends `GET / HTTP/1.1` with an empty Host value and expects
400 or connection closure. Native and both JDKs instead return 200 for the test
application's root handler.

The expectation is stricter than
[RFC 9112 section 3.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.3),
which permits rejecting an empty authority **or** applying a configured default
consistent with the incoming connection. This server has a single bound endpoint
and path-based contexts, with no Host-based virtual-server selection. It treats
that bound server as the default. Empty Host therefore remains accepted, while
missing, repeated and syntactically invalid Host fields are rejected.

This is recorded as a disputed suite expectation, not silently suppressed or
counted as a passing probe. The full core run intentionally returns nonzero
while that original verdict remains.

## Limitations of the probes

h1spec checks only one read of each response. In the reference runs, some body
assertions failed even with a 200 response: fixed-length echo on both JDKs, and
chunked echo on JDK 21. A separate check using Python's complete HTTP response
reader verified exact fixed-length and chunked echoes on all three targets.
Those body failures are reader artifacts; the raw h1spec totals above remain
unchanged. Other JDK failures include accepted malformed headers, unsupported
version spellings and missing or duplicate Host fields.

Http11Probe's CLI exits zero even when its report contains failed verdicts.
The checked-in runner checks its JSON as well as the process exit code. It
preserves warnings, disputed failures and unscored verdicts without changing
the external tests. Application capabilities such as range requests, ETags and
compression are informational here; the test application does not implement
them.

## Evidence and follow-up

[The machine-readable snapshot](results/2026-11-06.json) retains per-category
summaries, failed case IDs and the complete-response echo check. Raw local
reports, logs and invocation metadata are under `target/conformance-results/`:

- `native-h1spec`, `native-http11probe`: baseline.
- `native-http11probe-after`: after the method-length fix.
- `native-h1spec-final`, `native-http11probe-final`: after both fixes.
- `jdk-8.0.482-tem-*`, `jdk-21.0.10-tem-*`: reference runs.

The runner was also exercised with a server that exits before readiness and a
probe forced to time out; both returned nonzero and released owned processes.
Raw files under `target` are build artifacts and are not committed.

The runner was subsequently migrated to Scala with `mainargs`, retaining the
same suite revisions and command options. The Scala run passed h1spec 33/33 and
matched all five Http11Probe category summaries above. Validation also covered
quoted executable paths with spaces, a quoted JDK classpath, unknown options,
existing-output rejection, startup failure, probe timeout and TERM cancellation.
No server or probe processes remained after cancellation. These runs are under
`target/conformance-results/scala-runner-*`.

Both the original and Scala-runner server logs record a Scala Native 0.5.12
`PosixThread.park` assertion during TERM shutdown while the test application's
main thread waits on a latch. This is a separate runtime shutdown observation;
the protocol probes had completed, and process-group cleanup released the
server and workers. Interrupted runs can leave partial reports.

Protocol decisions here follow [this repository's compatibility policy](../COMPATIBILITY.md).
Scala Native's [Java library documentation](https://www.scala-native.org/en/latest/lib/javalib.html)
aims for JVM-like behavior while documenting differences. Its
[contribution guide](https://www.scala-native.org/en/latest/contrib/contributing.html)
does not define a blanket JDK-bugs-versus-RFC precedence rule. Upstream review
would need the rationale and tests for each deliberate difference.

The useful next steps are review of the empty-Host verdict with the external
suite, broader RFC coverage, and a repeatable CI baseline. Load testing and
macOS/Windows validation remain separate work.
