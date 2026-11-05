# scalanative-jdk-sun-httpserver

An Apache-2.0-licensed implementation of `com.sun.net.httpserver` for Scala
Native, developed incrementally with synchronous, blocking I/O.

`HttpServer.create()` and `create(address, backlog)` provide a working blocking
HTTP server with contexts, filters, Basic authentication, immutable request
headers, wrapped streams and exchange attributes. Fixed-length and chunked
request/response bodies, HEAD/bodyless responses and lazy `100 Continue` are
implemented and tested over loopback sockets.

HTTP/1.1 connections persist by default; HTTP/1.0 requires explicit keep-alive.
Pipelined requests are handled sequentially and responses retain request order.
Unread request bodies are drained up to 64 KiB of wire data and a one-second
total deadline; an incomplete or invalid body closes the connection. Waiting for
another request head has a 30-second read timeout. These are drain/idle bounds,
not a maximum request-body size or a timeout for application body reads.

The default executor runs handlers on the accept thread, so idle or slow clients
serialize default work. Persistent connections occupy a worker while idle.
Use `setExecutor` before `start` for concurrent dispatch; the caller owns that
executor and must shut it down.
`stop(delay)` allows active handlers a bounded grace period, then closes sockets
and stops the accept thread. Arbitrary handlers must cooperate with interruption
or closed sockets. Provider discovery is intentionally out of scope.
The [compatibility inventory](COMPATIBILITY.md) lists the implemented API,
unsupported features and validation limits. The [implementation plan](PLAN.md)
tracks remaining work.

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
The Native suite contains 135 JUnit tests: 45 shared header, principal, filter,
authentication and server contracts, one socket smoke test, 12 internal request-head parser tests, 11 fixed-length input tests,
14 chunked input tests, 12 fixed-length/no-body output tests, 9 chunked output tests,
11 response-head writer tests, and 20 Native-only server integration tests.
The isolated `jvm-reference` build runs the same 45 direct JUnit
contract tests against the real JDK implementation without loading this library's
production classes. The shared JUnit sources are registered in the JVM project's
`Test` configuration and
use Scala Native's pinned JUnit 4.13.2 and junit-interface 0.13.3 versions.
There is no separate scenarios layer or hand-written test runner.

To compare against an installed JDK 8 while sbt stays on JDK 21, set the test
runtime for the isolated reference build:

```sh
cd jvm-reference
HTTP_SERVER_TEST_JAVA_HOME="$HOME/.sdkman/candidates/java/8.0.482-tem" \
  sbt -batch ';++2.13.18;testFull'
```

Use `2.12.21` for the other Java 8-compatible Scala line. Scala 3.9.0 requires
Java 17 or newer. All 45 shared tests pass on Temurin 8u482. Assertions explicitly
distinguish measured JDK 8 and JDK 21 behavior, as described in
[COMPATIBILITY.md](COMPATIBILITY.md). Without the environment variable, reference
tests continue to use the build JDK.

Runtime compatibility follows the tested Temurin 8u482 behavior where it differs
from the published JDK 8 documentation. `HttpPrincipal.getName()` returns the
username. Header bulk insertion preserves input spelling, while direct writes
normalize names and reject null mappings/elements and invalid line breaks.
Request headers remain immutable and response serialization validates every field.

`BasicAuthenticator` accepts null/empty realms, uses the platform default charset
and emits the JDK 8 Basic realm challenge. The scheme is case-sensitive;
malformed credentials propagate parsing exceptions. Null exchanges pass through
filter chains. Duplicate contexts retain registration order and can be removed
independently. Newer-JDK differences are explicit in the shared reference tests.
The newer authentication charset constructor is deferred.

HTTPS and Sharaf integration are outside the current scope.
