package org.bloomreach.inspections.core.inspections.performance

import org.bloomreach.inspections.core.engine.*

/**
 * Detects usage of the _maxreflevel parameter, which is deprecated and will be
 * removed in Page Model API 2.0. It causes severe performance degradation by
 * recursively expanding document references in the Delivery API response.
 */
class MaxRefLevelUsageInspection : Inspection() {
    override val id = "performance.maxreflevel-usage"
    override val name = "Usage of _maxreflevel Parameter"
    override val description = """
        Detects usage of the _maxreflevel parameter in code and configuration.

        _maxreflevel causes the Delivery API to recursively follow document references
        to the specified depth, inflating response payloads and significantly increasing
        server processing time. Support will be removed in Page Model API 2.0.

        **Problem:**
        - Deep reference expansion creates large, slow API responses
        - Tight coupling between content model depth and performance
        - Removal in Page Model 2.0 will break the site without content model changes

        **Solution:**
        Redesign the content model to avoid deep reference chains. Use explicit
        content beans or separate targeted API calls instead of recursive expansion.

        Reference: https://documentation.bloomreach.com/content/docs/delivery-api-reference
    """.trimIndent()
    override val category = InspectionCategory.PERFORMANCE
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA, FileType.XML, FileType.PROPERTIES, FileType.YAML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        if (isTestFile(context)) return emptyList()
        val issues = mutableListOf<InspectionIssue>()
        context.fileContent.lines().forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()
            if (!isCommentLine(trimmed) && trimmed.contains("_maxreflevel", ignoreCase = true)) {
                issues.add(InspectionIssue(
                    inspection = this,
                    file = context.file,
                    severity = severity,
                    message = "_maxreflevel parameter detected — deprecated, will be removed in Page Model 2.0",
                    description = description,
                    range = TextRange.wholeLine(lineNum),
                    metadata = mapOf("line" to trimmed)
                ))
            }
        }
        return issues
    }

    private fun isCommentLine(line: String) =
        line.startsWith("//") || line.startsWith("#") || line.startsWith("*") || line.startsWith("<!--")

    private fun isTestFile(context: InspectionContext): Boolean {
        val name = context.file.name.lowercase()
        return name.endsWith("test.java") || name.endsWith("tests.java") ||
               name.endsWith("test.kt") || name.endsWith("tests.kt")
    }
}
