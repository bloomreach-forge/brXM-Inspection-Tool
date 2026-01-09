package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.yaml.snakeyaml.Yaml
import kotlin.math.max

/**
 * Detects missing or invalid hst.configuration.rootPath property in HST configuration files.
 *
 * Each HST webapp must have its hst.configuration.rootPath property set to correctly
 * point to the HST configuration root node. Without this property or with an incorrect
 * path, the HST cannot load its configuration and channels won't display.
 *
 * Supported file formats:
 * - PROPERTIES: hst.configuration.rootPath=/hst:config/hst:sites/mysite
 * - YAML: hst.configuration.rootPath or nested hst.configuration.rootPath
 * - XML: Not yet supported (future enhancement)
 *
 * Valid path patterns:
 * - /hst:config/hst:sites/... (standard)
 * - /content/hst... (alternative)
 * - Must start with /
 * - Should contain hst: namespace or hst keyword
 */
class HstConfigurationRootPathInspection : Inspection() {
    override val id = "config.hst-configuration-root-path"
    override val name = "HST Configuration Root Path Misconfiguration"
    override val description = """
        Detects missing or incorrect hst.configuration.rootPath property configuration.

        The HST (Hippo Site Toolkit) requires each webapp to have a property
        'hst.configuration.rootPath' that points to the location of HST configuration
        in the repository. Without this property or with an invalid path, the HST
        cannot load its configuration and channels won't display in the Experience Manager.

        **Problem:**
        - Property is missing entirely
        - Path points to non-existent node
        - Path doesn't follow standard patterns
        - Path is empty or only whitespace

        **Solution:**
        Add or correct the hst.configuration.rootPath property to point to valid
        HST configuration node, typically: /hst:config/hst:sites/mysite

        **Supported Files:**
        - hst-config.properties
        - hst-config.yaml
        - hst-config.yml
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.PROPERTIES, FileType.YAML)

    private val validPathPatterns = listOf(
        Regex("^/hst:config/hst:sites/.*"),  // Standard pattern
        Regex("^/content/hst.*"),              // Alternative pattern
        Regex("^/hst:config/.*"),              // Generic hst:config
        Regex("^/.*hst.*")                      // Must contain hst somewhere
    )

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        return when (context.language) {
            FileType.PROPERTIES -> inspectPropertiesFile(context)
            FileType.YAML -> inspectYamlFile(context)
            else -> emptyList()
        }
    }

    private fun inspectPropertiesFile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()
        val lines = context.fileContent.lines()

        var foundProperty = false

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // Skip comments and empty lines
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                continue
            }

            if (trimmed.startsWith("hst.configuration.rootPath")) {
                foundProperty = true

                // Parse the property
                val parts = line.split("=", limit = 2)
                if (parts.size < 2) {
                    issues.add(createMissingValueIssue(context, index + 1, line))
                } else {
                    val value = parts[1].trim()
                    if (value.isEmpty() || value.isBlank()) {
                        issues.add(createEmptyValueIssue(context, index + 1, line))
                    } else if (!isValidPath(value)) {
                        issues.add(createInvalidPathIssue(context, index + 1, line, value))
                    }
                }
            }
        }

        if (!foundProperty) {
            issues.add(createMissingPropertyIssue(context))
        }

        return issues
    }

    private fun inspectYamlFile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val yaml = Yaml()
            val data = yaml.load<Any>(context.fileContent) as? Map<*, *> ?: return issues

            // Look for hst.configuration.rootPath in different formats
            val value = findHstConfigurationRootPath(data)

            if (value == null) {
                issues.add(createMissingPropertyIssue(context))
            } else if (value is String) {
                if (value.isEmpty() || value.isBlank()) {
                    issues.add(createEmptyValueIssue(context, 1, "hst.configuration.rootPath"))
                } else if (!isValidPath(value)) {
                    issues.add(createInvalidPathIssue(context, 1, "hst.configuration.rootPath", value))
                }
            }
        } catch (e: Exception) {
            // YAML parse error - don't report as inspection issue
            return emptyList()
        }

        return issues
    }

    private fun findHstConfigurationRootPath(data: Map<*, *>): Any? {
        // Try flat format: hst.configuration.rootPath
        val flatKey = data.keys.find {
            it.toString().equals("hst.configuration.rootPath", ignoreCase = true)
        }
        if (flatKey != null) {
            return data[flatKey]
        }

        // Try nested format: hst -> configuration -> rootPath
        val hstMap = data["hst"] as? Map<*, *> ?: return null
        val configMap = hstMap["configuration"] as? Map<*, *> ?: return null
        return configMap["rootPath"]
    }

    private fun isValidPath(path: String): Boolean {
        if (!path.startsWith("/")) {
            return false
        }

        return validPathPatterns.any { it.matches(path) }
    }

    private fun createMissingPropertyIssue(context: InspectionContext): InspectionIssue {
        val range = TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.ERROR,
            message = "missing hst.configuration.rootPath property",
            description = """
                **Problem:** HST configuration root path property is missing

                The HST (Hippo Site Toolkit) requires a property named
                'hst.configuration.rootPath' that specifies where the HST configuration
                is located in the repository. Without this property, the HST cannot load
                its configuration and channels won't display.

                **Impact:**
                - Channels don't appear in Channel Manager
                - Site pages don't render
                - HST configuration is not loaded
                - WebApp will fail to start properly

                **Solution:** Add the hst.configuration.rootPath property

                **For hst-config.properties:**
                ```properties
                hst.configuration.rootPath=/hst:config/hst:sites/mysite
                ```

                **For hst-config.yaml:**
                ```yaml
                hst:
                  configuration:
                    rootPath: /hst:config/hst:sites/mysite
                ```

                **Valid Path Formats:**
                - `/hst:config/hst:sites/mysite` (standard - recommended)
                - `/content/hst:config/hst:sites/mysite` (alternative)
                - `/hst:config/hst:sites/mysite/en` (sub-configuration)

                **Steps to Find Your Path:**
                1. Go to CMS Console
                2. Navigate to Repository > System
                3. Find `/hst:config/hst:sites/`
                4. Locate your site name
                5. Copy the full path starting from `/hst:config`

                **Important Notes:**
                - Each webapp should have its own hst.configuration.rootPath
                - Path must start with `/`
                - Must point to existing node in repository
                - Different for each HST webapp if running multiple sites

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [HST Configuration Guide](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf("propertyName" to "hst.configuration.rootPath", "status" to "missing")
        )
    }

    private fun createMissingValueIssue(
        context: InspectionContext,
        lineNumber: Int,
        line: String
    ): InspectionIssue {
        val range = TextRange(lineNumber, 0, lineNumber, max(line.length, 10))

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.ERROR,
            message = "hst.configuration.rootPath property has no value",
            description = """
                **Problem:** hst.configuration.rootPath property is defined but has no value

                The property exists but is missing the actual path value. HST needs a
                valid path to the configuration node to function.

                **Current Line:**
                ```
                $line
                ```

                **Solution:** Add a path value to the property

                **Correct Format:**
                ```properties
                hst.configuration.rootPath=/hst:config/hst:sites/mysite
                ```

                **Valid Path Examples:**
                - `/hst:config/hst:sites/mysite`
                - `/content/hst:config/hst:sites/mysite`
                - `/hst:config/hst:sites/mysite/en`

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
            """.trimIndent(),
            range = range,
            metadata = mapOf("propertyName" to "hst.configuration.rootPath", "status" to "novalue")
        )
    }

    private fun createEmptyValueIssue(
        context: InspectionContext,
        lineNumber: Int,
        line: String
    ): InspectionIssue {
        val range = TextRange(lineNumber, 0, lineNumber, max(line.toString().length, 10))

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.ERROR,
            message = "hst.configuration.rootPath property is empty",
            description = """
                **Problem:** hst.configuration.rootPath property is empty or whitespace-only

                The property is defined but contains only whitespace or is completely empty.
                HST requires a valid non-empty path to the configuration node.

                **Solution:** Provide a valid path value

                **Correct Formats:**

                For PROPERTIES files:
                ```properties
                hst.configuration.rootPath=/hst:config/hst:sites/mysite
                ```

                For YAML files:
                ```yaml
                hst:
                  configuration:
                    rootPath: /hst:config/hst:sites/mysite
                ```

                **Valid Paths:**
                - `/hst:config/hst:sites/mysite` (standard)
                - `/content/hst-config/mysite` (alternative)
                - `/hst:config/hst:sites/mysite/en` (locale-specific)

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
            """.trimIndent(),
            range = range,
            metadata = mapOf("propertyName" to "hst.configuration.rootPath", "status" to "empty")
        )
    }

    private fun createInvalidPathIssue(
        context: InspectionContext,
        lineNumber: Int,
        line: String,
        path: String
    ): InspectionIssue {
        val range = TextRange(lineNumber, 0, lineNumber, max(line.toString().length, 10))

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.ERROR,
            message = "hst.configuration.rootPath has invalid path format: $path",
            description = """
                **Problem:** hst.configuration.rootPath value doesn't follow standard patterns

                The path value doesn't match expected HST configuration path patterns.
                This typically causes HST to fail loading configuration.

                **Current Path:**
                ```
                $path
                ```

                **Common Issues:**
                - Path doesn't start with `/` (must be absolute path)
                - Path missing `hst:` namespace or `hst` keyword
                - Path points to wrong hierarchy level
                - Typo in path name

                **Valid Path Patterns:**

                Standard (most common):
                ```
                /hst:config/hst:sites/mysite
                /hst:config/hst:sites/mysite/en
                ```

                Alternative:
                ```
                /content/hst:config/hst:sites/mysite
                /content/hst-config/mysite
                ```

                **How to Find Correct Path:**
                1. Open CMS Console
                2. Navigate to Repository > System
                3. Look in `/hst:config/hst:sites/`
                4. Find your site folder name
                5. Copy the full path

                **Examples by Configuration Type:**

                Single site deployment:
                ```properties
                hst.configuration.rootPath=/hst:config/hst:sites/mysite
                ```

                Multi-site with locales:
                ```properties
                hst.configuration.rootPath=/hst:config/hst:sites/mysite/en
                ```

                Custom structure:
                ```properties
                hst.configuration.rootPath=/content/hst:config/mysite
                ```

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [HST Configuration Documentation](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf("propertyName" to "hst.configuration.rootPath", "status" to "invalid", "providedPath" to path)
        )
    }
}
