package com.script.rhino

import com.script.ScriptException
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.javascript.Context
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Feature: security-hardening, Property 3: JS 脚本执行超时终止
// **Validates: Requirements 5.2**

/**
 * Property-based test verifying that when a JS script runs longer than the
 * configured timeout threshold, the Rhino engine interrupts execution and
 * throws a ScriptException wrapping a ScriptTimeoutException.
 *
 * The timeout mechanism works via an instruction observer that fires every
 * 10,000 instructions. On each observation, RhinoContext.ensureActive()
 * checks elapsed time against executionTimeoutMillis and throws
 * ScriptTimeoutException if exceeded. RhinoScriptEngine.eval() catches
 * this and wraps it in a ScriptException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = android.app.Application::class)
class RhinoTimeoutPropertyTest {

    // Infinite loop script that will never terminate on its own
    private val infiniteLoopScript = "while(true){}"

    /**
     * Property 3: For any timeout threshold in [100..2000] ms, executing an
     * infinite loop script must:
     * 1. Throw a ScriptException (not hang forever)
     * 2. Complete within approximately the timeout + tolerance window
     *
     * The tolerance accounts for the instruction observer granularity
     * (checks every 10,000 instructions) and JVM scheduling overhead.
     */
    @Test
    fun property3_infiniteLoopScriptTerminatesWithinTimeout() {
        val toleranceMs = 500L

        // Access RhinoScriptEngine to ensure the ContextFactory is initialized
        // (the object's init block calls ContextFactory.initGlobal)
        @Suppress("UNUSED_VARIABLE")
        val engine = RhinoScriptEngine

        runBlocking {
            checkAll(20, Arb.long(range = 100L..2000L)) { timeoutMs ->
                // Enter context to set timeout. Rhino returns the same context
                // for the same thread, so eval() will reuse this context.
                val cx = Context.enter() as RhinoContext
                cx.executionTimeoutMillis = timeoutMs
                // Don't exit — keep the context entered so eval() reuses it.
                // eval() will call Context.enter() again (incrementing ref count)
                // and Context.exit() when done (decrementing ref count).

                val startTime = System.currentTimeMillis()
                try {
                    RhinoScriptEngine.eval(infiniteLoopScript)
                    fail(
                        "Expected ScriptException for infinite loop with " +
                            "timeout=${timeoutMs}ms, but script completed normally"
                    )
                } catch (e: ScriptException) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val msg = e.message.orEmpty()

                    // Verify the exception is timeout-related
                    assertTrue(
                        "ScriptException should indicate timeout, got: $msg",
                        msg.contains("timed out", ignoreCase = true)
                    )

                    // Verify execution time is approximately within threshold + tolerance
                    assertTrue(
                        "Script should run at least close to timeout " +
                            "(timeout=${timeoutMs}ms, elapsed=${elapsed}ms)",
                        elapsed >= timeoutMs - toleranceMs
                    )
                    assertTrue(
                        "Script should terminate within timeout + tolerance " +
                            "(timeout=${timeoutMs}ms, tolerance=${toleranceMs}ms, " +
                            "elapsed=${elapsed}ms)",
                        elapsed <= timeoutMs + toleranceMs
                    )
                } finally {
                    // Exit the outer context we entered for setting timeout
                    Context.exit()
                }
            }
        }
    }
}
