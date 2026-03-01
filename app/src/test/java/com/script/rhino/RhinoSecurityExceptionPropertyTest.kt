package com.script.rhino

import com.script.ScriptException
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Feature: security-hardening, Property 2: 危险类访问触发安全异常
// **Validates: Requirements 5.4**

/**
 * Property-based test verifying that when a JS script attempts to use
 * a blacklisted class, the Rhino engine throws a ScriptException rather
 * than silently succeeding or returning null.
 *
 * When ClassShutter blocks a class, Rhino cannot resolve it and treats it
 * as a NativeJavaPackage. Any attempt to actually use the class (call a
 * method, instantiate it) throws a ScriptException wrapping a TypeError.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = android.app.Application::class)
class RhinoSecurityExceptionPropertyTest {

    // Blacklisted prefixes from RhinoClassShutter
    private val blacklistPrefixes = listOf(
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.reflect",
        "java.lang.invoke",
        "java.io.File",
        "java.net.URLClassLoader",
        "dalvik.system",
        "com.script",
        "org.mozilla",
    )

    /**
     * Property 2a: For any class name derived from a blacklist prefix,
     * attempting to instantiate it via `new Packages.<className>()` must
     * throw a ScriptException.
     *
     * Strategy: Randomly select a blacklist prefix, append ".TestClass",
     * and try to instantiate it. The ClassShutter blocks resolution, so
     * Rhino treats it as a NativeJavaPackage and throws TypeError on `new`.
     */
    @Test
    fun property2_blockedClassInstantiationThrowsScriptException() {
        val arbBlockedClassName = Arb.element(blacklistPrefixes).map { prefix ->
            prefix + ".TestClass"
        }

        runBlocking {
            checkAll(100, arbBlockedClassName) { className ->
                val script = "new Packages.$className();"
                try {
                    RhinoScriptEngine.eval(script)
                    fail(
                        "Expected ScriptException when instantiating blocked class " +
                            "'$className', but no exception was thrown"
                    )
                } catch (e: ScriptException) {
                    // Expected: ClassShutter blocks class resolution, Rhino throws
                    // TypeError wrapped in ScriptException
                    val msg = e.message.orEmpty()
                    assertTrue(
                        "ScriptException for blocked class '$className' should " +
                            "contain relevant error info, got: $msg",
                        msg.contains("not a function")
                                || msg.contains("TypeError")
                                || msg.contains("Cannot")
                    )
                }
            }
        }
    }

    /**
     * Property 2b: For blacklist prefixes with varied random suffixes,
     * attempting to call a method on the blocked class consistently
     * throws ScriptException.
     *
     * This tests that the blocking is prefix-based — any subclass or
     * inner class under a blocked prefix is also blocked.
     */
    @Test
    fun property2_blockedPrefixWithRandomSuffixThrowsScriptException() {
        // Generate valid Java identifier suffixes (letters and digits only,
        // starting with a letter) to avoid JS syntax errors
        val arbSuffix = Arb.string(minSize = 1, maxSize = 15)
            .map { s ->
                val filtered = s.filter { it.isLetter() }
                if (filtered.isEmpty()) "Cls" else filtered
            }

        runBlocking {
            checkAll(100, Arb.element(blacklistPrefixes), arbSuffix) { prefix, suffix ->
                val className = "$prefix.$suffix"
                // Try to instantiate the blocked class to force resolution
                val script = "new Packages.$className();"
                try {
                    RhinoScriptEngine.eval(script)
                    fail(
                        "Expected ScriptException when instantiating blocked " +
                            "class '$className', but no exception was thrown"
                    )
                } catch (e: ScriptException) {
                    val msg = e.message.orEmpty()
                    assertTrue(
                        "ScriptException for blocked class '$className' should " +
                            "contain relevant error info, got: $msg",
                        msg.contains("not a function")
                                || msg.contains("TypeError")
                                || msg.contains("Cannot")
                    )
                }
            }
        }
    }
}
