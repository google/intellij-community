// "Make 'Foo' data class" "false"
// ERROR: Destructuring declaration initializer of type Pair must have a 'component3()' function
// K2_ERROR: Destructuring of type 'Pair' requires operator function 'component3()'.
// K2_AFTER_ERROR: Destructuring of type 'Pair' requires operator function 'component3()'.

data class Pair(val first: Int, val second: Int)

fun foo(pairs: List<Pair>) {
    for ((_, _, _) in pa<caret>irs) {}
}
