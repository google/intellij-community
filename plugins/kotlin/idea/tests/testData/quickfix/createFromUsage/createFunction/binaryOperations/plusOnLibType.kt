// "Create extension function 'Int.plus'" "true"
// WITH_STDLIB
// K2_ERROR: None of the following candidates is applicable:<br><br>fun plus(other: Int): Int:<br>  Argument type mismatch: actual type is 'A<uninferred T (of class A<T>)>', but 'Int' was expected.<br><br>fun plus(other: Byte): Int:<br>  Argument type mismatch: actual type is 'A<uninferred T (of class A<T>)>', but 'Byte' was expected.<br><br>fun plus(other: Short): Int:<br>  Argument type mismatch: actual type is 'A<uninferred T (of class A<T>)>', but 'Short' was expected.<br><br>fun plus(other: Long): Long:<br>  Argument type mismatch: actual type is 'A<uninferred T (of class A<T>)>', but 'Long' was expected.<br><br>fun plus(other: Float): Float:<br>  Argument type mismatch: actual type is 'A<uninferred T (of class A<T>)>', but 'Float' was expected.<br><br>fun plus(other: Double): Double:<br>  Argument type mismatch: actual type is 'A<uninferred T (of class A<T>)>', but 'Double' was expected.

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2 <caret>+ A(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction