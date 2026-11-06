# scalanative-jdk-sun-httpserver

Apache-2.0 implementation of `com.sun.net.httpserver` for Scala Native, using blocking I/O. 

Supports contexts, filters, Basic authentication, stream wrapping, 
fixed-length/chunked bodies, HEAD, `100 Continue`, 
persistent connections and sequential pipelining.

Targets the JDK 8 HTTP API and measured Temurin 8u482 behavior.

## Documentation

| Document | Purpose |
| --- | --- |
| [Compatibility and limits](COMPATIBILITY.md) | Current features, security limits, API quirks and exclusions |
| [Architecture](ARCHITECTURE.md) | Request flow and thread ownership |
| [External HTTP checks](conformance/README.md) | Pinned tools and reproducible commands |
| [Conformance results](conformance/REPORT.md) | Measured results, fixes and disputed expectations |
| [Planned improvements](TODO.md) | Outstanding testing and hardening checklist |

## Build and test

Requires JDK 17+, sbt and the [Scala Native toolchain](https://scala-native.org/en/stable/user/setup.html).
Pinned: sbt 2.0.12, Scala Native 0.5.12, Scala 3.9.0; cross-builds: 2.12.21/2.13.18.

```sh
sbt testFull                         # Native, default Scala
sbt +testFull                        # Native, all Scala versions
(cd jvm-reference && sbt +testFull)   # Shared contracts against the host JDK
```

Use `testFull`: sbt 2's `test` can skip cached passing tests.
The isolated JVM build loads the JDK's HTTP server, without this library's classes.

To run reference tests on an installed JDK 8 while sbt stays on JDK 17+:

```sh
cd jvm-reference
HTTP_SERVER_TEST_JAVA_HOME="$HOME/.sdkman/candidates/java/8.0.482-tem" \
  sbt ';++2.13.18;testFull'
```

Repeat with `2.12.21`; Scala 3.9.0 cannot run on Java 8.
