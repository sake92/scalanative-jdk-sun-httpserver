# Final terminal evidence note

- No complete stdout log was retained for the final `sbt -batch +testFull` or
  `(cd jvm-reference && sbt -batch +run)` invocations; their result lines were
  visible in the terminal session only.
- Native durable result artifacts are the final XML reports:
  `/home/sake/projects/sake92/scalanative-jdk-sun-httpserver/target/out/native0.5/scala-2.12.21/scalanative-httpserver/test-reports/TEST-org.scalanative.testsuite.httpserver.HeadersContractTest.xml`,
  `/home/sake/projects/sake92/scalanative-jdk-sun-httpserver/target/out/native0.5/scala-2.13.18/scalanative-httpserver/test-reports/TEST-org.scalanative.testsuite.httpserver.HeadersContractTest.xml`,
  and `/home/sake/projects/sake92/scalanative-jdk-sun-httpserver/target/out/native0.5/scala-3.9.0/scalanative-httpserver/test-reports/TEST-org.scalanative.testsuite.httpserver.HeadersContractTest.xml`.
- Each Native XML records `tests="7" errors="0" failures="0"`; the sibling
  null-contract and socket reports in each version directory record 1/0/0,
  yielding the terminal 9/9 per Scala line.
- The retained JVM sbt global log
  `/home/sake/projects/sake92/scalanative-jdk-sun-httpserver/jvm-reference/target/global-logging/sbt-global-log7371325337960426373.log`
  is only a partial orchestration record (it shows `+run` and 2.12.21/2.13.18
  markers); it does not retain the child `Passed: Total 7` lines or a complete
  three-version run. No complete JVM result log path exists.
- `+testFull` was used for the final Native gate because it executes the full
  native suite per `crossScalaVersions`; `+test` was not treated as equivalent
  full-suite evidence. The JVM `+run` matrix independently runs the seven
  shared scenarios per Scala line.
