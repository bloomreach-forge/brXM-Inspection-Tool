package org.bloomreach.inspections.core.engine

/**
 * Represents a range of text in a file (line and column based)
 */
data class TextRange(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int
) {
    init {
        require(startLine > 0) { "Start line must be positive, got: $startLine" }
        require(endLine >= startLine) { "End line ($endLine) must be >= start line ($startLine)" }
        require(startColumn >= 0) { "Start column must be non-negative, got: $startColumn" }
        require(endColumn >= 0) { "End column must be non-negative, got: $endColumn" }
    }

    /**
     * Check if this range is a single line
     */
    fun isSingleLine(): Boolean = startLine == endLine

    /**
     * Get the number of lines this range spans
     */
    fun lineCount(): Int = endLine - startLine + 1

    companion object {
        /**
         * Create a range for a single line
         */
        fun singleLine(line: Int, startColumn: Int = 0, endColumn: Int = Int.MAX_VALUE): TextRange {
            return TextRange(line, startColumn, line, endColumn)
        }

        /**
         * Create a range for an entire line (full width)
         */
        fun wholeLine(line: Int): TextRange {
            return singleLine(line, 0, Int.MAX_VALUE)
        }
    }
}
