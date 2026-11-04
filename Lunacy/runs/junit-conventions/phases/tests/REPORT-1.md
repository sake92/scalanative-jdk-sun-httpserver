# Control Block

- Outcome: PASS — shared contract tests now use direct JUnit classes and real production-value assertions; no production source changed.
- Authority: applied `Lunacy/PROJECT_NOTES.md`, the run plan, and Scala Native's direct JUnit conventions from `docs/user/testing.md`, `InetSocketAddressTest.scala`, and Context7 `/scala-native/scala-native`.
- Native terminal: `XDG_RUNTIME_DIR=/tmp/sbt-runtime sbt -batch +testFull` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 24/24 (72/72 total).
- JVM terminal: `(cd jvm-reference && XDG_RUNTIME_DIR=/tmp/sbt-runtime sbt -batch +testFull)` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 22/22 (66/66 total).
- Targeted JUnit checks: Native and JVM `testOnly` for `HeadersTest`, `HttpPrincipalTest`, and `FilterTest` each passed 22/22.
- Logs: `target/junit-conventions-evidence/native-terminal.log`, `target/junit-conventions-evidence/native-targeted-initial.log`, `jvm-reference/target/junit-conventions-evidence/jvm-terminal.log`, and `jvm-reference/target/junit-conventions-evidence/jvm-targeted.log`.
- Review: behavior preservation checklist is `Lunacy/runs/junit-conventions/phases/tests/BEHAVIORS.md`; direct tests are `src/test/scala/org/scalanative/testsuite/httpserver/{HeadersTest,HttpPrincipalTest,FilterTest}.scala`.
- JVM isolation: `jvm-reference/build.sbt` registers only those shared files in `Test / unmanagedSources` and uses JUnit 4.13.2 plus junit-interface 0.13.3; Native-only null/socket tests remain excluded.
- Removed the three `*Scenarios` objects, forwarding wrappers, and `HeadersReferenceMain`; updated `README.md`, `jvm-reference/README.md`, and current `PLAN.md`. Historical Lunacy reports were not modified.
- `git diff --check` passed; existing Scala Native linker GNU-stack warnings remain non-failing infrastructure output.
