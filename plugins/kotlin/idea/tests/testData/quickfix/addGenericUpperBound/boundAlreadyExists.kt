// "class org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix" "false"
// ERROR: Type argument is not within its bounds: should be subtype of 'Any'
// K2_ERROR: Type argument is not within its bounds: type parameter 'T (of fun <T : Any> foo)' must be subtype of 'Any', but actual: 'E (of fun <E> bar)'.
// K2_AFTER_ERROR: Type argument is not within its bounds: type parameter 'T (of fun <T : Any> foo)' must be subtype of 'Any', but actual: 'E (of fun <E> bar)'.

fun <T : Any> foo() = 1

fun <E : Any?> bar() = foo<E<caret>>()
