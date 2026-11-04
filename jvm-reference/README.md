# JVM reference tests

This isolated JVM project compiles the shared direct JUnit contract tests
against the JDK's `com.sun.net.httpserver` classes. It has no dependency on
this library's Native production classes, and its test sources are registered
in the `Test` configuration rather than run through a custom main method.
The reference build uses Scala 3.9.0 by default and cross-runs on
Scala 2.12.21 and 2.13.18.

```sh
sbt -batch testFull
sbt -batch +testFull
sbt -batch 'testOnly org.scalanative.testsuite.httpserver.HeadersTest'
```

The shared tests use the Scala Native pinned JUnit 4.13.2 and
`com.github.sbt` junit-interface 0.13.3 dependencies. Native-only null and
socket smoke tests are intentionally excluded from this JVM reference build.
