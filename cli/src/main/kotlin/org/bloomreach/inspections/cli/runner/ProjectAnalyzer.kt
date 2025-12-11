package org.bloomreach.inspections.cli.runner

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.*
import java.nio.file.Path

/**
 * Analyzes a project by running inspections on files.
 */
class ProjectAnalyzer(private val config: InspectionConfig) {

    private val registry = InspectionRegistry()
    private val executor = InspectionExecutor(registry, config)

    private var results: InspectionResults? = null

    init {
        // Discover and register all inspections
        registry.discoverInspections()
    }

    /**
     * Analyze a project.
     *
     * @param projectRoot Root directory of the project
     * @param files Files to analyze
     * @param progressCallback Callback invoked for each file analyzed
     */
    fun analyze(
        projectRoot: Path,
        files: List<VirtualFile>,
        progressCallback: (String) -> Unit = {}
    ) {
        // Get applicable inspections
        val inspections = registry.getAllInspections()
            .filter { config.isEnabled(it.id) }

        if (inspections.isEmpty()) {
            throw IllegalStateException("No inspections are enabled")
        }

        // Run analysis - convert callback signature
        results = executor.executeAll(
            projectRoot = projectRoot,
            files = files,
            progressCallback = { _, fileName -> progressCallback(fileName) }
        )
    }

    /**
     * Get analysis results.
     */
    fun getResults(): InspectionResults {
        return results ?: throw IllegalStateException("No analysis has been run")
    }

    /**
     * Get project statistics.
     */
    fun getStatistics(): ProjectStatistics {
        val results = getResults()
        return ProjectStatistics(
            filesAnalyzed = results.issues.map { it.file.path }.distinct().size,
            inspectionsRun = registry.size(),
            totalIssues = results.totalIssues,
            errorCount = results.errorCount,
            warningCount = results.warningCount,
            infoCount = results.infoCount,
            hintCount = results.hintCount
        )
    }
}

/**
 * Project analysis statistics.
 */
data class ProjectStatistics(
    val filesAnalyzed: Int,
    val inspectionsRun: Int,
    val totalIssues: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val hintCount: Int
)
