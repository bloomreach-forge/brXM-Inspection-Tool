package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*

/**
 * Detects excessive numbers of HST channels in a single bootstrap file, indicating
 * HST configuration bloat. Each channel multiplies HST node count; many channels
 * with duplicate configuration create significant maintenance and performance overhead.
 */
class HstConfigurationBloatInspection : Inspection() {
    override val id = "config.hst-configuration-bloat"
    override val name = "HST Configuration Bloat — Too Many Channels"
    override val description = """
        Detects an excessive number of HST channel definitions in a single file.

        Each channel added to a project duplicates HST configuration nodes (sitemaps,
        component configurations, virtual hosts, mounts). When projects are created and
        channels added, this multiplication compounds. Large numbers of channels with
        near-duplicate configuration are a sign of architectural debt.

        **Problem:**
        - Too many channels inflates HST node count in the repository
        - Duplicate configuration across channels is hard to maintain
        - Channel Manager and Site Development UIs become cluttered

        **Solution:**
        - Delete unused or environment-specific channels
        - Consolidate channels with similar configurations
        - Use a single channel per site variant; use channel properties for minor differences
        - Delete completed projects immediately after merging

        Reference: https://documentation.bloomreach.com/content/docs/hst-configuration-model
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.XML)

    internal val channelCountThreshold = 5

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        if (!content.contains("hst:channel") && !content.contains("hst:site")) {
            return emptyList()
        }

        val channelCount = countChannelNodes(content)
        if (channelCount < channelCountThreshold) return emptyList()

        return listOf(InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "HST configuration bloat: $channelCount channel/site definitions found in single file (threshold: $channelCountThreshold)",
            description = description,
            range = TextRange.wholeLine(1),
            metadata = mapOf("channelCount" to channelCount.toString(), "threshold" to channelCountThreshold.toString())
        ))
    }

    private fun countChannelNodes(content: String): Int {
        // Count primary type declarations for hst:channel and hst:site
        val channelPattern = Regex("""<sv:value>hst:channel</sv:value>""")
        val sitePattern = Regex("""<sv:value>hst:site</sv:value>""")
        return channelPattern.findAll(content).count() + sitePattern.findAll(content).count()
    }
}
