// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// K2_ERROR: Argument already passed for this parameter.
// K2_ERROR: Argument type mismatch: actual type is 'Base', but 'String' was expected.
// K2_ERROR: No context argument for 'x: Derived' found.
// ERROR: An argument is already passed for this parameter
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Type mismatch: inferred type is Base but String was expected

open class Base
class Derived : Base()

context(x: Derived)
fun foo(a: String): String = a

fun main() {
    // Base is NOT a subtype of Derived, so the intention should NOT be applicable
    foo(<caret>Base(), a = "World")
}
