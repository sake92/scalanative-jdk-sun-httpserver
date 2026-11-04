# Headers phase

| Step | Status | Owner | Scope | Verification | Report |
| --- | --- | --- | --- | --- | --- |
| Headers | PASS | Luna xhigh | PLAN.md step 1: Headers, shared tests, isolated JVM reference build, docs | All Native tests and JVM reference tests; label version-specific divergences; final self-review | REPORT-1.md |

Read /home/sake/.agents/skills/pragmatic-architecture/SKILL.md and pragmatic-testing/SKILL.md before implementation/tests. Use find-docs/Context7 before library API work; if mismatched use version-matched primary API docs and state limitation. Honor .scalafmt.conf. No commits/push requested for this implementation step. Logs belong under this phase; terminal report starts with a concise Control Block including outcome, verification counts, limitations, and exact review pointers. Mailbox only BLOCKED, DECISION_REQUIRED, FINAL (three lines maximum).

Authorized scope amendment: Headers step also owns build.sbt, .scalafmt.conf if needed for common syntax, jvm-reference build, all new/shared source compatibility, and PLAN/README updates for Scala 2.12/2.13/3 support. Terminal verification must include Native tests on all three configured Scala versions and JVM reference scenarios on all three; avoid rerunning unchanged expensive matrix after terminal report. Prefer crossScalaVersions and common Scala 2 syntax to separate source trees. Native 0.5.12 remains pinned. Default Scala 3.9.0 remains.

## Repair 2: map view correctness and proportionality

Parent gate found entry-view iterator.remove calls entries.remove while iterating entries.keySet, invalidating iteration rather than using iterator.remove. Custom view classes also duplicate standard mutable map views without demonstrated contract need. Investigate published Map/view contracts and observable JDK behavior (no OpenJDK source/tests), then simplify to backing-map views where compatible. Add meaningful shared scenarios for multi-entry iterator removal followed by continued iteration, live views after later insertions, clearing/bulk view mutation, and entry mutation. Record any deliberate view-semantics choice against reference behavior; do not broaden HTTP API scope. Reconcile stale PLAN text saying only bootstrap is current and Scala 2.13 verification is future. Retain final verification logs in phase evidence, include exact review pointers and concise final counts in immutable REPORT-2.md. Re-run Native and JVM reference matrices after final code changes; REPORT-1 remains immutable.
