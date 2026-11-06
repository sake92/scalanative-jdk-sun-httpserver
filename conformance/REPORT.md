# External HTTP/1.1 results — 2026-11-06

Local Linux results: Scala Native 0.5.12 / Scala 3.9.0; reference fixture
Scala 2.13.18 on Temurin 8u482 and 21.0.10.
[Commands and pinned tools](README.md) · [Protocol policy](../COMPATIBILITY.md)

## Protocol checks

| Target | h1spec | Http11Probe pass | Warn | Fail |
| --- | --- | --- | --- | --- |
| Native before fixes | 33/33 | 137 | 14 | 3 |
| Native after fixes | 33/33 | 139 | 14 | 1 |
| JDK 8u482 | 26/33 | 97 | 19 | 38 |
| JDK 21.0.10 | 25/33 | 97 | 19 | 38 |

- Fixed oversized methods returning 414 instead of 501 and `OPTIONS *` returning 404 instead of 200; both have regressions.
- Remaining Native failure: Http11Probe rejects empty Host, while Native and both JDKs accept it using the bound endpoint as default authority, permitted by [RFC 9112 §3.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.3). The failure remains counted and the run returns nonzero.
- Http11Probe: 154 scored and 42 informational cases per target; Cookies/WebSockets excluded. Warnings remain warnings; Native smuggling cases had no failures.
- h1spec's single-read response checks caused some JDK body failures; separate complete-response reads succeeded without changing suite totals.

[Protocol snapshot](results/2026-11-06.json). Results cover these probes, not complete conformance or deployment-chain smuggling resistance.

## Slow-client baseline

These measurements predate the absolute header deadline; the external slow-client
baseline has not been rerun with that change.

| Target | Socket cases completed | slowhttptest 1.9.0 runs completed |
| --- | --- | --- |
| Native | 20/20 | 6/6 |
| JDK 8u482 | 19/20 | 6/6 |
| JDK 21.0.10 | 19/20 | 6/6 |

- Native slow clients starved ordinary requests; disconnecting restored service within 104 ms. Shutdown with blocked reads/writes succeeded; all Native fixtures exited within 222 ms of the stop control.
- Headers dripped over 32 seconds survived on all targets. Both JDK bounded-trailer cases failed to sustain the intended pressure state; these are setup failures, not protocol verdicts.
- Default and two-worker/two-queue executors tested separately. Bounded local runs only; concurrent comparisons are not benchmarks or DoS-resilience evidence.

## Runner checks

Startup failure, timeout, invalid arguments, output preservation and TERM cleanup
checked; no test processes remained. Final Native h1spec rerun: 33/33.
An earlier fixture TERM run hit a Native `PosixThread.park` assertion; investigation remains in [TODO.md](../TODO.md).

## Header-deadline regressions

| Local Native check | Result |
| --- | --- |
| `sbt +testFull`, Scala 2.12.21 / 2.13.18 / 3.9.0 | 143/143 per version |

- Six direct JUnit tests cover stalled/dripping initial and subsequent heads, recovery, body reads beyond the header budget, fresh deadlines and configuration; five failed before implementation. Host-JDK and external suites were not rerun for this change.

Raw logs, resource samples and tool reports stay untracked in
`target/conformance-results/` and may be removed during cleanup.
