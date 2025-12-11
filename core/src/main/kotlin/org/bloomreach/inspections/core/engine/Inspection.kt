package org.bloomreach.inspections.core.engine

/**
 * Base class for all inspections.
 *
 * Subclasses implement specific checks for Bloom reach best practices and common issues.
 *
 * To create a new inspection:
 * 1. Extend this class
 * 2. Implement the required abstract properties
 * 3. Implement the inspect() method
 * 4. Optionally override getQuickFixes() to provide automatic fixes
 * 5. Register via ServiceLoader in META-INF/services
 */
abstract class Inspection {
    /** Unique identifier for this inspection (e.g., "repository.session-leak") */
    abstract val id: String

    /** Human-readable name displayed in reports */
    abstract val name: String

    /** Detailed description of what this inspection checks */
    abstract val description: String

    /** Category this inspection belongs to */
    abstract val category: InspectionCategory

    /** Default severity level for issues found by this inspection */
    abstract val severity: Severity

    /** File types this inspection can analyze */
    abstract val applicableFileTypes: Set<FileType>

    /**
     * Perform the inspection on the given context.
     *
     * @param context Inspection context with file, configuration, and project state
     * @return List of issues found (empty if no issues)
     */
    abstract fun inspect(context: InspectionContext): List<InspectionIssue>

    /**
     * Get quick fixes for a specific issue.
     *
     * Override this method to provide automatic fixes for issues.
     *
     * @param issue The issue to provide fixes for
     * @return List of available quick fixes (empty if none available)
     */
    open fun getQuickFixes(issue: InspectionIssue): List<QuickFix> = emptyList()

    /**
     * Check if this inspection is applicable to the given file type.
     */
    fun isApplicable(fileType: FileType): Boolean {
        return fileType in applicableFileTypes
    }

    /**
     * Check if this inspection is applicable to the given file.
     */
    fun isApplicable(file: VirtualFile): Boolean {
        val fileType = FileType.fromFilename(file.name) ?: return false
        return isApplicable(fileType)
    }

    override fun toString(): String = "Inspection($id)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Inspection) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
