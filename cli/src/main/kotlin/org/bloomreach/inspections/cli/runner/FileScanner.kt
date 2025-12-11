package org.bloomreach.inspections.cli.runner

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.VirtualFile
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.streams.toList

/**
 * Scans a project directory for files to analyze.
 *
 * Respects include/exclude patterns from configuration.
 */
class FileScanner(private val config: InspectionConfig) {

    /**
     * Scan a project directory for files to analyze.
     *
     * @param projectRoot Root directory of the project
     * @return List of virtual files to analyze
     */
    fun scan(projectRoot: Path): List<VirtualFile> {
        val matchedFiles = mutableListOf<Path>()

        // Walk the directory tree
        Files.walk(projectRoot).use { paths ->
            paths
                .filter { it.isRegularFile() }
                .filter { shouldInclude(projectRoot, it) }
                .forEach { matchedFiles.add(it) }
        }

        // Convert to VirtualFile
        return matchedFiles.map { PathVirtualFile(it) }
    }

    /**
     * Check if a file should be included based on patterns.
     */
    private fun shouldInclude(projectRoot: Path, file: Path): Boolean {
        val relativePath = projectRoot.relativize(file).toString()

        // Check exclude patterns first
        if (config.excludePaths.any { pattern -> matches(relativePath, pattern) }) {
            return false
        }

        // Check include patterns
        return config.includePaths.any { pattern -> matches(relativePath, pattern) }
    }

    /**
     * Match a path against a glob pattern.
     */
    private fun matches(path: String, globPattern: String): Boolean {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$globPattern")
        val pathToMatch = Path.of(path)
        return matcher.matches(pathToMatch)
    }
}

/**
 * VirtualFile implementation wrapping a Path.
 */
class PathVirtualFile(private val filePath: Path) : VirtualFile {

    override val path: Path
        get() = filePath

    override val name: String
        get() = filePath.fileName.toString()

    override val extension: String
        get() = name.substringAfterLast('.', "")

    override fun readText(): String {
        return Files.readString(filePath)
    }

    override fun exists(): Boolean {
        return Files.exists(filePath)
    }

    override fun size(): Long {
        return Files.size(filePath)
    }

    override fun lastModified(): Long {
        return Files.getLastModifiedTime(filePath).toMillis()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathVirtualFile) return false
        return filePath == other.filePath
    }

    override fun hashCode(): Int {
        return filePath.hashCode()
    }

    override fun toString(): String {
        return "PathVirtualFile(path=$filePath)"
    }
}
