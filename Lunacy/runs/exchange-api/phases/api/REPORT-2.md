# Control Block

- Outcome: PASS — focused `Filter.Chain` regressions and fixture/documentation clarification complete; `REPORT-1.md` unchanged.
- Native terminal: `XDG_RUNTIME_DIR=/tmp/sbt-runtime sbt -batch +testOnly org.scalanative.testsuite.httpserver.ExchangeContractTest` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 14/14 (42/42 total).
- JVM terminal: `XDG_RUNTIME_DIR=/tmp/sbt-runtime sbt -batch +run` from `jvm-reference/` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 31/31 (93/93 total).
- Logs: `target/exchange-api-evidence/native-repair2-terminal.log`; `jvm-reference/target/exchange-api-evidence/jvm-repair2-terminal.log`.
- Review: new scenarios are `ExchangeContractScenarios.scala:201-246`; wrappers are `ExchangeContractTest.scala:37-45`; JVM runner count is `HeadersReferenceMain.scala:30-35`.
- Checklist/authority: `BEHAVIORS.md:18-19` and `STEPS.md:7,14-18`; `README.md:28-34` clarifies fixture proof versus deferred real-exchange semantics.
- `git diff --check` passed. Existing Native GNU-stack linker warnings remain. No production API code changed.

## Repair

`filterMayShortCircuitWithoutCallingDownstream` proves a filter can omit
`Chain.doFilter`, leaving later filters and the handler uncalled. The new
nonempty-chain scenario uses a preceding filter and verifies the exact handler
`IOException` instance propagates unchanged; its post-handler event is absent,
confirming normal unwinding is not substituted for the failure.
