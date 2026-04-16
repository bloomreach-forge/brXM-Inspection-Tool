package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*

/**
 * Detects development/test/staging channel names in HST configuration bootstrap files.
 *
 * Development environments should be handled through the Projects feature in brXM,
 * not through separate channels named after environments. Separate dev/test/uat channels
 * create HST configuration debt and clutter the Channel Manager UI.
 */
class DevelopmentChannelInspection : Inspection() {
    override val id = "config.development-channel-presence"
    override val name = "Development Channel Present in Configuration"
    override val description = """
        Detects HST channel or site nodes named after development environments
        (dev, test, uat, staging, local, acceptance, qa).

        Separate environments should be managed through brXM's Projects feature,
        not through dedicated channels. Environment-named channels:
        - Inflate HST configuration (every added channel multiplies config nodes)
        - Clutter the Channel Manager and Site Development UIs
        - Create configuration debt that is expensive to clean up

        **Solution:**
        Delete environment-specific channels. Use a single channel per site and manage
        environment differences via Projects, environment variables, or channel properties.

        Reference: https://documentation.bloomreach.com/content/docs/projects
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.XML)

    private val devSegments = listOf("dev", "test", "uat", "staging", "local", "acceptance", "qa", "acc")
    private val nodeNamePattern = Regex("""sv:name="([^"]+)"""")

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        // Only check HST configuration files
        if (!content.contains("hst:channel") && !content.contains("hst:site") &&
            !content.contains("hst:mount") && !content.contains("hst:virtualhost")) {
            return emptyList()
        }

        val issues = mutableListOf<InspectionIssue>()
        content.lines().forEachIndexed { idx, line ->
            val match = nodeNamePattern.find(line) ?: return@forEachIndexed
            val nodeName = match.groupValues[1]
            val segment = matchesDevPattern(nodeName)
            if (segment != null) {
                issues.add(InspectionIssue(
                    inspection = this,
                    file = context.file,
                    severity = severity,
                    message = "Development channel name detected: '$nodeName' (matches environment keyword '$segment')",
                    description = description,
                    range = TextRange.wholeLine(idx + 1),
                    metadata = mapOf("channelName" to nodeName, "matchedKeyword" to segment)
                ))
            }
        }
        return issues
    }

    private fun matchesDevPattern(name: String): String? {
        val lower = name.lowercase()
        for (segment in devSegments) {
            if (lower == segment ||
                lower.startsWith("${segment}-") || lower.startsWith("${segment}_") ||
                lower.endsWith("-${segment}") || lower.endsWith("_${segment}") ||
                lower.contains("-${segment}-") || lower.contains("_${segment}_")) {
                return segment
            }
        }
        return null
    }
}
