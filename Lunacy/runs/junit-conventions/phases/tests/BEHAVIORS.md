# Shared JUnit behavior checklist

| Behavior | Expected result | Test |
| --- | --- | --- |
| Header lookup | Header names compare case-insensitively while preserving the first inserted key spelling | `HeadersTest.lookupIsCaseInsensitive` |
| Repeated header values | `add` appends values in insertion order | `HeadersTest.addAppendsValues` |
| Header replacement | `set` replaces all values for a case-insensitive key | `HeadersTest.setReplacesValues` |
| First header value | `getFirst` returns the first value, or null when absent | `HeadersTest.getFirstReturnsFirstValue` |
| Header map operations | `put`, `putAll`, `remove`, and `containsKey` use header-key semantics | `HeadersTest.mapOperationsUseHeaderKeySemantics` |
| Live header views | Key, value, and entry views observe insertion and support contract-preserving mutation/removal | `HeadersTest.mapViewsRemainLive` |
| Header equality/hash | Equal case-insensitive mappings compare equal and have equal hashes | `HeadersTest.equalityAndHashCodeFollowMapContract` |
| Principal identity | Username and realm are retained by their accessors | `HttpPrincipalTest.identityFieldsAreRetained` |
| Principal rendering | Name and string form render as `realm:username` | `HttpPrincipalTest.nameAndToStringUseRealmThenUsername` |
| Empty and Unicode principals | Empty strings and non-ASCII values round-trip unchanged | `HttpPrincipalTest.emptyAndNonAsciiValuesRoundTrip` |
| Principal equality identity | Equality uses username and realm, not an ambiguous rendered name | `HttpPrincipalTest.equalityUsesUsernameAndRealmRatherThanRenderedName` |
| Principal equality rejection | Null and unrelated objects are rejected while equal principals compare equal | `HttpPrincipalTest.equalityRejectsNullAndUnrelatedValues` |
| Principal hash consistency | Equal principals have equal hashes, including Unicode values | `HttpPrincipalTest.equalPrincipalsHaveEqualHashes` |
| Principal hash formula | Ordinary principal values follow the documented username-plus-realm formula | `HttpPrincipalTest.hashCodeFollowsDocumentedUsernameRealmFormula` |
| Inherited principal equality | Subclasses retain constructor-based equality and hash behavior | `HttpPrincipalTest.inheritedEqualityUsesConstructorIdentity` |
| Virtual principal string form | `toString` delegates to an overridden `getName` | `HttpPrincipalTest.toStringDelegatesToOverriddenGetName` |
| Principal null validation | Null username or realm is rejected with `NullPointerException` | `HttpPrincipalTest.nullArgumentsAreRejected` |
| Filter order | Filters run in list order around the handler | `FilterTest.filtersRunInOrderAroundHandler` |
| Filter/handler failure propagation | Filter and handler `IOException` values escape unchanged | `FilterTest.filterAndHandlerExceptionsPropagate` |
| Empty filter chain | An empty chain invokes its handler | `FilterTest.emptyFilterChainInvokesHandler` |
| Filter short circuit | A filter may stop the chain without invoking later filters or the handler | `FilterTest.filterMayShortCircuitWithoutCallingDownstream` |
| Nonempty-chain handler failure | A handler failure through a nonempty chain remains the same exception and unwinds normally | `FilterTest.nonEmptyChainPropagatesHandlerIOExceptionUnchanged` |
