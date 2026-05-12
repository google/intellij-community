// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// K2_ERROR: Too many arguments for 'context(x: String) fun foo(a: String, b: String): String'.
// K2_AFTER_ERROR: Too many arguments for 'context(x: String) fun foo(a: String, b: String): String'.
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Too many arguments for public fun foo(a: String, b: String): String defined in root package in file contextParameterAmbiguous.kt
// ERROR: Unresolved reference: with
// ERROR: Unresolved reference: x
// AFTER-WARNING: Parameter 'b' is never used

context(x: String)
fun foo(a: String, b: String): String = x + a

fun main() {
    with("context") {
        foo(a = "World", <caret>"Hello", "Hola")
    }
}