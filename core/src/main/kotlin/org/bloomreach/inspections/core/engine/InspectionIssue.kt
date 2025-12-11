package org.bloomreach.inspections.core.engine

/**
 * Represents a single issue found by an inspection.
 */
data class InspectionIssue(
    /** The inspection that detected this issue */
    val inspection: Inspection,

    /** The file where the issue was found */
    val file: VirtualFile,

    /** Severity level of the issue */
    val severity: Severity,

    /** Short message describing the issue */
    val message: String,

    /** Detailed description with context and recommendations */
    val description: String,

    /** Location of the issue in the file */
    val range: TextRange,

    /** Available quick fixes for this issue */
    val quickFixes: List<QuickFix> = emptyList(),

    /** Additional metadata about the issue */
    val metadata: Map<String, Any> = emptyMap()
) {
    /** Get the inspection category */
    val category: InspectionCategory
        get() = inspection.category

    /** Get the inspection ID */
    val inspectionId: String
        get() = inspection.id

    override fun toString(): String {
        return "[$severity] ${inspection.id} at ${file.name}:${range.startLine} - $message"
    }
}
