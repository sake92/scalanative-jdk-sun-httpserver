# External HTTP/1.1 results — 2026-11-06

Local Linux results: Scala Native 0.5.12 / Scala 3.9.0; reference fixture
Scala 2.13.18 on Temurin 8u482 and 21.0.10.
[Commands and pinned tools](README.md) · [Protocol policy](../COMPATIBILITY.md)

External measurements predate the timeout, admission and body-size limits; current local
regression results are listed separately below.

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

## Native hardening regressions

| Local Native check | Result |
| --- | --- |
| `sbt +testFull`, Scala 2.12.21 / 2.13.18 / 3.9.0 | 175/175 per version |

- Six header tests cover stalled/dripping heads, recovery, body-read isolation, fresh budgets and configuration; five failed before implementation.
- Two acceptance tests send valid heads in four fragments, 300 ms apart, within a two-second budget; synthetic clients only. Minimum rates remain disabled ([rationale](../ARCHITECTURE.md#why-there-is-no-minimum-header-ingress-rate)).
- Ten body/write tests cover stalls, recovery, streaming, disabled limits, settings, watchdog cleanup and buffered responses after drain failure; five behavior cases failed before implementation, and the drain case caught an intermediate regression.
- Seven admission tests cover queued/active/idle caps, pending-slot release at task start, rejection and head-failure recovery, disabled limits and construction-time settings; three failed before implementation.
- Thirteen body-size tests cover declared/chunked limits, read/skip/drain paths, exact-limit pipelines, Expect/auth ordering, caught errors, blocking custom cleanup, disabled limits, settings and overflow; seven failed before implementation, and custom cleanup caught an intermediate regression.

Host-JDK and external suites were not rerun for the timeout, admission or body-size changes.

[OWASP coverage review](../COMPATIBILITY.md#denial-of-service-review): residual worker starvation, unbounded connection/queue lifetimes, application work and deployment-level traffic controls remain explicit limits.

Raw logs, resource samples and tool reports stay untracked in
`target/conformance-results/` and may be removed during cleanup.
