package org.bloomreach.inspections.core.model

import org.bloomreach.inspections.core.engine.VirtualFile
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-wide index for cross-file analysis.
 *
 * Stores metadata about the project structure, symbols, and relationships
 * between files. Used for inspections that need to check consistency across
 * multiple files (e.g., UUID conflicts, class references).
 */
class ProjectIndex {
    private val files = mutableListOf<VirtualFile>()
    private val filesByPath = ConcurrentHashMap<Path, VirtualFile>()

    // UUID tracking (for bootstrap conflict detection)
    private val uuids = ConcurrentHashMap<String, MutableList<UuidDefinition>>()

    // Java class index (for class references)
    private val javaClasses = ConcurrentHashMap<String, JavaClassInfo>()

    /**
     * Add a file to the index
     */
    fun addFile(file: VirtualFile) {
        files.add(file)
        filesByPath[file.path] = file
    }

    /**
     * Find files matching a glob pattern
     */
    fun findFiles(pattern: String): List<VirtualFile> {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        return files.filter { matcher.matches(it.path) }
    }

    /**
     * Get a file by path
     */
    fun getFile(path: Path): VirtualFile? {
        return filesByPath[path]
    }

    /**
     * Record a UUID definition
     */
    fun recordUuid(uuid: String, file: VirtualFile, line: Int) {
        uuids.getOrPut(uuid) { mutableListOf() }.add(
            UuidDefinition(uuid, file, line)
        )
    }

    /**
     * Find UUID conflicts (same UUID in multiple files)
     */
    fun findUuidConflicts(uuid: String): List<UuidDefinition> {
        return uuids[uuid]?.takeIf { it.size > 1 } ?: emptyList()
    }

    /**
     * Get all UUIDs defined in the project
     */
    fun getAllUuids(): Map<String, List<UuidDefinition>> {
        return uuids.toMap()
    }

    /**
     * Index a Java file (extract class info)
     */
    fun indexJavaFile(file: VirtualFile) {
        // To be implemented with JavaParser
    }

    /**
     * Index an XML file (extract node types, UUIDs)
     */
    fun indexXmlFile(file: VirtualFile) {
        // To be implemented with XML parser
    }

    /**
     * Get Java class information
     */
    fun getJavaClass(fullyQualifiedName: String): JavaClassInfo? {
        return javaClasses[fullyQualifiedName]
    }

    /**
     * Clear the index
     */
    fun clear() {
        files.clear()
        filesByPath.clear()
        uuids.clear()
        javaClasses.clear()
    }

    fun size(): Int = files.size
}

/**
 * Information about a UUID definition
 */
data class UuidDefinition(
    val uuid: String,
    val file: VirtualFile,
    val line: Int
)

/**
 * Information about a Java class
 */
data class JavaClassInfo(
    val fullyQualifiedName: String,
    val packageName: String,
    val simpleName: String,
    val file: VirtualFile,
    val isInterface: Boolean = false,
    val isAbstract: Boolean = false,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList()
)
