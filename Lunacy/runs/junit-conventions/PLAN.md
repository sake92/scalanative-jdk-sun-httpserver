# Align tests with Scala Native conventions

User authority: user rejects separate *Scenarios objects and requires conventions from /home/sake/projects/sake92/scala-native. Prior user correction prohibits fixture-only tests. Use pragmatic-testing and lunacy.

One coherent test-only step: convert all three Scenarios objects plus forwarding JUnit wrappers to direct JUnit classes (HeadersTest, HttpPrincipalTest, FilterTest) with test bodies and org.junit.Assert assertions. Preserve every existing meaningful behavior. Remove Scenarios objects, forwarding wrappers, and custom JVM main/manual counts. Reuse same direct JUnit test sources in isolated JVM reference project's Test configuration, using Scala Native checkout's pinned JUnit 4.13.2 and com.github.sbt junit-interface 0.13.3 as appropriate (verify docs/source compatibility via Context7/primary refs). No production changes or new assertion framework, no copying upstream tests.

Local reference authority: scala-native/docs/user/testing.md; unit-tests/shared/src/test/scala/org/scalanative/testsuite/javalib/net/InetSocketAddressTest.scala; project/Deps.scala; project/Build.scala. Read actual patterns before implementing. Keep Native-only null baseline tests/socket smoke excluded from JVM reference. Test sharing must not load library production classes on JVM.

Update README, jvm-reference/README.md and current root PLAN.md conventions/commands; historical Lunacy reports stay immutable. Document direct JUnit/shared-source expectations to prevent recurrence. No new AGENTS.md needed for this bounded task. Keep cross Scala 2.12.21/2.13.18/3.9.0 compatibility, Apache-2.0 and empty production dependencies.

Behavior checklist before edits: preserve seven headers tests, ten principal tests, five Filter.Chain tests; Native-only tests unchanged. Expected full final counts: 24 Native and 22 JVM per Scala version; counts must derive from JUnit results. Full terminal Native +testFull and JVM +testFull after final changes (sbt 2 incremental test can skip suites). Verify targeted testOnly works via JUnit. Owner runs final full matrix once and self-reviews. Parent targeted diff gate only; no adversary/scout required.

Additional user instruction: do not repeat the invented Scenarios/fixture-testing patterns. Persistent worker authority is ../../PROJECT_NOTES.md (Lunacy/PROJECT_NOTES.md from repository root); read and apply it. Future worker handoffs must include it.
