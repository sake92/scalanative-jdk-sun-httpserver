# JVM reference tests

This isolated JVM project compiles the shared header and principal contract
scenarios against the JDK's `com.sun.net.httpserver` classes. It has no
dependency on this library's Native production classes.
The reference build uses Scala 3.9.0 by default and cross-runs on
Scala 2.12.21 and 2.13.18.

```sh
sbt -batch +run
```
