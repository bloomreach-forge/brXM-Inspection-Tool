package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.engine.*

/**
 * Detects channels with External Preview Tokens enabled. Preview tokens can last
 * indefinitely and expose unpublished content to non-CMS users when left enabled.
 */
class ExternalPreviewTokenInspection : Inspection() {
    override val id = "security.external-preview-token"
    override val name = "External Preview Token Enabled"
    override val description = """
        Detects HST channels or projects with external preview tokens enabled.

        External Preview Tokens allow non-CMS users to preview unpublished content via
        a shared URL. Tokens can be created with no expiry and are often shared broadly.
        If the feature is left enabled, potentially sensitive unpublished content is
        exposed to anyone who holds a token — indefinitely.

        **Risk:**
        - Unpublished content (drafts, embargoed articles) visible to token holders
        - Tokens do not expire by default
        - Often shared to non-technical stakeholders who may further distribute the URL

        **Solution:**
        Audit all channels and projects for enabled preview tokens. Disable the feature
        on any channel that does not actively require it. Revoke any existing tokens
        before disabling.

        Reference: https://documentation.bloomreach.com/content/docs/preview-token
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.XML, FileType.YAML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        return when (context.language) {
            FileType.XML -> inspectXml(context)
            FileType.YAML -> inspectYaml(context)
            else -> emptyList()
        }
    }

    private fun inspectXml(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        if (!content.contains("previewToken", ignoreCase = true)) return emptyList()

        val issues = mutableListOf<InspectionIssue>()
        val lines = content.lines()
        val tokenPropertyPattern = Regex("""sv:name="[^"]*[Pp]review[Tt]oken[^"]*"""")

        lines.forEachIndexed { idx, line ->
            if (tokenPropertyPattern.containsMatchIn(line)) {
                val window = lines.drop(idx).take(5).joinToString("\n")
                val valueMatch = Regex("""<sv:value>(true|yes|1|enabled)</sv:value>""", RegexOption.IGNORE_CASE).find(window)
                if (valueMatch != null) {
                    val propMatch = Regex("""sv:name="([^"]+)"""").find(line)
                    val propName = propMatch?.groupValues?.get(1) ?: "previewToken property"
                    issues.add(createIssue(context, idx + 1, propName))
                }
            }
        }
        return issues
    }

    private fun inspectYaml(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()
        val tokenPattern = Regex("""[Pp]review[Tt]oken[^:]*:\s*(true|yes|enabled)""")
        context.fileContent.lines().forEachIndexed { idx, line ->
            if (tokenPattern.containsMatchIn(line.trim())) {
                issues.add(createIssue(context, idx + 1, line.trim().substringBefore(":").trim()))
            }
        }
        return issues
    }

    private fun createIssue(context: InspectionContext, lineNum: Int, propertyName: String) = InspectionIssue(
        inspection = this,
        file = context.file,
        severity = severity,
        message = "External preview token enabled ('$propertyName') — exposes unpublished content indefinitely",
        description = description,
        range = TextRange.wholeLine(lineNum),
        metadata = mapOf("propertyName" to propertyName)
    )
}
