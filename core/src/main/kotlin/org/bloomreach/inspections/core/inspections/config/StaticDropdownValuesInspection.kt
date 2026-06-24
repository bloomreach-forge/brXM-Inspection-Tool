package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*

/**
 * Detects static dropdown values hardcoded in content type XML instead of using
 * dynamic resource bundle sources. Hardcoded values prevent business users from
 * managing dropdown options without CMS development knowledge.
 */
class StaticDropdownValuesInspection : Inspection() {
    override val id = "config.static-dropdown-values"
    override val name = "Static Dropdown Values in Content Type"
    override val description = """
        Detects hardcoded static dropdown values in content type XML configurations.

        When selection fields use static inline values (e.g. "Red|red,Blue|blue"),
        business users cannot manage those options without a CMS developer. Using
        dynamic sources backed by resource bundles gives content editors full control.

        **Problem:**
        ```xml
        <!-- Static — developer must update code to change options -->
        <sv:property sv:name="source">
            <sv:value>Monday|monday,Tuesday|tuesday,Wednesday|wednesday</sv:value>
        </sv:property>
        ```

        **Solution:**
        ```xml
        <!-- Dynamic — sourced from resource bundle, editable by business users -->
        <sv:property sv:name="sourceId">
            <sv:value>weekdays</sv:value>
        </sv:property>
        ```

        Reference: https://documentation.bloomreach.com/content/docs/selection-field-configuration
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.XML)

    // Matches sv:name="source" property values containing label|value pairs
    private val sourcePropertyPattern = Regex("""sv:name="source"""")
    private val staticValuePattern = Regex("""sv:value[^>]*>([^<]*\|[^<]*)</sv:value""")

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        // Only check files that reference selection-type plugins
        if (!content.contains("source", ignoreCase = false) || !content.contains("|")) {
            return emptyList()
        }

        val issues = mutableListOf<InspectionIssue>()
        val lines = content.lines()

        lines.forEachIndexed { idx, line ->
            if (sourcePropertyPattern.containsMatchIn(line)) {
                // Look at next few lines for the sv:value with static label|value pairs
                val window = lines.drop(idx).take(5).joinToString("\n")
                val match = staticValuePattern.find(window)
                if (match != null) {
                    val value = match.groupValues[1].trim()
                    // Must contain | and not be a JCR path (paths contain / not |)
                    if (value.contains("|") && !value.startsWith("/") && value.length > 3) {
                        issues.add(InspectionIssue(
                            inspection = this,
                            file = context.file,
                            severity = severity,
                            message = "Static dropdown values detected: '${value.take(60)}${if (value.length > 60) "..." else ""}' — use a dynamic sourceId instead",
                            description = description,
                            range = TextRange.wholeLine(idx + 1),
                            metadata = mapOf("staticValues" to value)
                        ))
                    }
                }
            }
        }
        return issues
    }
}
