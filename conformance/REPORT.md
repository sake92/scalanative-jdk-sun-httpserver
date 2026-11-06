# External HTTP/1.1 results — 2026-11-06

Local Linux results: Scala Native 0.5.12 / Scala 3.9.0; reference fixture
Scala 2.13.18 on Temurin 8u482 and 21.0.10.
[Commands and pinned tools](README.md) · [Protocol policy](../COMPATIBILITY.md)

Protocol measurements predate the timeout, admission and body-size limits.
The slow-client baseline was refreshed with those changes; all results are local.

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

| Target | Socket cases completed | slowhttptest 1.9.0 completed | 32-second drip, both executors |
| --- | --- | --- | --- |
| Native | 20/20 | 6/6 | Closed; first observed at 30.12–30.15 s |
| JDK 8u482 | 19/20 | 6/6 | Survived |
| JDK 21.0.10 | 19/20 | 6/6 | Survived |

- Library `f6e7ffb`, rebuilt fixtures and updated runner; [snapshot, source/binary hashes and raw-report paths](results/2026-11-06-slowclients-refresh.json).
- Ordinary requests still failed under Native pressure; recovery after disconnect was within 103 ms, and all Native fixtures exited within 207 ms of stop control.
- Both JDK bounded-trailer cases closed early without reaching worker/queue saturation; setup failures remain counted and return nonzero.
- Default and two-worker/two-queue executors; four connections, three-second socket/five-second tool windows, except the 32-second drip. Concurrent local comparisons are not benchmarks, sustained-load checks or DoS-resilience evidence.

## Runner checks

Earlier checks covered startup failure, timeout, invalid arguments, output preservation
and TERM cleanup; no test processes remained. Earlier Native h1spec rerun: 33/33.
An earlier fixture TERM run hit a Native `PosixThread.park` assertion; investigation remains in [TODO.md](../TODO.md).

Refreshed runner: Native `close` and JDK `survive` expectations matched; both reversed expectations returned nonzero, and Native default `observe` completed. Help, invalid values, non-idle misuse and existing-output rejection checked; no fixture/tool processes remained.

## Native hardening regressions

| Local Native check | Result |
| --- | --- |
| `sbt +testFull`, Scala 2.12.21 / 2.13.18 / 3.9.0 | 175/175 per version |

- Six header tests cover stalled/dripping heads, recovery, body-read isolation, fresh budgets and configuration; five failed before implementation.
- Two acceptance tests send valid heads in four fragments, 300 ms apart, within a two-second budget; synthetic clients only. Minimum rates remain disabled ([rationale](../ARCHITECTURE.md#why-there-is-no-minimum-header-ingress-rate)).
- Ten body/write tests cover stalls, recovery, streaming, disabled limits, settings, watchdog cleanup and buffered responses after drain failure; five behavior cases failed before implementation, and the drain case caught an intermediate regression.
- Seven admission tests cover queued/active/idle caps, pending-slot release at task start, rejection and head-failure recovery, disabled limits and construction-time settings; three failed before implementation.
- Thirteen body-size tests cover declared/chunked limits, read/skip/drain paths, exact-limit pipelines, Expect/auth ordering, caught errors, blocking custom cleanup, disabled limits, settings and overflow; seven failed before implementation, and custom cleanup caught an intermediate regression.

Host-JDK contract tests and external protocol suites were not rerun for these changes; slow-client results above were refreshed.

[OWASP coverage review](../COMPATIBILITY.md#denial-of-service-review): residual worker starvation, unbounded connection/queue lifetimes, application work and deployment-level traffic controls remain explicit limits.

Raw logs, resource samples and tool reports stay untracked in
`target/conformance-results/` and may be removed during cleanup.
