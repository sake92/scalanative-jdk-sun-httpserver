# External HTTP/1.1 results — 2026-11-06

Local Linux evidence only; no remote CI or upstream acceptance implied.
[Commands and pinned tools](README.md) · [Machine-readable snapshot](results/2026-11-06.json) · [Protocol policy](../COMPATIBILITY.md) · [Planned work](../TODO.md)

## Environment and scope

- Native: Scala Native 0.5.12, Scala 3.9.0; starting revision `eb0a942`, final results include the two fixes below.
- Reference: shared application compiled with Scala 2.13.18, running on Temurin 8u482 and 21.0.10 without this implementation.
- Http11Probe: 196 executed cases per target; 69 Compliance, 87 Smuggling, 26 MalformedInput, 5 Normalization, 9 Capabilities.
- Scoring: 154 scored and 42 informational cases; Cookies and WebSockets excluded; no execution errors or unscored failures.

## Results

| Target | h1spec | Http11Probe scored pass | Scored warn | Scored fail |
| --- | --- | --- | --- | --- |
| Native before fixes | 33/33 | 137 | 14 | 3 |
| Native after fixes | 33/33 | 139 | 14 | 1 |
| JDK 8u482 | 26/33 | 97 | 19 | 38 |
| JDK 21.0.10 | 25/33 | 97 | 19 | 38 |

- Native Smuggling: 62 scored passes, 5 scored warnings, 8 informational passes, 12 informational warnings; no failures before or after fixes.
- Warnings include accepted leading zeros and whitespace around Content-Length; they remain warnings, not passes.
- These totals describe the selected probes, not complete conformance, deployment-chain smuggling resistance or DoS resilience.

## Confirmed fixes

- `MAL-LONG-METHOD`: oversized methods now receive 501 instead of 414; oversized targets retain 414; reproduced by a 9 KB parser regression and the external 100 KB case ([RFC 9112 §3](https://www.rfc-editor.org/rfc/rfc9112.html#section-3)).
- `COMP-OPTIONS-STAR`: `OPTIONS *` now receives an empty 200 and closes without invoking a resource handler; real-socket regression covers the former 404, also observed on both reference JDKs ([RFC 9110 §9.3.7](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.7)).

## Disputed expectation

- `COMP-HOST-EMPTY-VALUE`: suite expects rejection; Native and both JDKs return 200 using the bound endpoint as default authority, permitted by [RFC 9112 §3.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.3); missing, repeated and invalid Host fields remain rejected.
- The empty-Host verdict is preserved as a failure, so the full core run returns nonzero.

## Qualifications and runner checks

- h1spec reads each response once; a separate complete-response reader verified exact fixed-length/chunked echoes on all targets, identifying some JDK body failures as reader artifacts without changing raw totals.
- Other JDK failures include accepted malformed headers, unsupported version spellings and missing or duplicate Host fields.
- Http11Probe exits zero for failed verdicts; the runner also checks JSON, including unscored failures, and preserves warnings and disputed results.
- Range requests, ETags and compression describe application capabilities absent from the fixture, not necessarily transport defects.
- Scala runner: h1spec 33/33 and all five Http11Probe category summaries reproduced.
- Runner lifecycle: startup failure, probe timeout and TERM cancellation returned nonzero and released owned processes; cancellation left no server/probe processes.
- Runner arguments: quoted executable paths, quoted JDK classpath, unknown options and existing-output rejection checked.
- Runtime observation: Scala Native 0.5.12 `PosixThread.park` assertion during TERM shutdown while the fixture main thread waits on a latch; protocol probes had completed and process-group cleanup released workers.

## Raw evidence locations

Build artifacts under `target/conformance-results/` are untracked and may be absent after cleanup; interrupted runs may contain partial reports.

| Directory pattern | Evidence |
| --- | --- |
| `native-h1spec`, `native-http11probe` | Baseline |
| `native-http11probe-after` | Method-length fix |
| `native-h1spec-final`, `native-http11probe-final` | Both fixes |
| `jdk-8.0.482-tem-*`, `jdk-21.0.10-tem-*` | Reference runs |
| `scala-runner-*` | Scala runner parity and lifecycle checks |
