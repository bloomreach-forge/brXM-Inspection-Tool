package org.bloomreach.inspections.core.parsers

import org.bloomreach.inspections.core.engine.FileType

/**
 * Base interface for file parsers
 */
interface Parser<T> {
    /**
     * Parse file content into an AST
     */
    fun parse(content: String): ParseResult<T>

    /**
     * Check if this parser supports the given file type
     */
    fun supports(fileType: FileType): Boolean
}
