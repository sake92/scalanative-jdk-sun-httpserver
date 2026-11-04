# Decision Required: API dependency boundary

Question: May the API step add the required public dependency declarations, or must the step stop before implementation?

Authority: `PLAN.md` section 3 requires JDK 8-compatible `HttpHandler`, `HttpExchange`, `HttpContext`, `Filter`, and `Filter.Chain`; it explicitly says to resolve authentication/server dependencies and forbids temporary public stubs. `Lunacy/runs/exchange-api/PLAN.md` limits this step to those five surfaces and says dependency conflicts require parent decision.

Facts:

- `HttpContext`'s required JDK 8 signature includes `abstract HttpServer getServer()`, `abstract Authenticator setAuthenticator(Authenticator)`, and `abstract Authenticator getAuthenticator()`; neither `HttpServer.scala` nor `Authenticator.scala` exists in the repository.
- `HttpExchange` can be declared independently after `HttpPrincipal`, `Headers`, and JDK stream/network types are available. Direct Oracle JDK 8 verification confirms `getResponseCode()` is part of the baseline; current-only additions are `Request` and `AutoCloseable`.
- `Filter`/`Filter.Chain` can be declared independently of `HttpServer` at source level, but behavior tests for context/server integration cannot use a real server yet.
- A placeholder `HttpServer`/`Authenticator` public type would violate the explicit no-temporary-public-stubs constraint; omitting `HttpContext.getServer` or changing its return type would violate the published signature.
- Oracle JDK 8 API confirms the dependency edges and signatures: [`HttpContext`](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpContext.html) and [`HttpServer`](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpServer.html); the JDK 6/8-era [`HttpExchange`](https://docs.oracle.com/javase/6/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpExchange.html) page documents the baseline exchange methods. Current JDK adds `Request` and `AutoCloseable`; `getResponseCode()` is already in the JDK 8 baseline.

Options:

1. Recommended: parent authorizes a coordinated dependency slice: implement the complete JDK 8 `Authenticator` result hierarchy and the complete abstract `HttpServer` public contract in this phase, while deferring server behavior. This broadens this step beyond its durable scope and needs explicit plan/state amendment; no fake methods or stubs.
2. Parent narrows this step to `HttpHandler`, `HttpExchange`, and `Filter`/`Filter.Chain`, deferring `HttpContext` declarations/tests until the authentication and server phases provide real dependencies. This leaves section 3 incomplete for now but preserves scope.
3. Parent authorizes a separate dependency step/worker for `Authenticator` and `HttpServer` declarations/contracts, then resumes this API step after it is complete.

Recommendation: choose option 2 or 3. Do not add public placeholder classes. If option 1 is chosen, amend `PLAN.md`, run `STEPS.md`, and ownership/state before edits.

Execution impact: work is frozen before source/test edits; no terminal verification or `REPORT-1.md` is produced until the parent resolves this boundary.
