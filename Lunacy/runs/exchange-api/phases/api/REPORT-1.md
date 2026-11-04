# Control Block

- Outcome: PASS — implemented the JDK 8 API batch and its permanent dependency boundary.
- Native terminal: `XDG_RUNTIME_DIR=/tmp/sbt-runtime sbt -batch +testFull` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 31/31 (93/93 total).
- JVM terminal: `XDG_RUNTIME_DIR=/tmp/sbt-runtime sbt -batch +run` from `jvm-reference/` — Scala 2.12.21, 2.13.18, and 3.9.0 each passed 29/29 (87/87 total).
- Logs: `target/exchange-api-evidence/native-terminal.log`; `jvm-reference/target/exchange-api-evidence/jvm-terminal.log`.
- Review: production API is `src/main/scala/com/sun/net/httpserver/{Authenticator,HttpHandler,HttpExchange,HttpContext,Filter,HttpServer}.scala`; chain behavior is `Filter.scala:14-27`.
- Review: shared behavior matrix is `src/test/scala/org/scalanative/testsuite/httpserver/ExchangeContractScenarios.scala:9-225`; JUnit wrappers are `ExchangeContractTest.scala:5-39`; JVM registration is `jvm-reference/build.sbt:8-15` and `HeadersReferenceMain.scala:6-34`.
- Docs: `PLAN.md:97-112,177-187` and `README.md:6-12,26-32` record the dependency boundary and deferred factories/socket engine.
- `git diff --check` passed. Existing Native linker warnings about missing GNU-stack notes remain; no framing, BasicAuthenticator, provider factory, or socket behavior was added.

## Implementation

Added `HttpHandler`, the JDK 8-era abstract `HttpExchange` surface (including the directly verified baseline `getResponseCode()`), `HttpContext`, `Filter`, and stateful insertion-order `Filter.Chain`. Added the complete `Authenticator` base/result hierarchy and the permanent abstract `HttpServer` instance contract required by `HttpContext`; static factories and concrete server behavior remain deferred to section 5.

Shared scenarios cover result values, handler exchange/exception behavior, exchange request/response state and stream replacement, attributes, context mutable state and authenticator replacement/invocation, filter order/unwind, wrapped streams, downstream attributes, exception propagation, empty chains, and the abstract server contract. JVM runs execute these scenarios against the real JDK classes without loading production classes.

Published-contract references used: Oracle JDK 8 `HttpExchange` (`getResponseCode` is baseline), `HttpContext`, `HttpServer`, `Authenticator`, and `Filter` API pages. No OpenJDK implementation or test source was copied.
