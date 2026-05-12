// "Change to function invocation" "true"
// K2_ERROR: Function invocation 'bar(...)' expected.
// K2_ERROR: No value passed for parameter 'i'.
// K2_ERROR: No value passed for parameter 'j'.
fun bar(i: Int, j: Int) {}

fun test(s: String){
    "$bar<caret>(1, 2) sometext $s"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix