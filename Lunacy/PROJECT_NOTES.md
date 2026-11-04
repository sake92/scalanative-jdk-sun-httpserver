# Project instructions for Lunacy

## Testing conventions — user instruction, 2026-11-04

- Follow the actual Scala Native repository test conventions. Inspect relevant examples in /home/sake/projects/sake92/scala-native before designing tests.
- Put test bodies directly in JUnit test classes using org.junit.Test and org.junit.Assert. Share those same JUnit sources with the isolated JVM reference Test configuration. Do not introduce *Scenarios objects, forwarding-only test wrappers, hand-written main runners, or manual pass counts.
- Apply pragmatic-testing to actual test choices: assert production behavior, not behavior implemented entirely by test fixtures. Abstract declarations alone do not justify simulated exchange/context/server behavior tests. Defer integration coverage until real implementations exist.
- Keep only minimal doubles needed to exercise real production branching. Every retained assertion must catch a plausible production defect; do not add trivial getter/setter tests merely for counts.
- Parent must include this file in future worker handoff authority, and review compliance before accepting test work. Read run-specific user notes as well; subsequent explicit user instructions take precedence.
