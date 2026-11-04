# Control Block

- Outcome: PASS — `HttpPrincipal` and shared contract coverage complete.
- Native terminal: `sbt -batch +testFull` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 16/16 (48/48 total).
- JVM terminal: `(cd jvm-reference && sbt -batch +run)` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 14/14 (42/42 total).
- Terminal logs: `target/http-principal-evidence/native-terminal-2.log`; `jvm-reference/target/http-principal-evidence/jvm-terminal-2.log`.
- Review: implementation `src/main/scala/com/sun/net/httpserver/HttpPrincipal.scala:1-34`; checklist `BEHAVIORS.md:1-12`.
- Review: shared scenarios `src/test/scala/org/scalanative/testsuite/httpserver/HttpPrincipalContractScenarios.scala:1-75`; Native wrappers `HttpPrincipalContractTest.scala:1-26`.
- Review: JVM registration `jvm-reference/build.sbt:8-12`; runner `HeadersReferenceMain.scala:12-19`; docs `README.md:6-33`, `jvm-reference/README.md:3-8`, `PLAN.md:9-12,171-181`.
- Limitations: existing Native linker GNU-stack warnings and Scala 3 wildcard deprecation warnings remain; no authentication classes were added.

## Implementation

`HttpPrincipal` implements `java.security.Principal` with private immutable
username, realm, and rendered-name state. It preserves the JDK 8 signatures,
renders `realm:username`, rejects null constructor arguments with
`NullPointerException`, compares username and realm independently, and keeps
hashes consistent for equal principals.

The shared scenarios cover identity getters, rendering, empty and non-ASCII
values, realm/username differences, delimiter ambiguity, null/unrelated
equality, hash consistency, and null inputs. The same scenarios run against
the local JDK reference without loading Native production classes. The
documented contract and local JDK 21 observations are separated in
`JVM-OBSERVATION.md:1-14`; OpenJDK implementation source and tests were not
inspected. Context7 resolved the Oracle Java SE 8 API but did not expose the
class detail needed here, so the Oracle JDK 8 published HttpPrincipal page was
used as the version-matched primary source.

`git diff --check` passed after the final edits. No commits were created, and
the pre-existing untracked `.basamake/` directory was preserved.
