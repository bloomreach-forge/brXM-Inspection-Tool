package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*

/**
 * Detects resource bundle (translation) definitions in XML bootstrap files that are
 * not stored under the standard Administration path. Bundles in non-standard locations
 * are harder to maintain and may not appear in the CMS Administration perspective.
 */
class ResourceBundleLocationInspection : Inspection() {
    override val id = "config.resource-bundle-location"
    override val name = "Resource Bundle Not in Administration Folder"
    override val description = """
        Detects resource bundle (hippo:bundle) definitions located outside the standard
        /hippo:configuration/hippo:translations/ hierarchy.

        brXM stores translatable strings in resource bundles under the Administration
        folder in the CMS. Bundles outside this path:
        - May not appear in the Administration > Translations view
        - Are harder to find and maintain by CMS administrators
        - Can cause translation tooling to miss them

        **Solution:**
        Move all resource bundle bootstrap XML files to follow the standard path:
        /hippo:configuration/hippo:translations/hippo:translations/[namespace]/

        Reference: https://documentation.bloomreach.com/content/docs/translations
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.HINT
    override val applicableFileTypes = setOf(FileType.XML)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val content = context.fileContent
        // Only check files that define resource bundle nodes
        if (!content.contains("hippo:bundle") && !content.contains("hipposys:resourcebundles") &&
            !content.contains("hippo:translation")) {
            return emptyList()
        }

        // Check if this file's path is in a standard location
        val filePath = context.file.path.toString().lowercase()
        val isInStandardLocation = filePath.contains("administration") ||
            filePath.contains("translations") ||
            filePath.contains("resourcebundle")

        if (isInStandardLocation) return emptyList()

        // Check if the XML content itself routes bundles to the correct JCR path
        val hasCorrectJcrPath = content.contains("hippo:translations") &&
            content.contains("hippo:configuration")

        if (hasCorrectJcrPath) return emptyList()

        return listOf(InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Resource bundle definitions found outside the standard Administration/translations path",
            description = description,
            range = TextRange.wholeLine(1),
            metadata = mapOf("filePath" to context.file.path.toString())
        ))
    }
}
