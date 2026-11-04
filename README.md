# scalanative-jdk-sun-httpserver

An Apache-2.0-licensed implementation of `com.sun.net.httpserver` for Scala
Native, developed incrementally with synchronous, blocking I/O.

The first HTTP API classes, `com.sun.net.httpserver.Headers` and
`HttpPrincipal`, are implemented with case-insensitive mutable header maps and
the JDK 8 principal contract. The [implementation plan](PLAN.md) tracks the
remaining HTTP API and server work.

Requires JDK 17 or newer, sbt, and the
[Scala Native toolchain](https://scala-native.org/en/stable/user/setup.html).
The build pins sbt 2.0.12 and Scala Native 0.5.12, with Scala 3.9.0 as the
default and Scala 2.12.21/2.13.18 cross-builds.

```sh
sbt -batch testFull
sbt -batch +testFull
sbt 'testOnly org.scalanative.testsuite.httpserver.NativeSocketSmokeTest'
(cd jvm-reference && sbt -batch +run)
```

Tests use Scala Native's JUnit plugin and compile to a native executable.
Use `testFull` to run every test; sbt 2's `test` runs only tests without a
cached passing result, so it can report zero tests even after `clean`.
The Native suite covers the header and principal contracts plus the socket smoke
test. The isolated `jvm-reference` build runs the same shared scenarios against
the JDK implementation without loading this library's production classes.

The JDK 8 specification documents null keys and values for `Headers`; the
Native implementation follows that baseline. Current JDK 21 reference runs
reject some null map operations, so that observation is kept separate from the
shared contract scenarios.

HTTPS and Sharaf integration are outside the current scope.
