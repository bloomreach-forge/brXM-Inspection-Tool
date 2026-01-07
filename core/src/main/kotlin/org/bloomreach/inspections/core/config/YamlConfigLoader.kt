package org.bloomreach.inspections.core.config

import org.bloomreach.inspections.core.engine.Severity
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.InputStream
import java.nio.file.Path

/**
 * Loads inspection configuration from YAML files.
 *
 * Supports both file paths and input streams for flexibility.
 * See [generateDefaultYaml] for an example of the YAML format.
 */
object YamlConfigLoader {

    /**
     * Load configuration from a file
     */
    fun load(file: File): InspectionConfig {
        require(file.exists()) { "Configuration file does not exist: ${file.absolutePath}" }
        return file.inputStream().use { load(it) }
    }

    /**
     * Load configuration from a path
     */
    fun load(path: Path): InspectionConfig {
        return load(path.toFile())
    }

    /**
     * Load configuration from an input stream
     */
    fun load(inputStream: InputStream): InspectionConfig {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(inputStream)
        return parseConfig(data)
    }

    /**
     * Load configuration from a YAML string
     */
    fun loadFromString(yamlContent: String): InspectionConfig {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(yamlContent)
        return parseConfig(data)
    }

    /**
     * Try to load configuration from project root, return default if not found
     */
    fun loadOrDefault(projectRoot: Path): InspectionConfig {
        val configFile = projectRoot.resolve(".brxm-inspections.yaml").toFile()
        if (!configFile.exists()) {
            val altConfigFile = projectRoot.resolve("brxm-inspections.yaml").toFile()
            if (!altConfigFile.exists()) {
                return InspectionConfig.default()
            }
            return load(altConfigFile)
        }
        return load(configFile)
    }

    /**
     * Parse YAML data into InspectionConfig
     */
    private fun parseConfig(data: Map<String, Any>): InspectionConfig {
        val enabled = data["enabled"] as? Boolean ?: true
        val parallel = data["parallel"] as? Boolean ?: true
        val cacheEnabled = data["cacheEnabled"] as? Boolean ?: true

        val minSeverity = data["minSeverity"]?.let {
            parseSeverity(it.toString())
        } ?: Severity.INFO

        val excludePaths = (data["excludePaths"] as? List<*>)
            ?.mapNotNull { it?.toString() }
            ?: InspectionConfig().excludePaths

        val includePaths = (data["includePaths"] as? List<*>)
            ?.mapNotNull { it?.toString() }
            ?: InspectionConfig().includePaths

        val maxThreads = (data["maxThreads"] as? Int)
            ?: Runtime.getRuntime().availableProcessors()

        val inspections = parseInspections(data["inspections"] as? Map<String, Any>)

        return InspectionConfig(
            enabled = enabled,
            inspections = inspections,
            excludePaths = excludePaths,
            includePaths = includePaths,
            minSeverity = minSeverity,
            parallel = parallel,
            maxThreads = maxThreads,
            cacheEnabled = cacheEnabled
        )
    }

    /**
     * Parse per-inspection settings
     */
    private fun parseInspections(data: Map<String, Any>?): Map<String, InspectionSettings> {
        if (data == null) return emptyMap()

        return data.mapValues { (_, value) ->
            val settings = value as? Map<*, *> ?: return@mapValues InspectionSettings()

            val enabled = settings["enabled"] as? Boolean ?: true
            val severity = settings["severity"]?.let { parseSeverity(it.toString()) }
            val options = parseOptions(settings["options"] as? Map<*, *>)

            InspectionSettings(
                enabled = enabled,
                severity = severity,
                options = options
            )
        }
    }

    /**
     * Parse inspection-specific options
     */
    private fun parseOptions(data: Map<*, *>?): Map<String, Any> {
        if (data == null) return emptyMap()
        return data.mapKeys { it.key.toString() }.mapValues { it.value ?: "" }
    }

    /**
     * Parse severity from string
     */
    private fun parseSeverity(value: String): Severity {
        return when (value.uppercase()) {
            "ERROR" -> Severity.ERROR
            "WARNING", "WARN" -> Severity.WARNING
            "INFO" -> Severity.INFO
            "HINT" -> Severity.HINT
            else -> throw IllegalArgumentException("Invalid severity: $value. Must be one of: ERROR, WARNING, INFO, HINT")
        }
    }

    /**
     * Generate a default YAML configuration file content
     */
    fun generateDefaultYaml(): String {
        return """
            # Bloomreach CMS Inspections Configuration
            # This file controls how inspections are executed in your project.

            # Enable/disable inspections globally
            enabled: true

            # Enable parallel execution (improves performance)
            parallel: true

            # Enable parse cache (improves performance)
            cacheEnabled: true

            # Minimum severity to report (ERROR, WARNING, INFO, HINT)
            minSeverity: INFO

            # File patterns to exclude from analysis
            excludePaths:
              - "**target/**"
              - "**build/**"
              - "**node_modules/**"
              - "**.git/**"

            # File patterns to include in analysis
            includePaths:
              - "**/*.java"
              - "**/*.xml"
              - "**/*.yaml"
              - "**/*.yml"
              - "**/*.json"

            # Per-inspection configuration
            # Each inspection can be individually configured
            inspections:
              # Repository Tier Issues
              repository.session-leak:
                enabled: true
                severity: ERROR

              repository.workflow-action-validation:
                enabled: true
                severity: WARNING

              # Configuration Issues
              config.bootstrap-uuid-conflict:
                enabled: true
                severity: ERROR

              config.sitemap-shadowing:
                enabled: true
                severity: WARNING

              config.component-parameter-null:
                enabled: true
                severity: WARNING

              config.cache-configuration:
                enabled: true
                severity: WARNING

              # Performance Issues
              performance.unbounded-query:
                enabled: true
                severity: WARNING
                options:
                  maxResultsWithoutLimit: 100

              performance.missing-index:
                enabled: true
                severity: INFO

              # Security Issues
              security.hardcoded-credentials:
                enabled: true
                severity: ERROR

              security.hardcoded-paths:
                enabled: true
                severity: WARNING

              security.rest-authentication:
                enabled: true
                severity: ERROR

              # Deployment Issues
              deployment.docker-config:
                enabled: true
                severity: WARNING
        """.trimIndent()
    }

    /**
     * Write default configuration to a file
     */
    fun writeDefaultConfig(file: File) {
        file.writeText(generateDefaultYaml())
    }

    /**
     * Write default configuration to project root
     */
    fun writeDefaultConfig(projectRoot: Path) {
        val configFile = projectRoot.resolve(".brxm-inspections.yaml").toFile()
        writeDefaultConfig(configFile)
    }
}
