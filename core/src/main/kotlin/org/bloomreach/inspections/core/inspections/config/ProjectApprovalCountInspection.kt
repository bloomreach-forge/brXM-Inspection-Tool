package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*

/**
 * Detects project approval count configured to less than 2 in production.
 *
 * brXM projects require a minimum of 2 approvals by default to prevent a single
 * user from merging untested changes to production. Reducing this to 1 is sometimes
 * done during development but must not exist in deployed production environments.
 */
class ProjectApprovalCountInspection : Inspection() {
    override val id = "config.project-approval-count"
    override val name = "Project Approval Count Below Minimum"
    override val description = """
        Detects project approval workflows configured with fewer than 2 required approvals.

        brXM projects default to requiring 2 approvals before merging to production.
        This two-person rule prevents a single developer from publishing untested content
        or configuration to the live environment. Reducing this to 1 is a security and
        governance risk in production.

        **Problem:**
        A project approval count of 1 means any single user can merge changes to
        production without a second review — inappropriate for production environments.

        **Solution:**
        Restore the default approval count to 2 for production environments.
        Development environments that genuinely need faster iteration should use
        a dedicated test environment, not reduced approvals on production.

        Reference: https://documentation.bloomreach.com/content/docs/projects
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.XML)

    // Pattern: sv:property with "approval" in name, followed by sv:value of 1
    private val approvalPropertyPattern = Regex("""sv:name="[^"]*[Aa]pproval[^"]*"""")

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        if (!content.contains("pproval", ignoreCase = true)) return emptyList()

        val issues = mutableListOf<InspectionIssue>()
        val lines = content.lines()

        lines.forEachIndexed { idx, line ->
            if (approvalPropertyPattern.containsMatchIn(line)) {
                val window = lines.drop(idx).take(5).joinToString("\n")
                val valueMatch = Regex("""<sv:value>([^<]+)</sv:value>""").find(window)
                if (valueMatch != null) {
                    val value = valueMatch.groupValues[1].trim()
                    if (value == "1" || value == "0") {
                        val propNameMatch = Regex("""sv:name="([^"]+)"""").find(line)
                        val propName = propNameMatch?.groupValues?.get(1) ?: "approval property"
                        issues.add(InspectionIssue(
                            inspection = this,
                            file = context.file,
                            severity = severity,
                            message = "Project approval count set to $value for '$propName' — minimum 2 required for production",
                            description = description,
                            range = TextRange.wholeLine(idx + 1),
                            metadata = mapOf("propertyName" to propName, "value" to value)
                        ))
                    }
                }
            }
        }
        return issues
    }
}
