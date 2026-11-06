# Repository instructions

## Implementation

- Use Scala for repository tools and automation, including standalone scripts; do not add Python tooling.
- Prefer `mainargs` for Scala command-line parsing.
- Consult Context7 before using a library or framework; if unavailable or mismatched, use version-matched primary documentation and state the fallback.
- Follow `.scalafmt.conf`.
- Keep the public API compatible with JDK 8; follow the HTTP protocol policy in `COMPATIBILITY.md` when wire behavior differs from the JDK.
- Write original implementation and tests from API contracts and RFCs; do not copy or translate OpenJDK source or tests.

## Validation

| Change | Check |
| --- | --- |
| Native implementation or protocol behavior | `sbt +testFull` |
| Shared public API contracts | `(cd jvm-reference && sbt +testFull)` |
| External protocol checks or their runner | Follow `conformance/README.md` |

- Use direct JUnit tests in `src/test/scala`; add regressions for confirmed defects.
- Use `testFull` for full verification: sbt 2 can skip cached passing tests with `test`.
- Keep Native results, host-JDK results and remote CI evidence distinct.
- Keep downloaded suites, runtimes, build outputs and raw reports outside tracked sources.

## Documentation maintenance

- Keep current features, protections and limits in `COMPATIBILITY.md`; do not duplicate feature checklists elsewhere.
- Track outstanding work as unchecked items in `TODO.md`; on completion, update the behavior reference and relevant evidence, then remove the task.
- Keep `conformance/REPORT.md` compact: result tables and one-line findings under clear sections, retaining qualifications and evidence links.
- Keep reproducible commands in `conformance/README.md` and implementation explanations in `ARCHITECTURE.md`.
- Update the documentation index in `README.md` when adding or moving documents.

## References

- `README.md`: build setup and pinned versions.
- `ARCHITECTURE.md`: class relationships, request flow and thread ownership.
- `COMPATIBILITY.md`: API contracts, protocol decisions and exclusions.
- `conformance/README.md`: pinned external suites and reproducible commands.
- `conformance/REPORT.md`: observed results and disputed expectations.
- `TODO.md`: planned testing, hardening and portability work.
