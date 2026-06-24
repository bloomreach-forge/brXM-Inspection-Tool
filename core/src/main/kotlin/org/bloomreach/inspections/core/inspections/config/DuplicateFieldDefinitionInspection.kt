package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*

/**
 * Detects repeated field definitions in content type XML that indicate candidate
 * refactoring into reusable field groups (compound types). When the same set of
 * fields appears multiple times in a content model, a field group should be used.
 */
class DuplicateFieldDefinitionInspection : Inspection() {
    override val id = "config.duplicate-field-definition"
    override val name = "Duplicate Field Definitions — Consider Field Groups"
    override val description = """
        Detects field names that appear to be numbered duplicates within a content type,
        suggesting they should be consolidated into a reusable field group (compound type).

        When fields like image1, image2, image3 or link1, link2, link3 appear in a
        document type, they indicate copy-pasted field definitions that should instead
        be modelled as a multi-value compound field. Field groups:
        - Reduce content model duplication
        - Make content editing simpler (add/remove items dynamically)
        - Allow content reuse across document types

        **Problem:**
        ```xml
        <!-- Three separate image fields — hard to extend, poor editing UX -->
        ns:image1, ns:image2, ns:image3
        ```

        **Solution:**
        Create a compound type (e.g., ns:imageItem) and use a multiple-values
        field group instead of separate numbered fields.

        Reference: https://documentation.bloomreach.com/content/docs/compound-fields
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.HINT
    override val applicableFileTypes = setOf(FileType.XML)

    // Matches field path values like "ns:fieldname2" — namespace:name followed by digit(s)
    private val numberedFieldPattern = Regex("""[a-z][a-z0-9]*:[a-z][a-z0-9]*[0-9]+""")

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        // Only check namespace/document type files
        if (!content.contains("hipposys:templatetype") && !content.contains("hippo:namespace") &&
            !content.contains("editor:templates")) {
            return emptyList()
        }

        val numberedFields = mutableMapOf<String, MutableList<Pair<String, Int>>>()

        content.lines().forEachIndexed { idx, line ->
            val match = numberedFieldPattern.find(line) ?: return@forEachIndexed
            val fieldPath = match.value
            // Extract base name by stripping trailing digits
            val baseName = fieldPath.trimEnd('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
            numberedFields.getOrPut(baseName) { mutableListOf() }.add(Pair(fieldPath, idx + 1))
        }

        return numberedFields
            .filter { (_, occurrences) -> occurrences.size >= 2 }
            .map { (baseName, occurrences) ->
                val fieldNames = occurrences.map { it.first }.distinct().joinToString(", ")
                InspectionIssue(
                    inspection = this,
                    file = context.file,
                    severity = severity,
                    message = "Numbered field duplicates detected for '${baseName}N' ($fieldNames) — consider a field group compound type",
                    description = description,
                    range = TextRange.wholeLine(occurrences.first().second),
                    metadata = mapOf("baseName" to baseName, "fields" to fieldNames)
                )
            }
    }
}
