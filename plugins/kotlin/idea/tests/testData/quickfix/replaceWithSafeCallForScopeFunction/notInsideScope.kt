// "Replace scope function with safe (?.) call" "false"
// ACTION: Add non-null asserted (a!!) call
// ACTION: Introduce local variable
// ACTION: Replace with safe (?.) call
// ACTION: Surround with null check
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
// WITH_STDLIB
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'.
// K2_AFTER_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'.
fun foo(a: String?) {
    a<caret>.length
}