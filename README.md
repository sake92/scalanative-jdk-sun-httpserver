# scalanative-jdk-sun-httpserver

An Apache-2.0-licensed implementation of `com.sun.net.httpserver` for Scala
Native, developed incrementally with synchronous, blocking I/O.

The initial HTTP API classes, including `Headers`, `HttpPrincipal`, the
JDK 8 authentication result values, and the handler/exchange/context/filter
contracts, are implemented incrementally. `HttpServer` currently exposes its
permanent abstract instance contract so contexts can retain their owning
server; provider factories and socket behavior remain deferred to the server
phase. The [implementation plan](PLAN.md) tracks the remaining HTTP API and
server work.

Requires JDK 17 or newer, sbt, and the
[Scala Native toolchain](https://scala-native.org/en/stable/user/setup.html).
The build pins sbt 2.0.12 and Scala Native 0.5.12, with Scala 3.9.0 as the
default and Scala 2.12.21/2.13.18 cross-builds.

```sh
sbt -batch testFull
sbt -batch +testFull
sbt 'testOnly org.scalanative.testsuite.httpserver.HeadersTest'
(cd jvm-reference && sbt -batch testFull)
(cd jvm-reference && sbt -batch +testFull)
```

Tests use Scala Native's JUnit plugin and compile to a native executable.
Use `testFull` to run every test; sbt 2's `test` runs only tests without a
cached passing result, so it can report zero tests even after `clean`.
The Native suite contains 24 JUnit tests: seven header, ten principal, five
`Filter.Chain` control-flow, one Native-only null baseline, and one socket
smoke test. The isolated `jvm-reference` build runs the same 22 direct JUnit
contract tests for headers, principals, and filters against the real JDK
implementation without loading this library's production classes. The shared
JUnit sources are registered in the JVM project's `Test` configuration and
use Scala Native's pinned JUnit 4.13.2 and junit-interface 0.13.3 versions.
There is no separate scenarios layer or hand-written test runner.

The JDK 8 specification documents null keys and values for `Headers`; the
Native implementation follows that baseline. Current JDK 21 reference runs
reject some null map operations, so that observation is kept separate from the
shared contract tests.

HTTPS and Sharaf integration are outside the current scope.
