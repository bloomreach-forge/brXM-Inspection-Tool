package org.bloomreach.inspections.cli.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Loads and manages CLI configuration from YAML files.
 */
class ConfigLoader {

    private val mapper = ObjectMapper(YAMLFactory())

    /**
     * Load configuration from a YAML file.
     *
     * @param configPath Path to configuration file
     * @return Parsed CliConfig object, or null if file doesn't exist
     * @throws Exception if file parsing fails
     */
    fun loadConfig(configPath: Path): CliConfig? {
        if (!Files.exists(configPath)) {
            return null
        }

        return try {
            val content = Files.readString(configPath)
            mapper.readValue(content, CliConfig::class.java)
        } catch (e: Exception) {
            throw ConfigException("Failed to parse configuration file: ${e.message}", e)
        }
    }

    /**
     * Load configuration from a file path string.
     * Supports relative and absolute paths.
     */
    fun loadConfigFromString(configPath: String?): CliConfig? {
        if (configPath == null) {
            return findDefaultConfig()
        }

        val path = Paths.get(configPath)
        return loadConfig(path)
    }

    /**
     * Find default configuration file in current directory or parent directories.
     */
    private fun findDefaultConfig(): CliConfig? {
        val defaultName = ".brxm-inspections.yaml"
        val workingDir = Paths.get(System.getProperty("user.dir"))

        // Check current directory
        var current = workingDir
        repeat(3) { // Check up to 3 levels up
            val configPath = current.resolve(defaultName)
            if (Files.exists(configPath)) {
                return loadConfig(configPath)
            }
            current = current.parent
        }

        return null
    }

    /**
     * Save configuration to a YAML file.
     */
    fun saveConfig(config: CliConfig, path: Path) {
        try {
            val yaml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config)
            Files.createDirectories(path.parent)
            Files.writeString(path, yaml)
        } catch (e: Exception) {
            throw ConfigException("Failed to save configuration: ${e.message}", e)
        }
    }

    /**
     * Validate configuration for required fields.
     */
    fun validateConfig(config: CliConfig) {
        val errors = mutableListOf<String>()

        if (config.maxThreads != null && config.maxThreads!! < 1) {
            errors.add("maxThreads must be at least 1")
        }

        if (config.minSeverity != null) {
            val validSeverities = listOf("ERROR", "WARNING", "INFO", "HINT")
            if (config.minSeverity !in validSeverities) {
                errors.add("minSeverity must be one of: ${validSeverities.joinToString(", ")}")
            }
        }

        if (errors.isNotEmpty()) {
            throw ConfigException("Configuration validation failed:\n" + errors.joinToString("\n"))
        }
    }
}

/**
 * Exception thrown during configuration operations.
 */
class ConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * CLI Configuration data class.
 *
 * Maps to YAML structure with fields like:
 * enabled, minSeverity, parallel, maxThreads, cacheEnabled,
 * includePaths, excludePaths, and per-inspection configurations.
 */
data class CliConfig(
    val enabled: Boolean? = null,
    val minSeverity: String? = null,
    val parallel: Boolean? = null,
    val maxThreads: Int? = null,
    val cacheEnabled: Boolean? = null,
    val includePaths: List<String>? = null,
    val excludePaths: List<String>? = null,
    val inspections: Map<String, InspectionConfig>? = null
) {
    companion object {
        /**
         * Create a default configuration.
         */
        fun default(): CliConfig {
            return CliConfig(
                enabled = true,
                minSeverity = "INFO",
                parallel = true,
                maxThreads = Runtime.getRuntime().availableProcessors(),
                cacheEnabled = true,
                includePaths = listOf(
                    "**/*.java",
                    "**/*.xml",
                    "**/*.yaml",
                    "**/*.yml",
                    "**/*.json"
                ),
                excludePaths = listOf(
                    "**/target/**",
                    "**/build/**",
                    "**/node_modules/**",
                    "**/.git/**"
                ),
                inspections = emptyMap()
            )
        }
    }
}

/**
 * Configuration for individual inspection.
 */
data class InspectionConfig(
    val enabled: Boolean? = null,
    val severity: String? = null,
    val options: Map<String, Any>? = null
)
