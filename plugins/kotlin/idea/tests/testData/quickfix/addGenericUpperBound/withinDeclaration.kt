// "Add 'Any' as upper bound for E" "true"
// K2_ERROR: Type argument is not within its bounds: type parameter 'T (of class A<T : Any>)' must be subtype of 'Any', but actual: 'E (of fun <E> bar)'.

class A<T : Any>
fun <E> bar(x: A<E<caret>>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix