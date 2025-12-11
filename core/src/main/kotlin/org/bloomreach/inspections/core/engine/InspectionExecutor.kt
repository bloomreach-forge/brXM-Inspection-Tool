package org.bloomreach.inspections.core.engine

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Executes inspections on files with support for parallel processing.
 *
 * Features:
 * - Parallel execution for performance
 * - Progress callbacks for UI/CLI feedback
 * - Error handling and isolation (one inspection failure doesn't stop others)
 * - Configurable thread pool
 */
class InspectionExecutor(
    private val registry: InspectionRegistry,
    private val config: InspectionConfig,
    private val executorService: ExecutorService = createDefaultExecutor(config)
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(InspectionExecutor::class.java)

    /**
     * Execute all applicable inspections on a collection of files.
     *
     * @param projectRoot Root directory of the project
     * @param files Files to analyze
     * @param progressCallback Optional callback for progress updates (progress 0.0-1.0, current file)
     * @return Aggregated inspection results
     */
    fun executeAll(
        projectRoot: Path,
        files: List<VirtualFile>,
        progressCallback: (progress: Double, current: String) -> Unit = { _, _ -> }
    ): InspectionResults {
        logger.info("Starting inspection of ${files.size} files")

        val results = InspectionResults()
        val projectIndex = buildProjectIndex(projectRoot, files)
        val cache = InspectionCache()

        val totalWork = files.size
        val completed = AtomicInteger(0)

        if (config.parallel && files.size > 1) {
            // Parallel execution
            executeParallel(projectRoot, files, projectIndex, cache, results) { current ->
                val done = completed.incrementAndGet()
                progressCallback(done.toDouble() / totalWork, current)
            }
        } else {
            // Sequential execution (for single file or when parallel is disabled)
            files.forEach { file ->
                executeFile(projectRoot, file, projectIndex, cache, results)
                val done = completed.incrementAndGet()
                progressCallback(done.toDouble() / totalWork, file.name)
            }
        }

        results.filesScanned = files.size
        results.inspectionsRun = registry.size()

        logger.info("Inspection complete: ${results.totalIssues} issues found in ${files.size} files")

        return results
    }

    /**
     * Execute inspections on a single file (optimized for IDE use).
     *
     * @param projectRoot Root directory of the project
     * @param file File to analyze
     * @param projectIndex Optional project index for cross-file analysis
     * @return Inspection results for this file
     */
    fun executeIncremental(
        projectRoot: Path,
        file: VirtualFile,
        projectIndex: ProjectIndex = ProjectIndex()
    ): InspectionResults {
        val results = InspectionResults()
        val cache = InspectionCache()

        executeFile(projectRoot, file, projectIndex, cache, results)

        results.filesScanned = 1
        results.inspectionsRun = registry.getApplicableInspections(file).size

        return results
    }

    /**
     * Execute inspections on a single file
     */
    private fun executeFile(
        projectRoot: Path,
        file: VirtualFile,
        projectIndex: ProjectIndex,
        cache: InspectionCache,
        results: InspectionResults
    ) {
        // Detect file type
        val fileType = FileType.fromFilename(file.name)
        if (fileType == null) {
            logger.debug("Skipping file with unknown type: ${file.name}")
            return
        }

        // Get applicable inspections
        val applicableInspections = registry.getApplicableInspections(fileType)
            .filter { config.isEnabled(it.id) }

        if (applicableInspections.isEmpty()) {
            logger.debug("No applicable inspections for ${file.name}")
            return
        }

        // Read file content
        val content = try {
            file.readText()
        } catch (e: Exception) {
            logger.error("Failed to read file: ${file.name}", e)
            return
        }

        // Create inspection context
        val context = InspectionContext(
            projectRoot = projectRoot,
            file = file,
            fileContent = content,
            language = fileType,
            config = config,
            cache = cache,
            projectIndex = projectIndex
        )

        // Execute each inspection
        for (inspection in applicableInspections) {
            try {
                logger.debug("Running inspection ${inspection.id} on ${file.name}")

                val issues = inspection.inspect(context)

                // Filter by minimum severity
                val filteredIssues = issues.filter {
                    it.severity.priority >= config.minSeverity.priority
                }

                results.addAll(filteredIssues)

                logger.debug("Inspection ${inspection.id} found ${filteredIssues.size} issue(s)")

            } catch (e: Exception) {
                logger.error("Inspection ${inspection.id} failed on ${file.name}", e)
                results.addError(file, inspection, e)
            }
        }
    }

    /**
     * Execute inspections in parallel using thread pool
     */
    private fun executeParallel(
        projectRoot: Path,
        files: List<VirtualFile>,
        projectIndex: ProjectIndex,
        cache: InspectionCache,
        results: InspectionResults,
        progressCallback: (current: String) -> Unit
    ) {
        val futures = files.map { file ->
            executorService.submit<InspectionResults> {
                val fileResults = InspectionResults()
                executeFile(projectRoot, file, projectIndex, cache, fileResults)
                progressCallback(file.name)
                fileResults
            }
        }

        // Collect results from all futures
        futures.forEach { future ->
            try {
                val fileResults = future.get()
                synchronized(results) {
                    results.addAll(fileResults.issues)
                    fileResults.errors.forEach { error ->
                        results.addError(error.file, error.inspection, error.exception)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error executing parallel inspection", e)
            }
        }
    }

    /**
     * Build project index for cross-file analysis
     */
    private fun buildProjectIndex(projectRoot: Path, files: List<VirtualFile>): ProjectIndex {
        logger.debug("Building project index from ${files.size} files")

        val index = ProjectIndex()

        files.forEach { file ->
            index.addFile(file)

            // Index file based on type
            when (FileType.fromFilename(file.name)) {
                FileType.JAVA -> index.indexJavaFile(file)
                FileType.XML -> index.indexXmlFile(file)
                else -> {} // Other file types don't need indexing yet
            }
        }

        logger.debug("Project index built: ${index.size()} files")

        return index
    }

    override fun close() {
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        /**
         * Create default executor service based on configuration
         */
        private fun createDefaultExecutor(config: InspectionConfig): ExecutorService {
            val threads = if (config.parallel) config.maxThreads else 1

            return ThreadPoolExecutor(
                threads,
                threads,
                60L,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(),
                ThreadFactory { runnable ->
                    Thread(runnable, "inspection-executor").apply {
                        isDaemon = true
                    }
                }
            )
        }
    }
}
