// PROBLEM: none
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// K2_ERROR: Missing return statement.

<caret>@Deprecated("")
fun foo(): String {
    bar()
}

fun bar(): String = ""