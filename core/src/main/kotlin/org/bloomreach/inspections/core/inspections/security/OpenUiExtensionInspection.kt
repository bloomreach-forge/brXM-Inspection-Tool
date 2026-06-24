package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.engine.*

/**
 * Detects Open UI Extension plugin usage in content type XML configurations.
 * Open UI Extensions can display arbitrary iFrames in the Content Perspective;
 * all usages should be audited for what values they save and what they display.
 */
class OpenUiExtensionInspection : Inspection() {
    override val id = "security.open-ui-extension"
    override val name = "Open UI Extension Detected — Manual Review Required"
    override val description = """
        Detects Open UI Extension plugin references in content type configurations.

        Open UI Extensions display iFrames inside the brXM Content Perspective and can
        save arbitrary values to content fields. Without proper review, they may:
        - Display malicious or uncontrolled external content inside the CMS
        - Save unexpected or unsafe values to content nodes
        - Introduce XSS or data injection vectors via the external page

        **This inspection flags for manual review, not as a definitive bug.**

        **Required Actions:**
        1. Identify what URL each Open UI extension loads
        2. Verify the loaded page is controlled and trusted
        3. Confirm what values the extension saves to content fields
        4. Ensure the extension is actively used (remove unused ones)

        Reference: https://documentation.bloomreach.com/content/docs/open-ui-extensions
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.XML)

    private val openUiPatterns = listOf(
        Regex("""[Oo]pen[Uu]i[A-Za-z]*[Pp]lugin"""),
        Regex("""[Oo]pen[Uu]i[A-Za-z]*[Ff]ield"""),
        Regex("""openui""", RegexOption.IGNORE_CASE)
    )

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        if (!content.contains("openui", ignoreCase = true) && !content.contains("OpenUi")) {
            return emptyList()
        }

        val issues = mutableListOf<InspectionIssue>()
        val classValuePattern = Regex("""<sv:value>([^<]*[Oo]pen[Uu]i[^<]*)</sv:value>""")

        content.lines().forEachIndexed { idx, line ->
            val match = classValuePattern.find(line) ?: return@forEachIndexed
            val className = match.groupValues[1].trim()
            issues.add(InspectionIssue(
                inspection = this,
                file = context.file,
                severity = severity,
                message = "Open UI Extension detected: '$className' — audit what iFrame it loads and what values it saves",
                description = description,
                range = TextRange.wholeLine(idx + 1),
                metadata = mapOf("pluginClass" to className)
            ))
        }
        return issues
    }
}
