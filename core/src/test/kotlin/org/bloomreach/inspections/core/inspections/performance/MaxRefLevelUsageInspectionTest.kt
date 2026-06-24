package org.bloomreach.inspections.core.inspections.performance

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaxRefLevelUsageInspectionTest {

    private val inspection = MaxRefLevelUsageInspection()

    @Test
    fun `should detect _maxreflevel in Java string`() {
        val code = """
            public class ApiClient {
                public String buildUrl(String base) {
                    return base + "?_maxreflevel=3";
                }
            }
        """.trimIndent()
        assertEquals(1, runInspection(code, FileType.JAVA).size)
    }

    @Test
    fun `should detect _maxreflevel in XML attribute`() {
        val xml = """
            <?xml version="1.0"?>
            <config>
                <property name="delivery.url" value="/api?_maxreflevel=2"/>
            </config>
        """.trimIndent()
        assertEquals(1, runInspection(xml, FileType.XML).size)
    }

    @Test
    fun `should detect _maxreflevel in properties file`() {
        val props = """
            api.base.url=https://example.com
            api.documents.url=https://example.com/api/documents?_maxreflevel=3
        """.trimIndent()
        assertEquals(1, runInspection(props, FileType.PROPERTIES, "application.properties").size)
    }

    @Test
    fun `should detect _maxreflevel in YAML`() {
        val yaml = """
            delivery:
              url: "https://example.com/api?_maxreflevel=2"
        """.trimIndent()
        assertEquals(1, runInspection(yaml, FileType.YAML, "config.yaml").size)
    }

    @Test
    fun `should detect case insensitive variant`() {
        val code = """
            String url = base + "?_MAXREFLEVEL=5";
        """.trimIndent()
        assertEquals(1, runInspection(code, FileType.JAVA).size)
    }

    @Test
    fun `should not flag when no _maxreflevel present`() {
        val code = """
            public class ApiClient {
                public String buildUrl() {
                    return "/api/documents?offset=0&limit=10";
                }
            }
        """.trimIndent()
        assertEquals(0, runInspection(code, FileType.JAVA).size)
    }

    @Test
    fun `should skip commented Java lines`() {
        val code = """
            public class ApiClient {
                // Use _maxreflevel=3 if needed
                public String buildUrl() { return "/api"; }
            }
        """.trimIndent()
        assertEquals(0, runInspection(code, FileType.JAVA).size)
    }

    @Test
    fun `should skip commented properties lines`() {
        val props = """
            # api.url=https://example.com?_maxreflevel=2
            api.url=https://example.com
        """.trimIndent()
        assertEquals(0, runInspection(props, FileType.PROPERTIES, "app.properties").size)
    }

    @Test
    fun `should skip test files`() {
        val code = """
            public class ApiClientTest {
                String url = "/api?_maxreflevel=2";
            }
        """.trimIndent()
        assertEquals(0, runInspection(code, FileType.JAVA, "ApiClientTest.java").size)
    }

    @Test
    fun `should detect multiple occurrences`() {
        val code = """
            String url1 = "/api/news?_maxreflevel=2";
            String url2 = "/api/banners?_maxreflevel=3";
        """.trimIndent()
        assertEquals(2, runInspection(code, FileType.JAVA).size)
    }

    @Test
    fun `should report ERROR severity`() {
        val code = """String url = "/api?_maxreflevel=2";"""
        val issues = runInspection(code, FileType.JAVA)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    private fun runInspection(content: String, fileType: FileType, fileName: String = "ApiClient.java"): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/project/$fileName")
            override val name: String = fileName
            override val extension: String = fileName.substringAfterLast('.')
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
        return inspection.inspect(InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = content,
            language = fileType,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        ))
    }
}
