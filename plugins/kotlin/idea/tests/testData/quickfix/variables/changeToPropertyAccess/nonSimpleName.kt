// "Change to property access" "true"
// K2_ERROR: Candidate 'fun <T, R> DeepRecursiveFunction<T, R>.invoke(value: T): R' is inapplicable because of a receiver type mismatch.
// K2_ERROR: Cannot infer type for type parameter 'R'. Specify it explicitly.
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.

fun x() {
    val y = (1 + 2<caret>)()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UnresolvedInvocationQuickFix$ChangeToPropertyAccessQuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UnresolvedInvocationQuickFix$ChangeToPropertyAccessQuickFix