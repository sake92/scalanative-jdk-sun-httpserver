# Current JDK observation

Environment: OpenJDK 21.0.10 (Temurin, 2026-01-20).

Command: `java /tmp/HeadersProbe.java`.

The JDK `Headers` reference rejected `getFirst(null)`, `add(null, ...)`,
`set(null, ...)`, and `put(null, null)` with `NullPointerException` in this
current runtime. The JDK 8 API page documents null keys and values as accepted,
so the shared contract keeps this as a version-specific observation while the
Native implementation tests the JDK 8 baseline separately.
