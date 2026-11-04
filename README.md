# scalanative-jdk-sun-httpserver

An Apache-2.0-licensed implementation of `com.sun.net.httpserver` for Scala
Native, developed incrementally with synchronous, blocking I/O.

Currently this repository contains the build, a Native socket smoke test, and
the [implementation plan](PLAN.md). No HTTP API classes are implemented yet.

Requires JDK 17 or newer, sbt, and the
[Scala Native toolchain](https://scala-native.org/en/stable/user/setup.html).
The build pins sbt 2.0.12, Scala 3.9.0, and Scala Native 0.5.12.

```sh
sbt test
sbt 'testOnly org.scalanative.testsuite.httpserver.NativeSocketSmokeTest'
```

Tests use Scala Native's JUnit plugin and compile to a native executable.
The smoke test binds a loopback socket on an OS-assigned port and closes it.

The next implementation step is `Headers`, with behavior tests. HTTPS and
Sharaf integration are outside the current scope.
