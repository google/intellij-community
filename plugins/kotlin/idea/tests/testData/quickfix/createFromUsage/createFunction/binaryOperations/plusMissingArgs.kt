// "Create member function 'A.plus'" "true"
// K2_ERROR: 'operator' modifier is required on 'fun plus(i: Int, s: String): A<Int>' defined in 'A'.
// K2_ERROR: No value passed for parameter 's'.

class A<T>(val n: T) {
    fun plus(i: Int, s: String): A<T> = throw Exception()
}

fun test() {
    val a: A<Int> = A(1) <caret>+ 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction