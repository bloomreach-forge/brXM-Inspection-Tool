package org.bloomreach.inspections.core.engine

/**
 * Categories for inspections based on Bloomreach community forum analysis.
 *
 * Priority percentages indicate the frequency of issues in the community.
 */
enum class InspectionCategory(val displayName: String, val priority: Int) {
    /** JCR session management, bootstrap, workflows (40% of community issues) */
    REPOSITORY_TIER("Repository Tier Issues", 40),

    /** HST config, sitemaps, caching, component parameters (25% of issues) */
    CONFIGURATION("Configuration Problems", 25),

    /** Docker, Kubernetes, multi-pod setup (20% of issues) */
    DEPLOYMENT("Deployment Issues", 20),

    /** REST API, SAML, SSO, external services (20% of issues) */
    INTEGRATION("Integration Challenges", 20),

    /** Query optimization, caching, resource usage (15% of issues) */
    PERFORMANCE("Performance Issues", 15),

    /** Authentication, authorization, XSS, credentials (10% of issues) */
    SECURITY("Security Issues", 10),

    /** Code style, best practices, maintainability (5% of issues) */
    CODE_QUALITY("Code Quality", 5);

    companion object {
        /**
         * Get categories sorted by priority (highest first)
         */
        fun byPriority(): List<InspectionCategory> {
            return values().sortedByDescending { it.priority }
        }
    }
}
