# Control Block

- Outcome: PASS — `Headers` implementation and cross-version contract coverage complete.
- Native terminal: `sbt -batch +testFull` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 9/9 (27/27 total).
- JVM terminal: `(cd jvm-reference && sbt -batch +run)` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 7/7 (21/21 total).
- Cross artifacts: Scala Native 0.5.12 compiler artifacts for all three lines were present and resolved by the builds.
- Review: implementation `src/main/scala/com/sun/net/httpserver/Headers.scala:14-232`; behavior matrix `Lunacy/runs/headers/phases/headers/BEHAVIORS.md`.
- Review: Native/JVM shared scenarios `src/test/scala/org/scalanative/testsuite/httpserver/HeadersContractScenarios.scala:6-103`; null baseline `HeadersNullContractTest.scala:7-22`.
- Review: isolated reference build `jvm-reference/build.sbt:1-17` and runner `jvm-reference/src/main/scala/org/scalanative/testsuite/httpserver/HeadersReferenceMain.scala:1-15`.

## Implementation

`Headers` implements the Java `Map[String, List[String]]` surface with ASCII
case-insensitive canonical keys, `add`, `set`, and `getFirst`, retaining null
keys/values for the JDK 8 baseline. Private live key, value, and entry views
preserve mutation, entry `setValue`, iterator removal, equality, and hashing.
Shared scenarios run against the JDK class without loading Native production
classes. README, PLAN, and both builds document Scala 2.12.21/2.13.18/3.9.0.

## Limitations and evidence

- Current OpenJDK 21.0.10 rejects several null header operations; this is
  recorded separately in `JVM-OBSERVATION.md` and is not treated as the JDK 8
  contract. JDK 8 API reference: Oracle `Headers.html`.
- The existing `.scalafmt.conf` uses `preset = common`, which installed
  Scalafmt 3.11.5 reports as unknown; formatting was inspected manually and
  the project configuration was not changed.
- Native linking emits the existing Scala Native `SafepointPollTrampoline` GNU-stack warning; all test processes exit successfully.
- HTTPS, newer convenience APIs, exchange/server types, and Scala Native
  portability beyond these Linux runs remain out of scope.
