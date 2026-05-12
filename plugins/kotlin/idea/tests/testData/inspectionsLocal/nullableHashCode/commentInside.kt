// PROBLEM: Nullable value 'hashCode()' can be simplified
// FIX: Use 'hashCode()' extension on a nullable receiver
// IGNORE_K1
// WITH_STDLIB

fun hash(value: String?): Int {
    return value?.hash<caret>Code() /* keep me */ ?: 0
}
