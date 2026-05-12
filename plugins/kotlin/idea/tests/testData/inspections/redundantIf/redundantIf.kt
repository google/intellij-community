fun foo() {
    if (value % 2 == 0) {
        return true
    } else {
        return false
    }
}

fun bar() {
    if (value % 2 == 0) return true else return false
}

fun baz(value: Int): Boolean {
    if (value % 2 == 0) return value > 10 else return false
}

fun qux(value: Int): Boolean {
    if (value % 2 == 0) return true else return value > 10
}

fun quux(value: Int): Boolean {
    if (value % 2 == 0) return value > 10 else return true
}

fun noChange(value: Int, flag: Boolean?): Boolean {
    return if (value % 2 == 0) flag else false
}
