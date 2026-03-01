package io.legado.app

/**
 * Extracts the sensitive class name detection logic from SecurityVerifyPlugin.gradle
 * into a testable Kotlin function.
 *
 * The patterns match the SENSITIVE_CLASS_PATTERNS defined in the Gradle plugin:
 * - java.lang.Runtime
 * - java.lang.ProcessBuilder
 * - java.net.URLClassLoader
 * - dalvik.system.*
 */
object SensitiveClassNameDetector {

    private val SENSITIVE_PATTERNS = listOf(
        Regex("""\bjava\.lang\.Runtime\b"""),
        Regex("""\bjava\.lang\.ProcessBuilder\b"""),
        Regex("""\bjava\.net\.URLClassLoader\b"""),
        Regex("""\bdalvik\.system\.\w+"""),
    )

    /**
     * Detect sensitive class names from a list of class name strings.
     *
     * @param classNames list of class names to scan
     * @return list of class names that match any sensitive pattern
     */
    fun detect(classNames: List<String>): List<String> {
        return classNames.filter { className ->
            SENSITIVE_PATTERNS.any { pattern -> pattern.containsMatchIn(className) }
        }
    }
}
