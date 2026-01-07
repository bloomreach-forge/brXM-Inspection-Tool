package org.bloomreach.inspections.core.config

import org.bloomreach.inspections.core.engine.Severity

/**
 * Configuration for inspection execution.
 */
data class InspectionConfig(
    /** Enable/disable inspections globally */
    var enabled: Boolean = true,

    /** Per-inspection configuration */
    val inspections: Map<String, InspectionSettings> = emptyMap(),

    /** Glob patterns for files to exclude */
    val excludePaths: List<String> = listOf(
        "**target/**",
        "**build/**",
        "**node_modules/**",
        "**.git/**"
    ),

    /** Glob patterns for files to include */
    val includePaths: List<String> = listOf(
        "**/*.java",
        "**/*.xml",
        "**/*.yaml",
        "**/*.yml",
        "**/*.json"
    ),

    /** Minimum severity to report */
    val minSeverity: Severity = Severity.INFO,

    /** Enable parallel execution */
    var parallel: Boolean = true,

    /** Maximum number of threads for parallel execution */
    val maxThreads: Int = Runtime.getRuntime().availableProcessors(),

    /** Enable parse cache to improve performance */
    var cacheEnabled: Boolean = true
) {
    /**
     * Check if a specific inspection is enabled
     */
    fun isEnabled(inspectionId: String): Boolean {
        if (!enabled) return false
        return inspections[inspectionId]?.enabled ?: true
    }

    /**
     * Get severity override for a specific inspection
     */
    fun getSeverity(inspectionId: String): Severity? {
        return inspections[inspectionId]?.severity
    }

    /**
     * Get options for a specific inspection
     */
    fun getOptions(inspectionId: String): Map<String, Any> {
        return inspections[inspectionId]?.options ?: emptyMap()
    }

    companion object {
        /**
         * Default configuration with all inspections enabled
         */
        fun default() = InspectionConfig()

        /**
         * Minimal configuration (errors only)
         */
        fun minimal() = InspectionConfig(
            minSeverity = Severity.ERROR
        )

        /**
         * Strict configuration (all checks, all severities)
         */
        fun strict() = InspectionConfig(
            minSeverity = Severity.HINT
        )
    }
}

/**
 * Per-inspection settings
 */
data class InspectionSettings(
    /** Enable/disable this specific inspection */
    val enabled: Boolean = true,

    /** Override default severity */
    val severity: Severity? = null,

    /** Inspection-specific options */
    val options: Map<String, Any> = emptyMap()
)
