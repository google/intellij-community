// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB

fun hash(value: String): Int {
    return value?.hash<caret>Code() ?: 0
}
