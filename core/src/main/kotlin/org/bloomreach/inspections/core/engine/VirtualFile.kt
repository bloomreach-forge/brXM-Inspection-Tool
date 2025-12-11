package org.bloomreach.inspections.core.engine

import java.nio.file.Path

/**
 * Abstract file representation that works in both IDE and CLI contexts.
 *
 * This abstraction allows the inspection engine to work with files
 * without depending on specific IDE or filesystem APIs.
 */
interface VirtualFile {
    /** Absolute path to the file */
    val path: Path

    /** File name with extension */
    val name: String

    /** File extension (without dot) */
    val extension: String

    /** Read the entire file content as text */
    fun readText(): String

    /** Check if file exists */
    fun exists(): Boolean

    /** Get file size in bytes */
    fun size(): Long

    /** Get last modified timestamp */
    fun lastModified(): Long
}

/**
 * Implementation of VirtualFile for the filesystem (used by CLI)
 */
class FileSystemVirtualFile(override val path: Path) : VirtualFile {
    override val name: String
        get() = path.fileName.toString()

    override val extension: String
        get() = name.substringAfterLast('.', "")

    override fun readText(): String {
        return path.toFile().readText()
    }

    override fun exists(): Boolean {
        return path.toFile().exists()
    }

    override fun size(): Long {
        return path.toFile().length()
    }

    override fun lastModified(): Long {
        return path.toFile().lastModified()
    }

    override fun toString(): String = path.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileSystemVirtualFile) return false
        return path == other.path
    }

    override fun hashCode(): Int = path.hashCode()
}
