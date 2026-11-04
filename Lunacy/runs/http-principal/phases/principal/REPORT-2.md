# Control Block

- Outcome: PASS — Repair 2 restores inherited equality/reflexivity and virtual `toString` behavior.
- Native terminal: `sbt -batch +testFull` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 19/19 (57/57 total).
- JVM terminal: `(cd jvm-reference && sbt -batch +run)` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 17/17 (51/51 total).
- Terminal logs: `target/http-principal-evidence/native-terminal-repair2.log`; `jvm-reference/target/http-principal-evidence/jvm-terminal-repair2.log`.
- Review: implementation `src/main/scala/com/sun/net/httpserver/HttpPrincipal.scala:11-34`; scenarios `HttpPrincipalContractScenarios.scala:50-123`; wrappers `HttpPrincipalContractTest.scala:21-34`.
- Review: JVM runner `jvm-reference/src/main/scala/org/scalanative/testsuite/httpserver/HeadersReferenceMain.scala:17-22`; checklist `BEHAVIORS.md:9-15`; observations `JVM-OBSERVATION.md:3-21`.
- Limitations: existing Native GNU-stack linker warnings remain; no authentication/server scope was added; `REPORT-1.md` remains unchanged.

## Repair

`equals` now compares both instances' private immutable constructor fields,
which preserves symmetry and reflexivity when a non-final subclass overrides
`getUsername` or `getRealm`. `toString` now delegates to virtual `getName`,
matching the published contract for subclasses overriding `getName`.

Shared Native/JVM scenarios now cover the documented ordinary hash formula,
subclass constructor-identity equality in both directions with equal hashes,
and virtual `toString`/`getName` behavior. The local JDK probe confirmed those
observations without inspecting OpenJDK source or tests. The Oracle JDK 8 page
documents the hash formula with malformed parentheses; `JVM-OBSERVATION.md`
records that textual defect and the runtime-confirmed interpretation as the
Java string hash of `username + realm`.

`git diff --check` passed after the final edits. No commits were created, and
the pre-existing untracked `.basamake/` directory was preserved.
