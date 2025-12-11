package org.bloomreach.inspections.core.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TextRangeTest {

    @Test
    fun `should create valid text range`() {
        val range = TextRange(1, 0, 5, 10)

        assertEquals(1, range.startLine)
        assertEquals(0, range.startColumn)
        assertEquals(5, range.endLine)
        assertEquals(10, range.endColumn)
    }

    @Test
    fun `should detect single line range`() {
        val singleLine = TextRange(5, 0, 5, 100)
        val multiLine = TextRange(5, 0, 10, 0)

        assertTrue(singleLine.isSingleLine())
        assertFalse(multiLine.isSingleLine())
    }

    @Test
    fun `should calculate line count correctly`() {
        assertEquals(1, TextRange(5, 0, 5, 100).lineCount())
        assertEquals(5, TextRange(1, 0, 5, 0).lineCount())
        assertEquals(10, TextRange(10, 0, 19, 50).lineCount())
    }

    @Test
    fun `should create single line range using factory`() {
        val range = TextRange.singleLine(10, 5, 20)

        assertEquals(10, range.startLine)
        assertEquals(10, range.endLine)
        assertEquals(5, range.startColumn)
        assertEquals(20, range.endColumn)
        assertTrue(range.isSingleLine())
    }

    @Test
    fun `should create whole line range`() {
        val range = TextRange.wholeLine(15)

        assertEquals(15, range.startLine)
        assertEquals(15, range.endLine)
        assertEquals(0, range.startColumn)
        assertTrue(range.isSingleLine())
    }

    @Test
    fun `should reject invalid ranges`() {
        assertThrows(IllegalArgumentException::class.java) {
            TextRange(0, 0, 1, 0) // Start line must be positive
        }

        assertThrows(IllegalArgumentException::class.java) {
            TextRange(5, 0, 3, 0) // End line before start line
        }

        assertThrows(IllegalArgumentException::class.java) {
            TextRange(1, -1, 1, 10) // Negative column
        }
    }
}
