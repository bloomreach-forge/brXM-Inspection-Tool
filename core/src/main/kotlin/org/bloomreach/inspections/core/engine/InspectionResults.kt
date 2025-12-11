package org.bloomreach.inspections.core.engine

/**
 * Aggregated results from running inspections.
 */
class InspectionResults {
    private val _issues = mutableListOf<InspectionIssue>()
    private val _errors = mutableListOf<InspectionError>()

    /** All issues found */
    val issues: List<InspectionIssue>
        get() = _issues.toList()

    /** Errors that occurred during inspection */
    val errors: List<InspectionError>
        get() = _errors.toList()

    /** Number of files scanned */
    var filesScanned: Int = 0

    /** Number of inspections run */
    var inspectionsRun: Int = 0

    /**
     * Add a single issue
     */
    fun addIssue(issue: InspectionIssue) {
        _issues.add(issue)
    }

    /**
     * Add multiple issues
     */
    fun addAll(issues: List<InspectionIssue>) {
        _issues.addAll(issues)
    }

    /**
     * Record an error that occurred during inspection
     */
    fun addError(file: VirtualFile, inspection: Inspection, exception: Throwable) {
        _errors.add(InspectionError(file, inspection, exception))
    }

    // Aggregate statistics

    val totalIssues: Int
        get() = _issues.size

    val errorCount: Int
        get() = _issues.count { it.severity == Severity.ERROR }

    val warningCount: Int
        get() = _issues.count { it.severity == Severity.WARNING }

    val infoCount: Int
        get() = _issues.count { it.severity == Severity.INFO }

    val hintCount: Int
        get() = _issues.count { it.severity == Severity.HINT }

    /**
     * Check if there are any ERROR-level issues
     */
    fun hasErrors(): Boolean = errorCount > 0

    /**
     * Group issues by category
     */
    val issuesByCategory: Map<InspectionCategory, List<InspectionIssue>>
        get() = _issues.groupBy { it.category }

    /**
     * Group issues by severity
     */
    val issuesBySeverity: Map<Severity, List<InspectionIssue>>
        get() = _issues.groupBy { it.severity }

    /**
     * Group issues by file
     */
    val issuesByFile: Map<VirtualFile, List<InspectionIssue>>
        get() = _issues.groupBy { it.file }

    /**
     * Get issues for a specific file
     */
    fun getIssuesForFile(file: VirtualFile): List<InspectionIssue> {
        return _issues.filter { it.file == file }
    }

    /**
     * Get issues for a specific inspection
     */
    fun getIssuesForInspection(inspectionId: String): List<InspectionIssue> {
        return _issues.filter { it.inspectionId == inspectionId }
    }

    /**
     * Get issues with minimum severity
     */
    fun getIssuesWithMinSeverity(minSeverity: Severity): List<InspectionIssue> {
        return _issues.filter { it.severity.priority >= minSeverity.priority }
    }

    override fun toString(): String {
        return "InspectionResults(issues=$totalIssues, errors=${_errors.size}, " +
                "filesScanned=$filesScanned, inspectionsRun=$inspectionsRun)"
    }
}

/**
 * Represents an error that occurred while running an inspection
 */
data class InspectionError(
    val file: VirtualFile,
    val inspection: Inspection,
    val exception: Throwable
) {
    val message: String
        get() = exception.message ?: "Unknown error"

    override fun toString(): String {
        return "InspectionError(inspection=${inspection.id}, file=${file.name}, error=$message)"
    }
}
