package org.bloomreach.inspections.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.config.YamlConfigLoader
import org.bloomreach.inspections.core.engine.InspectionCache
import org.bloomreach.inspections.core.engine.InspectionRegistry
import org.bloomreach.inspections.core.model.ProjectIndex
import java.nio.file.Paths

/**
 * Project-level service that maintains shared state for Bloomreach inspections.
 *
 * This service is created once per project and shared across all inspections.
 * It manages:
 * - Configuration (loaded from .brxm-inspections.yaml if present)
 * - Project-wide index (for cross-file analysis like UUID conflicts)
 * - Parse cache (to avoid re-parsing files)
 * - Inspection registry
 */
@Service(Service.Level.PROJECT)
class BrxmInspectionService(private val project: Project) : Disposable {

    /**
     * Configuration for inspections
     * Loaded from .brxm-inspections.yaml in project root, or default if not found
     */
    val config: InspectionConfig = loadConfig()

    /**
     * Cache for parsed ASTs and other expensive computations
     */
    val cache: InspectionCache = InspectionCache()

    /**
     * Project-wide index for cross-file analysis
     *
     * This is used by inspections like BootstrapUuidConflictInspection
     * to detect conflicts across multiple files.
     */
    val projectIndex: ProjectIndex = ProjectIndex()

    /**
     * Registry of all available inspections
     */
    val registry: InspectionRegistry = InspectionRegistry().apply {
        // Register all built-in inspections
        discoverInspections()
    }

    /**
     * Rebuild the project index
     *
     * This should be called when the project structure changes significantly.
     */
    fun rebuildIndex() {
        projectIndex.clear()
        // The index will be populated incrementally as files are inspected
    }

    /**
     * Clear the parse cache
     *
     * This should be called when files change externally or after refactoring.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Get statistics about the current state
     */
    fun getStatistics(): Statistics {
        return Statistics(
            indexedFiles = projectIndex.size(),
            cachedFiles = cache.size(),
            registeredInspections = registry.getAllInspections().size
        )
    }

    /**
     * Load configuration from project root or use default
     */
    private fun loadConfig(): InspectionConfig {
        val projectPath = project.basePath ?: return InspectionConfig.default()
        val projectRoot = Paths.get(projectPath)
        return try {
            YamlConfigLoader.loadOrDefault(projectRoot)
        } catch (e: Exception) {
            // If config loading fails, use default and log error
            InspectionConfig.default()
        }
    }

    override fun dispose() {
        // Clean up resources
        cache.clear()
        projectIndex.clear()
    }

    data class Statistics(
        val indexedFiles: Int,
        val cachedFiles: Int,
        val registeredInspections: Int
    )
}
