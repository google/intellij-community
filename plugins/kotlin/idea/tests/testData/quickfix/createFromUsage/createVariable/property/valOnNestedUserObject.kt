// "Create member property 'A.FOO'" "true"
// K2_ACTION: "Create property 'FOO'" "true"
// ERROR: Property must be initialized or be abstract
// K2_AFTER_ERROR: Property must be initialized or be abstract.
// K2_ERROR: Unresolved reference 'FOO'.

fun foo(){

    A.B.C.F<caret>OO

}
object A {
    object B {
        object C
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction