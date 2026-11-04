# HttpPrincipal

Authority: repository PLAN.md step 2 and user authorization to proceed after committing Headers (dcc5a59).
Implement only HttpPrincipal next, one class at a time. Preserve JDK 8 public contracts and published API signatures; do not read/copy/translate OpenJDK implementation or tests.
Support Scala 2.12.21, 2.13.18, and 3.9.0 with common sources. Default Scala stays 3.9.0; Scala Native stays 0.5.12.

One coherent phase/step owned by one Luna worker: reuse inventory, behavior checklist, independent implementation, shared JDK/Native contract scenarios, Native JUnit entry points, reuse isolated JVM reference runner, concise README/PLAN progress updates, full verification and self-review.
No authentication classes, exchange/server contracts, new test frameworks, or unrelated cleanup. Prefer existing conventions, java.security.Principal, private immutable fields and simple implementation. Preserve existing Headers coverage.

Worker owns final Native +testFull and JVM reference +run on all three Scala lines after final edits, retaining stdout under /tmp or ignored target directory with exact report paths. Parent reviews targeted actual code and tests; one focused Native sample only if it adds useful acceptance evidence. No adversary or gate scout needed for this bounded value type.
Changes for this next step stay uncommitted unless user requests another commit.
