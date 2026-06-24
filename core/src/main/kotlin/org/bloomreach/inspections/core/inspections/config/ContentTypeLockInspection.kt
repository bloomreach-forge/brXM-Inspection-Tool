package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*

/**
 * Detects locked content types in brXM namespace/document type XML files.
 * Locked content types (yellow triangles in the CMS) indicate uncommitted changes
 * that have not been submitted to core, blocking other development work.
 */
class ContentTypeLockInspection : Inspection() {
    override val id = "config.content-type-lock"
    override val name = "Locked Content Type Detected"
    override val description = """
        Detects content type definitions that are in a locked/draft state.

        A locked content type (shown with a yellow triangle in the CMS Type Editor)
        has unpublished changes that have not been submitted to core. This:
        - Blocks other developers from editing the same content type
        - Indicates the content type is out of sync between UI and bootstrap files
        - Can prevent proper deployment if bootstrap files don't reflect the locked state

        **Solution:**
        Open the Type Editor, review the pending changes, and either:
        - Submit the content type to core (commit the changes)
        - Revert the changes to remove the lock

        Ensure no content types have active locks (no yellow triangles) before
        deploying to production.

        Reference: https://documentation.bloomreach.com/content/docs/document-types
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.XML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        if (!content.contains("lock", ignoreCase = true)) return emptyList()

        val issues = mutableListOf<InspectionIssue>()
        val lines = content.lines()

        // Detect hipposys:locked = true or jcr:lockOwner presence
        val lockPropertyPattern = Regex("""sv:name="([^"]*(?:lock|Lock)[^"]*)"""")

        lines.forEachIndexed { idx, line ->
            val match = lockPropertyPattern.find(line) ?: return@forEachIndexed
            val propName = match.groupValues[1]

            // Check for explicit true value on locked property, or presence of lockOwner
            if (propName.contains("lock", ignoreCase = true)) {
                val window = lines.drop(idx).take(5).joinToString("\n")
                val hasActiveValue = when {
                    propName == "jcr:lockOwner" -> true  // presence alone means locked
                    else -> Regex("""<sv:value>(true|yes|1)</sv:value>""", RegexOption.IGNORE_CASE).containsMatchIn(window)
                }
                if (hasActiveValue) {
                    issues.add(InspectionIssue(
                        inspection = this,
                        file = context.file,
                        severity = severity,
                        message = "Content type lock detected ('$propName') — submit or revert pending changes",
                        description = description,
                        range = TextRange.wholeLine(idx + 1),
                        metadata = mapOf("propertyName" to propName)
                    ))
                }
            }
        }
        return issues
    }
}
