// IS_APPLICABLE: false
// IGNORE_K1
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.EXPRESSION)
annotation class Anno

val l: <caret>Long = @Anno +10
