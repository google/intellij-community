// FIR_COMPARISON
// FIR_IDENTICAL
package test

enum class Color { RED, GREEN, BLUE }

fun take(c: Color) {}

fun test() {
    take(RE<caret>)
}

// ELEMENT: RED
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
