package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HstConfigurationRootPathInspectionTest {

    private val inspection = HstConfigurationRootPathInspection()

    @Test
    fun `should detect missing rootPath in properties file`() {
        val content = """
            server.port=8080
            logging.level=INFO
            # No hst.configuration.rootPath property
            other.setting=value
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size, "Should detect missing property")
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("missing"))
        assertTrue(issues[0].message.contains("hst.configuration.rootPath"))
    }

    @Test
    fun `should allow correct rootPath in properties file`() {
        val content = """
            hst.configuration.rootPath=/hst:config/hst:sites/mysite
            server.port=8080
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(0, issues.size, "Should allow valid path")
    }

    @Test
    fun `should detect invalid path without hst namespace`() {
        val content = """
            hst.configuration.rootPath=/mysite
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("invalid path"))
    }

    @Test
    fun `should detect path that doesn't start with slash`() {
        val content = """
            hst.configuration.rootPath=hst:config/hst:sites/mysite
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should allow alternative valid paths`() {
        val validPaths = listOf(
            "hst.configuration.rootPath=/hst:config/hst:sites/mysite",
            "hst.configuration.rootPath=/hst:config/hst:sites/mysite/en",
            "hst.configuration.rootPath=/content/hst:config/hst:sites/mysite",
            "hst.configuration.rootPath=/content/hst-config/mysite",
            "hst.configuration.rootPath=/hst:config/hst:sites/mysite/fr"
        )

        validPaths.forEach { content ->
            val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")
            assertEquals(0, issues.size, "Should allow: $content")
        }
    }

    @Test
    fun `should detect empty rootPath`() {
        val content = """
            hst.configuration.rootPath=
            server.port=8080
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("empty"))
    }

    @Test
    fun `should detect whitespace-only rootPath`() {
        val content = """
            hst.configuration.rootPath=
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should detect missing property in YAML`() {
        val content = """
            server:
              port: 8080
            logging:
              level: INFO
        """.trimIndent()

        val issues = runInspection(content, FileType.YAML, "hst-config.yaml")

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("missing"))
    }

    @Test
    fun `should allow correct rootPath in YAML nested format`() {
        val content = """
            hst:
              configuration:
                rootPath: /hst:config/hst:sites/mysite
            server:
              port: 8080
        """.trimIndent()

        val issues = runInspection(content, FileType.YAML, "hst-config.yaml")

        assertEquals(0, issues.size)
    }

    @Test
    fun `should allow correct rootPath in YAML flat format`() {
        val content = """
            hst.configuration.rootPath: /hst:config/hst:sites/mysite
            server:
              port: 8080
        """.trimIndent()

        val issues = runInspection(content, FileType.YAML, "hst-config.yaml")

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect invalid path in YAML`() {
        val content = """
            hst:
              configuration:
                rootPath: /mysite
        """.trimIndent()

        val issues = runInspection(content, FileType.YAML, "hst-config.yaml")

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should detect empty rootPath in YAML`() {
        val content = """
            hst:
              configuration:
                rootPath:
        """.trimIndent()

        val issues = runInspection(content, FileType.YAML, "hst-config.yaml")

        assertEquals(1, issues.size)
    }

    @Test
    fun `should handle YAML with multiple hst instances`() {
        val content = """
            hst:
              configuration:
                rootPath: /hst:config/hst:sites/site1
                maxPoolSize: 100
              other:
                setting: value
        """.trimIndent()

        val issues = runInspection(content, FileType.YAML, "hst-config.yaml")

        assertEquals(0, issues.size)
    }

    @Test
    fun `should ignore comments in properties file`() {
        val content = """
            # This is a comment
            # hst.configuration.rootPath=/commented/path
            hst.configuration.rootPath=/hst:config/hst:sites/mysite
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(0, issues.size)
    }

    @Test
    fun `should handle properties with spaces around equals sign`() {
        val content = """
            hst.configuration.rootPath = /hst:config/hst:sites/mysite
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(0, issues.size)
    }

    @Test
    fun `should provide helpful description with examples`() {
        val content = """
            # No hst.configuration.rootPath
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size)
        val issue = issues[0]
        assertTrue(issue.description.contains("HST"), "Should mention HST")
        assertTrue(issue.description.contains("Channel Manager"), "Should mention Channel Manager")
        assertTrue(issue.description.contains("/hst:config/hst:sites"), "Should include example path")
        assertTrue(issue.description.contains("properties"), "Should mention file format")
    }

    @Test
    fun `should include metadata about missing property`() {
        val content = """
            server.port=8080
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("propertyName"))
        assertEquals("hst.configuration.rootPath", issues[0].metadata["propertyName"])
        assertEquals("missing", issues[0].metadata["status"])
    }

    @Test
    fun `should detect missing value issue`() {
        val content = """
            hst.configuration.rootPath
            server.port=8080
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
        assertEquals("novalue", issues[0].metadata["status"])
    }

    @Test
    fun `should handle complex YAML structures`() {
        val content = """
            hst:
              configuration:
                rootPath: /hst:config/hst:sites/multisite
                connectionPool:
                  maxConnections: 100
            database:
              host: localhost
        """.trimIndent()

        val issues = runInspection(content, FileType.YAML, "hst-config.yaml")

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect empty string in YAML`() {
        val content = """
            hst:
              configuration:
                rootPath: ""
        """.trimIndent()

        val issues = runInspection(content, FileType.YAML, "hst-config.yaml")

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should detect path without proper structure`() {
        val content = """
            hst.configuration.rootPath=/config/mysite
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(1, issues.size, "Path missing hst namespace or keyword"
        )
    }

    @Test
    fun `should allow paths with hst keyword anywhere`() {
        val content = """
            hst.configuration.rootPath=/content/myhst/config
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES, "hst-config.properties")

        assertEquals(0, issues.size)
    }

    private fun runInspection(content: String, fileType: FileType, fileName: String): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/config/$fileName")
            override val name: String = fileName
            override val extension: String = when (fileType) {
                FileType.PROPERTIES -> "properties"
                FileType.YAML -> "yaml"
                else -> "properties"
            }
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }

        val context = InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = content,
            language = fileType,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }
}
