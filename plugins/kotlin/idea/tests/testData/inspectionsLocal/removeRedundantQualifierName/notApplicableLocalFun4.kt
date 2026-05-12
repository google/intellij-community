// PROBLEM: none
// K2_ERROR: None of the following candidates is applicable:<br><br>fun hashCode(): Int:<br>  Too many arguments for 'fun hashCode(): Int'.<br>  Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'E (of fun <E> E.foo)'.<br>  'infix' modifier is required on 'fun hashCode(): Int'.<br><br>fun Any?.hashCode(): Int:<br>  Too many arguments for 'fun Any?.hashCode(): Int'.<br>  'infix' modifier is required on 'fun Any?.hashCode(): Int'.
package my.simple.name

fun <T, E, D> foo(a: T, b: E, c: D) = a!!.hashCode() + b!!.hashCode() + c!!.hashCode()

fun <E> E.foo() = this.!!hashCode()

class Outer {
    fun <E> E.foo(x: E, y: E, z: E) = x!!.hashCode() + y!!.hashCode() + z!!.hashCode()

    class Inner {
        fun foo(a: Int, b: Boolean, c: String) = c + a + b

        fun test(): Int {
            fun foo(a: Int, b: Boolean, c: String) = c + a + b
            return my.simple.name<caret>.foo(1, false, "bar")
        }

        companion object {
            fun <T, E, D> foo(a: T, b: E, c: D) = a!!.hashCode() + b!!.hashCode() + c!!.hashCode()
        }
    }
}
