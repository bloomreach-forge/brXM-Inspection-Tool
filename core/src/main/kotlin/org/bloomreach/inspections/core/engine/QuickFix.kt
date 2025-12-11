package org.bloomreach.inspections.core.engine

/**
 * Represents a quick fix that can automatically resolve an inspection issue.
 *
 * Quick fixes are optional and may not be available for all inspection types.
 */
interface QuickFix {
    /** Display name for the quick fix */
    val name: String

    /** Detailed description of what the fix does */
    val description: String

    /**
     * Apply the quick fix to resolve the issue.
     *
     * @param context Context containing file and issue information
     */
    fun apply(context: QuickFixContext)
}

/**
 * Context provided when applying a quick fix
 */
data class QuickFixContext(
    val file: VirtualFile,
    val range: TextRange,
    val issue: InspectionIssue,
    val additionalData: Map<String, Any> = emptyMap()
)

/**
 * Base implementation of QuickFix for common patterns
 */
abstract class BaseQuickFix(
    override val name: String,
    override val description: String
) : QuickFix {

    override fun toString(): String = name
}
