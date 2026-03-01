package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReflectHelperTest {

    // Test target class with private fields and methods
    @Suppress("unused")
    private class TestTarget {
        private val secretField: String = "hidden_value"
        private val intField: Int = 42

        private fun secretMethod(): String = "secret_result"

        private fun addNumbers(a: Int, b: Int): Int = a + b
    }

    @Test
    fun getFieldValue_returnsPrivateFieldValue() {
        val target = TestTarget()
        val value = ReflectHelper.getFieldValue<String>(target, "secretField")
        assertEquals("hidden_value", value)
    }

    @Test
    fun getFieldValue_returnsIntFieldValue() {
        val target = TestTarget()
        val value = ReflectHelper.getFieldValue<Int>(target, "intField")
        assertEquals(42, value)
    }

    @Test
    fun getFieldValue_returnsNullForNonexistentField() {
        val target = TestTarget()
        val value = ReflectHelper.getFieldValue<String>(target, "nonExistent")
        assertNull(value)
    }

    @Test
    fun getFieldValue_returnsNullForWrongType() {
        val target = TestTarget()
        val value = ReflectHelper.getFieldValue<Int>(target, "secretField")
        assertNull(value)
    }

    @Test
    fun invokeMethod_callsPrivateMethodNoArgs() {
        val target = TestTarget()
        val result = ReflectHelper.invokeMethod(target, "secretMethod")
        assertEquals("secret_result", result)
    }

    @Test
    fun invokeMethod_callsPrivateMethodWithArgs() {
        val target = TestTarget()
        val result = ReflectHelper.invokeMethod(
            target, "addNumbers",
            paramTypes = arrayOf(Int::class.java, Int::class.java),
            args = arrayOf(3, 7)
        )
        assertEquals(10, result)
    }

    @Test
    fun invokeMethod_returnsNullForNonexistentMethod() {
        val target = TestTarget()
        val result = ReflectHelper.invokeMethod(target, "nonExistent")
        assertNull(result)
    }

}
