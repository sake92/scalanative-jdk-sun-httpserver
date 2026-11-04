# JVM reference observations

The shared scenarios use only the JDK 8 published contract. These observations
come from a small runtime probe against the local JDK 21.0.10 and are kept
separate from those assertions; OpenJDK implementation source and tests were
not inspected. The published source is the [Oracle JDK 8 HttpPrincipal
documentation](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpPrincipal.html).

- `new HttpPrincipal("alice", "users")` renders `users:alice` from both
  `getName()` and `toString()`.
- Null username or realm throws `NullPointerException`.
- The JDK 8 page documents the hash as `(getUsername()+getRealm().hashCode()`;
  its parentheses are malformed. Interpreting the intended formula as the Java
  string hash of `username + realm` matches the local JDK values and the shared
  ordinary-value test. The implementation uses private constructor identity for
  the operands, not overridable accessors.
- A realm/username pair whose rendered names collide through `:` still compares
  unequal, for example `("b:c", "a")` and `("c", "a:b")`.
- A non-final subclass overriding `getUsername`, `getRealm`, and `getName`
  still compares symmetrically by its constructor identity; `toString()`
  follows the subclass's overridden `getName()`.
