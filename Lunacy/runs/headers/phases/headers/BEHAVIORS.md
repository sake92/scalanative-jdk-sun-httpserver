# Headers behavior checklist

| Behavior | Expected result | Test |
| --- | --- | --- |
| Header lookup | Keys compare case-insensitively | `lookupIsCaseInsensitive` |
| Repeated values | `add` preserves insertion order | `addAppendsValues` |
| Replacement | `set` leaves exactly one value | `setReplacesValues` |
| First value | `getFirst` returns first or null when absent/empty | `getFirstReturnsFirstValue` |
| Map operations | `put`, `remove`, and `putAll` use header-key semantics | `mapOperationsUseHeaderKeySemantics` |
| Map views | Standard backing views reflect later insertion and bulk clearing | `mapViewsRemainLive` |
| View iteration | Entry iterator removal permits continued iteration | `mapViewsRemainLive` |
| Entry mutation | Entry `setValue` updates the header mapping | `mapViewsRemainLive` |
| Equality/hash | Equal canonical mappings compare equal and hash equally | `equalityAndHashCodeFollowMapContract` |
| Null contract | Documented null keys and values are retained | `nullKeysAndValuesAreAccepted` |
