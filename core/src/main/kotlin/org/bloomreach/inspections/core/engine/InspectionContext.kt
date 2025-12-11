package org.bloomreach.inspections.core.engine

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import java.nio.file.Path

/**
 * Context provided to inspections during execution.
 *
 * Contains all information needed to perform analysis:
 * - File being analyzed
 * - Project configuration
 * - Cross-file indexes
 * - Caching
 */
data class InspectionContext(
    /** Root directory of the project being analyzed */
    val projectRoot: Path,

    /** The file being inspected */
    val file: VirtualFile,

    /** Content of the file */
    val fileContent: String,

    /** Detected language/file type */
    val language: FileType,

    /** Configuration for this inspection run */
    val config: InspectionConfig,

    /** Cache for parsed ASTs and other expensive computations */
    val cache: InspectionCache,

    /** Project-wide index for cross-file analysis */
    val projectIndex: ProjectIndex
) {
    /**
     * Find files matching a glob pattern relative to project root
     */
    fun findRelatedFiles(pattern: String): List<VirtualFile> {
        return projectIndex.findFiles(pattern)
    }

    /**
     * Get the path relative to project root
     */
    fun getRelativePath(): Path {
        return projectRoot.relativize(file.path)
    }

    /**
     * Check if a specific inspection is enabled
     */
    fun isInspectionEnabled(inspectionId: String): Boolean {
        return config.isEnabled(inspectionId)
    }
}

/**
 * Simple cache for inspection results and parsed data
 */
class InspectionCache {
    private val cache = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = cache[key] as? T

    fun <T> put(key: String, value: T) {
        cache[key] = value as Any
    }

    fun <T> getOrPut(key: String, producer: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(key) { producer() as Any } as T
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size
}
