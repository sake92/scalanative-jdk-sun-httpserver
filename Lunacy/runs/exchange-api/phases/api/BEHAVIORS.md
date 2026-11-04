# API behavior checklist

Fixture-only exchange/context/server checks and trivial authentication getter
checks were removed per the cleanup authority. These five scenarios exercise
the production `Filter.Chain` control flow with opaque `null` exchanges; real
exchange integration is deferred until concrete production implementations.

| Behavior | Expected result | Test |
| --- | --- | --- |
| Filter chain order | Filters run in insertion order before the handler and unwind after it | `filtersRunInOrderAroundHandler` |
| Filter/handler failures | An `IOException` from a filter or empty-chain handler reaches the caller unchanged | `filterAndHandlerExceptionsPropagate` |
| Empty chain | An empty chain invokes its handler exactly once | `emptyFilterChainInvokesHandler` |
| Filter short-circuit | A filter may stop the chain; later filters and the handler are not called | `filterMayShortCircuitWithoutCallingDownstream` |
| Nonempty-chain handler failure | An `IOException` from the handler propagates unchanged through preceding filters | `nonEmptyChainPropagatesHandlerIOExceptionUnchanged` |
