package org.bloomreach.inspections.core.config

import org.bloomreach.inspections.core.engine.Severity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YamlConfigLoaderTest {

    @Test
    fun `should load minimal config`() {
        val yaml = """
            enabled: true
        """.trimIndent()

        val config = YamlConfigLoader.loadFromString(yaml)

        assertTrue(config.enabled)
        assertTrue(config.parallel)  // Default value
        assertTrue(config.cacheEnabled)  // Default value
    }

    @Test
    fun `should load complete config`() {
        val yaml = """
            enabled: true
            parallel: false
            cacheEnabled: true
            minSeverity: WARNING
            maxThreads: 4

            excludePaths:
              - "**target/**"
              - "**build/**"

            includePaths:
              - "**/*.java"
              - "**/*.xml"

            inspections:
              repository.session-leak:
                enabled: true
                severity: ERROR
              performance.unbounded-query:
                enabled: false
                severity: WARNING
                options:
                  maxResultsWithoutLimit: 100
        """.trimIndent()

        val config = YamlConfigLoader.loadFromString(yaml)

        assertTrue(config.enabled)
        assertFalse(config.parallel)
        assertTrue(config.cacheEnabled)
        assertEquals(Severity.WARNING, config.minSeverity)
        assertEquals(4, config.maxThreads)

        assertEquals(2, config.excludePaths.size)
        assertTrue(config.excludePaths.contains("**target/**"))

        assertEquals(2, config.includePaths.size)
        assertTrue(config.includePaths.contains("**/*.java"))

        // Check per-inspection settings
        val sessionLeakSettings = config.inspections["repository.session-leak"]
        assertNotNull(sessionLeakSettings)
        assertTrue(sessionLeakSettings.enabled)
        assertEquals(Severity.ERROR, sessionLeakSettings.severity)

        val unboundedQuerySettings = config.inspections["performance.unbounded-query"]
        assertNotNull(unboundedQuerySettings)
        assertFalse(unboundedQuerySettings.enabled)
        assertEquals(Severity.WARNING, unboundedQuerySettings.severity)
        assertEquals(100, unboundedQuerySettings.options["maxResultsWithoutLimit"])
    }

    @Test
    fun `should parse all severity levels`() {
        val severities = listOf("ERROR", "WARNING", "WARN", "INFO", "HINT")

        severities.forEach { severity ->
            val yaml = """
                minSeverity: $severity
            """.trimIndent()

            val config = YamlConfigLoader.loadFromString(yaml)
            assertNotNull(config.minSeverity)
        }
    }

    @Test
    fun `should throw on invalid severity`() {
        val yaml = """
            minSeverity: INVALID
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            YamlConfigLoader.loadFromString(yaml)
        }
    }

    @Test
    fun `should load from file`() {
        val tempFile = Files.createTempFile("test-config", ".yaml").toFile()
        tempFile.deleteOnExit()

        tempFile.writeText("""
            enabled: true
            parallel: true
        """.trimIndent())

        val config = YamlConfigLoader.load(tempFile)

        assertTrue(config.enabled)
        assertTrue(config.parallel)
    }

    @Test
    fun `should throw when file does not exist`() {
        val nonExistentFile = File("/nonexistent/config.yaml")

        assertThrows<IllegalArgumentException> {
            YamlConfigLoader.load(nonExistentFile)
        }
    }

    @Test
    fun `should load or default when config file exists`() {
        val tempDir = Files.createTempDirectory("test-project").toFile()
        tempDir.deleteOnExit()

        val configFile = File(tempDir, ".brxm-inspections.yaml")
        configFile.writeText("""
            enabled: false
        """.trimIndent())

        val config = YamlConfigLoader.loadOrDefault(tempDir.toPath())

        assertFalse(config.enabled)
    }

    @Test
    fun `should return default when config file does not exist`() {
        val tempDir = Files.createTempDirectory("test-project").toFile()
        tempDir.deleteOnExit()

        val config = YamlConfigLoader.loadOrDefault(tempDir.toPath())

        // Should have default values
        assertTrue(config.enabled)
        assertTrue(config.parallel)
    }

    @Test
    fun `should generate default yaml`() {
        val defaultYaml = YamlConfigLoader.generateDefaultYaml()

        // Verify it's valid YAML by parsing it
        val config = YamlConfigLoader.loadFromString(defaultYaml)

        assertTrue(config.enabled)
        assertTrue(config.parallel)
        assertTrue(config.cacheEnabled)

        // Should have some default inspections configured
        assertTrue(config.inspections.isNotEmpty())
    }

    @Test
    fun `should write default config to file`() {
        val tempFile = Files.createTempFile("default-config", ".yaml").toFile()
        tempFile.deleteOnExit()

        YamlConfigLoader.writeDefaultConfig(tempFile)

        assertTrue(tempFile.exists())
        assertTrue(tempFile.length() > 0)

        // Verify it's valid by loading it
        val config = YamlConfigLoader.load(tempFile)
        assertTrue(config.enabled)
    }

    @Test
    fun `should handle inspection without options`() {
        val yaml = """
            inspections:
              repository.session-leak:
                enabled: true
                severity: ERROR
        """.trimIndent()

        val config = YamlConfigLoader.loadFromString(yaml)

        val settings = config.inspections["repository.session-leak"]
        assertNotNull(settings)
        assertTrue(settings.options.isEmpty())
    }

    @Test
    fun `should handle empty inspections map`() {
        val yaml = """
            enabled: true
            inspections: {}
        """.trimIndent()

        val config = YamlConfigLoader.loadFromString(yaml)

        assertTrue(config.inspections.isEmpty())
    }

    @Test
    fun `should use config isEnabled to check specific inspections`() {
        val yaml = """
            enabled: true
            inspections:
              repository.session-leak:
                enabled: false
        """.trimIndent()

        val config = YamlConfigLoader.loadFromString(yaml)

        // Global enabled is true
        assertTrue(config.enabled)

        // But session-leak is disabled
        assertFalse(config.isEnabled("repository.session-leak"))

        // Unknown inspection should default to enabled (if global enabled)
        assertTrue(config.isEnabled("unknown.inspection"))
    }

    @Test
    fun `should use config isEnabled with global disabled`() {
        val yaml = """
            enabled: false
            inspections:
              repository.session-leak:
                enabled: true
        """.trimIndent()

        val config = YamlConfigLoader.loadFromString(yaml)

        // Global enabled is false - all inspections should be disabled
        assertFalse(config.enabled)
        assertFalse(config.isEnabled("repository.session-leak"))
        assertFalse(config.isEnabled("any.inspection"))
    }
}
