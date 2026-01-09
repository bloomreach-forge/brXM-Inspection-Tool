package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlCommentStrippingInspectionTest {

    private val inspection = HtmlCommentStrippingInspection()

    @Test
    fun `should detect comment stripping filter in web xml`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>CommentRemovalFilter</filter-name>
                    <filter-class>com.example.CompressionFilter</filter-class>
                    <init-param>
                        <param-name>strip-comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size, "Should detect comment stripping filter")
        assertEquals(Severity.WARNING, issues[0].severity)
        assertTrue(issues[0].message.contains("strips HTML comments"))
    }

    @Test
    fun `should detect remove comments parameter`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>MinifierFilter</filter-name>
                    <filter-class>com.example.MinifyFilter</filter-class>
                    <init-param>
                        <param-name>remove-comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("strips HTML comments"))
    }

    @Test
    fun `should detect compress comments parameter`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>CompressionFilter</filter-name>
                    <filter-class>com.example.CompressionFilter</filter-class>
                    <init-param>
                        <param-name>compress-comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
    }

    @Test
    fun `should allow filter without comment stripping`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>GzipFilter</filter-name>
                    <filter-class>com.example.GzipFilter</filter-class>
                    <init-param>
                        <param-name>compression</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should allow filters without comment stripping")
    }

    @Test
    fun `should allow comment stripping parameter set to false`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>CommentFilter</filter-name>
                    <filter-class>com.example.CompressionFilter</filter-class>
                    <init-param>
                        <param-name>strip-comments</param-name>
                        <param-value>false</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should allow when set to false")
    }

    @Test
    fun `should detect response header removing comments in Java`() {
        val code = """
            import javax.servlet.http.HttpServletResponse;

            public class ResponseFilter {
                public void addHeaders(HttpServletResponse response) {
                    response.addHeader("X-Remove-Comments", "true");
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("strips HTML comments"))
    }

    @Test
    fun `should detect setHeader for comment removal`() {
        val code = """
            import javax.servlet.http.HttpServletResponse;

            public class ResponseProcessor {
                public void process(HttpServletResponse response) {
                    response.setHeader("X-Strip-Comments", "true");
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        assertEquals(1, issues.size)
    }

    @Test
    fun `should skip test files`() {
        val code = """
            import javax.servlet.http.HttpServletResponse;

            public class ResponseFilterTest {
                public void addHeaders(HttpServletResponse response) {
                    response.addHeader("X-Remove-Comments", "true");
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA, "ResponseFilterTest.java")

        assertEquals(0, issues.size, "Should skip test files")
    }

    @Test
    fun `should detect multiple comment stripping configurations`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>CommentFilter1</filter-name>
                    <filter-class>com.example.CompressionFilter</filter-class>
                    <init-param>
                        <param-name>strip-comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
                <filter>
                    <filter-name>CommentFilter2</filter-name>
                    <filter-class>com.example.MinifyFilter</filter-class>
                    <init-param>
                        <param-name>remove-comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(2, issues.size, "Should detect both filters")
    }

    @Test
    fun `should detect minify filter with comment stripping`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>MinifyFilter</filter-name>
                    <filter-class>com.example.MinifyFilter</filter-class>
                    <init-param>
                        <param-name>minify</param-name>
                        <param-value>true</param-value>
                    </init-param>
                    <init-param>
                        <param-name>strip-comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
    }

    @Test
    fun `should include filter name in metadata`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>MyCompressionFilter</filter-name>
                    <filter-class>com.example.Compressor</filter-class>
                    <init-param>
                        <param-name>strip-comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("filterName"))
        assertEquals("MyCompressionFilter", issues[0].metadata["filterName"])
    }

    @Test
    fun `should provide helpful description with examples`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>CompressionFilter</filter-name>
                    <filter-class>com.example.CompressionFilter</filter-class>
                    <init-param>
                        <param-name>strip-comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
        val desc = issues[0].description
        assertTrue(desc.contains("Experience Manager"), "Should mention Experience Manager")
        assertTrue(desc.contains("/admin"), "Should show admin URL example")
        assertTrue(desc.contains("url-pattern"), "Should show XML configuration")
    }

    @Test
    fun `should handle parameter value case variations`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>CompressionFilter</filter-name>
                    <filter-class>com.example.CompressionFilter</filter-class>
                    <init-param>
                        <param-name>strip-comments</param-name>
                        <param-value>enabled</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size, "Should detect 'enabled' as true")
    }

    @Test
    fun `should detect underscore in parameter name`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <filter>
                    <filter-name>CompressionFilter</filter-name>
                    <filter-class>com.example.CompressionFilter</filter-class>
                    <init-param>
                        <param-name>strip_comments</param-name>
                        <param-value>true</param-value>
                    </init-param>
                </filter>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size, "Should detect underscore variant")
    }

    @Test
    fun `should handle empty web app`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should handle empty config")
    }

    @Test
    fun `should detect comment header with variable name variations`() {
        val code = """
            public void configureResponse(HttpServletResponse response) {
                response.addHeader("X-Remove-Comments", "true");
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        assertEquals(1, issues.size)
    }

    private fun runInspection(content: String, fileType: FileType, fileName: String = "config.xml"): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/config/$fileName")
            override val name: String = fileName
            override val extension: String = when (fileType) {
                FileType.XML -> "xml"
                FileType.JAVA -> "java"
                else -> "txt"
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
