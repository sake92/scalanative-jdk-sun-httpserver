# scalanative-jdk-sun-httpserver

An Apache-2.0-licensed implementation of `com.sun.net.httpserver` for Scala
Native, developed incrementally with synchronous, blocking I/O.

The initial HTTP API classes, including `Headers`, `HttpPrincipal`, the
JDK 8 authentication result values, `BasicAuthenticator`, and the
handler/exchange/context/filter contracts, are implemented incrementally.
`HttpServer` currently exposes its permanent abstract instance contract so
contexts can retain their owning
server; provider factories and socket behavior remain deferred to the server
phase. Internal request-head parsing, body-framing validation, and fixed-length
and chunked request input are implemented, along with fixed-length and chunked
response output. No-body output and socket serving remain deferred.
The [implementation plan](PLAN.md) tracks the remaining HTTP API and
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
The Native suite contains 95 JUnit tests: 32 shared header, principal, filter,
and authentication contracts, six Native-only baseline checks, one socket
smoke test, 12 internal request-head parser tests, 11 fixed-length input tests,
14 chunked input tests, 10 fixed-length output tests, and 9 chunked output tests.
The isolated `jvm-reference` build runs the same 32 direct JUnit
contract tests against the real JDK implementation without loading this library's
production classes. The shared JUnit sources are registered in the JVM project's
`Test` configuration and
use Scala Native's pinned JUnit 4.13.2 and junit-interface 0.13.3 versions.
There is no separate scenarios layer or hand-written test runner.

The JDK 8 specification documents null keys and values for `Headers`; the
Native implementation follows that baseline. Current JDK 21 reference runs
reject some null map operations, so that observation is kept separate from the
shared contract tests.

`BasicAuthenticator` uses the platform default charset and the JDK 8 Basic realm
challenge. Newer JDKs can add a UTF-8 charset parameter. Native accepts Basic
scheme names case-insensitively and returns Failure 401 for malformed credentials;
JDK 21 instead rejects other casing and can throw parsing exceptions.
Native also follows JDK 8's documented empty-realm NPE and filter null-exchange
NPE, which differ from JDK 21 behavior. The newer charset constructor is deferred.

HTTPS and Sharaf integration are outside the current scope.
