// "Add 'Any' as upper bound for E" "true"
// K2_ERROR: Type argument is not within its bounds: type parameter 'T (of fun <T : Any> foo)' must be subtype of 'Any', but actual: 'E (of fun <E> bar)'.

fun <T : Any> foo() = 1

fun <E> bar() = foo<E<caret>>()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix