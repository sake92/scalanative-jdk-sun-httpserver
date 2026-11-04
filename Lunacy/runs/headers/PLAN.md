# Headers implementation

Authority: repository PLAN.md, step 1. User authorized implementation and requested lunacy when appropriate.

One coherent phase/step: implement JDK 8 Headers and shared Native/JVM reference tests.
Use API documentation and specifications only; do not inspect/copy OpenJDK implementation/tests.
Start with reuse inventory and behavior checklist. Preserve Java signatures and mutable Map views.
Explicitly distinguish JDK 8 documented null behavior from current JDK observations.
No newer constructors/factories or exchange/server work.

Owner verifies Native suite and isolated JVM reference suite after final edits, updates README/PLAN progress and reports limitations. Parent reviews targeted implementation and test isolation; no redundant full matrix.
No adversary or gate scout planned for this bounded single-owner phase.

## User scope update: Scala cross-version support

Support Scala 2.12, 2.13 and 3 now, not only before upstream readiness. Add crossScalaVersions to Native and JVM reference builds, keep all shared sources compatible, document +test/reference commands, and run each target on all three lines. Default Scala 3.9.0 stays; candidate Scala 2 versions 2.12.21 and 2.13.18 match local scala-native/project/ScalaVersions.scala; verify published 0.5.12 compiler artifacts and use supported patch releases if necessary. Test JDK 8-contract-only null cases on Native; version-specific reference observations must remain explicit. No server/API scope expansion.
