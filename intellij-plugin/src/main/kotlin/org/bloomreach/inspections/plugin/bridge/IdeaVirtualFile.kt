package org.bloomreach.inspections.plugin.bridge

import com.intellij.openapi.vfs.VirtualFile as IdeaVFile
import org.bloomreach.inspections.core.engine.VirtualFile
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Bridges IntelliJ's VirtualFile to our core VirtualFile interface.
 *
 * This allows the core inspection engine to work with IDE files
 * without depending on IntelliJ APIs.
 */
class IdeaVirtualFile(private val ideaFile: IdeaVFile) : VirtualFile {

    override val path: Path
        get() = Paths.get(ideaFile.path)

    override val name: String
        get() = ideaFile.name

    override val extension: String
        get() = ideaFile.extension ?: ""

    override fun readText(): String {
        return String(ideaFile.contentsToByteArray(), ideaFile.charset)
    }

    override fun exists(): Boolean {
        return ideaFile.exists()
    }

    override fun size(): Long {
        return ideaFile.length
    }

    override fun lastModified(): Long {
        return ideaFile.timeStamp
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdeaVirtualFile) return false
        return ideaFile.path == other.ideaFile.path
    }

    override fun hashCode(): Int {
        return ideaFile.path.hashCode()
    }

    override fun toString(): String {
        return "IdeaVirtualFile(path=${ideaFile.path})"
    }
}
