package org.bloomreach.inspections.core.engine

/**
 * Severity levels for inspection issues.
 *
 * Ordered by priority from highest (ERROR) to lowest (HINT).
 */
enum class Severity(val priority: Int) {
    /** Critical issues that must be fixed */
    ERROR(4),

    /** Issues that should be fixed */
    WARNING(3),

    /** Informational findings */
    INFO(2),

    /** Suggestions for improvement */
    HINT(1);

    companion object {
        /**
         * Get severity by name, case-insensitive
         */
        fun fromString(name: String): Severity? {
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}
