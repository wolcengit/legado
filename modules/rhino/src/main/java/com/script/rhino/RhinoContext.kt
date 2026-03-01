package com.script.rhino

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import kotlin.coroutines.CoroutineContext

class RhinoContext(factory: ContextFactory) : Context(factory) {

    var coroutineContext: CoroutineContext? = null
    var allowScriptRun = false
    var recursiveCount = 0

    /**
     * Execution timeout in milliseconds. Default is 30 seconds.
     * Set to 0 or negative to disable timeout.
     */
    var executionTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS

    /**
     * Timestamp (System.currentTimeMillis) when script execution started.
     * Set to 0 when no script is running.
     */
    var executionStartTime: Long = 0L

    fun startExecutionTimer() {
        executionStartTime = System.currentTimeMillis()
    }

    fun clearExecutionTimer() {
        executionStartTime = 0L
    }

    @Throws(RhinoInterruptError::class, ScriptTimeoutException::class)
    fun ensureActive() {
        try {
            coroutineContext?.ensureActive()
        } catch (e: CancellationException) {
            throw RhinoInterruptError(e)
        }
        checkTimeout()
    }

    private fun checkTimeout() {
        val timeout = executionTimeoutMillis
        val startTime = executionStartTime
        if (timeout > 0 && startTime > 0) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > timeout) {
                throw ScriptTimeoutException(timeout)
            }
        }
    }

    @Throws(RhinoRecursionError::class)
    fun checkRecursive() {
        if (recursiveCount >= 10) {
            throw RhinoRecursionError()
        }
    }

    companion object {
        /** Default script execution timeout: 30 seconds */
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }

}
