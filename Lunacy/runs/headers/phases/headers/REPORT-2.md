# Control Block

- Outcome: PASS — Repair 2 removes unsafe custom map-view mutation and expands shared view coverage.
- Native terminal: `sbt -batch +testFull` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 9/9 (27/27 total).
- JVM terminal: `(cd jvm-reference && sbt -batch +run)` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 7/7 (21/21 total).
- Review: direct backing views are `Headers.scala:52-58`; behavior scenarios are `HeadersContractScenarios.scala:63-101`.
- Review: cross-version scope and corrected progress wording are `PLAN.md:9-12,52-67,71-87,171-180`.
- Limitations: existing Scalafmt `preset = common` is unsupported by installed Scalafmt 3.11.5; Native emits the existing GNU-stack linker warning.

## Repair

`Headers` now returns the backing `LinkedHashMap`'s standard `keySet`,
`values`, and `entrySet` views. This removes wrapper iterators that performed
structural map removal outside the active iterator and preserves the standard
live-view contract, including iterator removal and entry `setValue` mutation.

The shared scenario captures all three views, checks later insertion, mutates
an entry, removes two entries while continuing the same iterator, removes the
survivor through the values view, and clears through the key view. The JDK 8
Map view contract used for this choice is recorded from Oracle API docs; the
current JDK 21 null-operation divergence remains documented in
`JVM-OBSERVATION.md` and is outside these shared scenarios.

`PLAN.md` now identifies Headers as the active implementation step, marks
bootstrap complete, and requires all configured Scala 2.12, 2.13, and 3 lines
before upstream readiness. `REPORT-1.md` was not changed.

## Verification notes

- `git diff --check` passed after the final source and documentation changes.
- A stale incremental Native output initially exposed a permission error on a
  cached `Headers.nir` symlink; `sbt -batch clean` followed by the terminal
  Native matrix passed. This was generated output, not a source failure.
- No HTTP API scope was expanded beyond `Headers`.
