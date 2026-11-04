# HttpPrincipal behavior checklist

| Behavior | Expected result | Test |
| --- | --- | --- |
| Identity fields | `getUsername` and `getRealm` return the constructor values | `identityFieldsAreRetained` |
| Principal name | `getName` and `toString` return `realm:username` | `nameAndToStringUseRealmThenUsername` |
| Empty and non-ASCII values | Empty strings are retained and Unicode values round-trip without normalization | `emptyAndNonAsciiValuesRoundTrip` |
| Delimiter ambiguity | Usernames/realms containing `:` remain distinct principals despite the same rendered-name ambiguity | `equalityUsesUsernameAndRealmRatherThanRenderedName` |
| Equality | Equal username/realm pairs compare equal; null and unrelated objects do not | `equalityRejectsNullAndUnrelatedValues` |
| Hash consistency | Equal principals have equal hash codes, including Unicode values | `equalPrincipalsHaveEqualHashes` |
| Hash formula | Ordinary values follow the documented username-plus-realm hash formula | `hashCodeFollowsDocumentedUsernameRealmFormula` |
| Inherited equality | Non-final subclasses compare by constructor identity, symmetrically, even with overridden accessors | `inheritedEqualityUsesConstructorIdentity` |
| Virtual string form | `toString` remains equal to an overridden `getName` | `toStringDelegatesToOverriddenGetName` |
| Null constructor arguments | A null username or realm throws `NullPointerException` | `nullArgumentsAreRejected` |
