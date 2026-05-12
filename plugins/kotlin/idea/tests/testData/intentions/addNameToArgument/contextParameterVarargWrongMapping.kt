// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// K2_ERROR: Argument type mismatch: actual type is 'Array<String>', but 'Array<out Array<String>>' was expected.
// ERROR: Type mismatch: inferred type is String but Array<String> was expected

fun foo(vararg items: Array<String>) {}

fun main() {
    // wrong type mapping, so we don't suggest anything
    foo(<caret>*arrayOf("a", "b"))
}