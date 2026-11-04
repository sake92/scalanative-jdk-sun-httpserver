# Control Block

- Outcome: PASS — removed fixture-only and trivial API tests, retained five real `Filter.Chain` control-flow scenarios, and preserved production code unchanged.
- Native terminal: `XDG_RUNTIME_DIR=/tmp/sbt-runtime sbt -batch +testFull` after scoped `+clean` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 24/24 (72/72 total).
- JVM terminal: `XDG_RUNTIME_DIR=/tmp/sbt-runtime sbt -batch +run` from `jvm-reference/` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 22/22 (66/66 total).
- Logs: `target/exchange-api-evidence/native-cleanup3-terminal.log`; `jvm-reference/target/exchange-api-evidence/jvm-cleanup3-terminal.log`.
- Review: retained production-chain scenarios are `ExchangeContractScenarios.scala:7-86`; five JUnit wrappers are `ExchangeContractTest.scala:5-20`; JVM registrations/count are `HeadersReferenceMain.scala:22-27`.
- Checklist/docs: `BEHAVIORS.md:3-14`, `STEPS.md:20`, and `README.md:29-34` accurately describe the reduced coverage and deferred exchange integration.
- `git diff --check` passed. Existing Native GNU-stack linker warnings remain. `REPORT-1.md` and `REPORT-2.md` were not modified.

## Cleanup

Removed exchange/context/server fixture classes and their storage/getter,
stream, attribute, authenticator, handler-only, and server-contract tests.
Removed their obsolete imports and JVM invocations. The remaining scenarios
pass opaque `null` exchanges because exchange behavior is irrelevant to chain
control flow; concrete exchange/context/authentication/server semantics are
deferred until production implementations exist.
