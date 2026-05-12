// "Create member function 'A.contains'" "true"
// K2_ERROR: None of the following candidates is applicable:<br><br>fun <T : Comparable<T>, R : ClosedRange<T>, Iterable<T>> R.contains(element: T?): Boolean:<br>  Candidate 'fun <T : Comparable<T>, R : ClosedRange<T>, Iterable<T>> R.contains(element: T?): Boolean' is inapplicable because of a receiver type mismatch.<br><br>fun <T : Comparable<T>, R : OpenEndRange<T>, Iterable<T>> R.contains(element: T?): Boolean:<br>  Candidate 'fun <T : Comparable<T>, R : OpenEndRange<T>, Iterable<T>> R.contains(element: T?): Boolean' is inapplicable because of a receiver type mismatch.

class A<T>(val n: T)

fun test() {
    when {
        2 <caret>in A(1) -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction