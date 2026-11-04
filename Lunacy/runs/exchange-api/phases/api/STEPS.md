# Steps

| Step | Status | Ownership | Verification | Report |
| --- | --- | --- | --- | --- |
| api | PASS | Section 3 API, shared tests, docs; dependency conflicts require parent decision before added scope | Full Native/JVM matrix on all three configured Scala versions | REPORT-1.md |
| api-repair-2 | PASS | Focused `Filter.Chain` regression tests and README clarification; production API unchanged | Impacted shared scenarios on Native/JVM, all three configured Scala versions | REPORT-2.md |

Read existing project code and public API contracts first. For every class add behavior/expected-result/test-name checklist before implementation. No OpenJDK implementation or test copying. Use pragmatic-architecture, pragmatic-testing and find-docs skills. Full inspect/implement/verify/self-review/fix/terminal-reverify loop. Only BLOCKED, DECISION_REQUIRED or FINAL mailbox messages, at most three lines. Final report immutable with concise Control Block and evidence paths.

Parent dependency resolution: api scope now includes real Authenticator/result classes and the permanent abstract HttpServer instance contract. Factories and server engine deferred explicitly; no placeholder bodies. BasicAuthenticator excluded. Implement all section 3 behavior applicable before real exchanges; use small concrete test-only exchange/context subclasses for abstract extension contracts and authenticator integration, not fake production implementation behavior. Check JDK 8 getResponseCode baseline directly. Resume same coherent step; report REPORT-1.md.

## Repair 2: filter-chain control flow regressions

Add focused shared scenarios proving a filter may short-circuit without invoking
later filters or the handler, and that an `IOException` from the handler is
propagated unchanged through a nonempty chain. Preserve `REPORT-1.md`; write
the terminal repair evidence to `REPORT-2.md`. Do not change production code
unless a concrete chain defect is found.

| api-cleanup-3 | PASS | User-authorized removal of fixture-only and trivial tests, all registrations and current coverage documentation; production unchanged | Final full Native/JVM suites across all configured Scala versions | REPORT-3.md |
