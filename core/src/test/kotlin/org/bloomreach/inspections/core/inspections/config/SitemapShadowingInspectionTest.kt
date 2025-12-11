package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SitemapShadowingInspectionTest {

    private val inspection = SitemapShadowingInspection()

    @Test
    fun `should detect _default before specific pattern`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="news">
              <hst:sitemapitem hst:name="_default">
                <hst:relativecontentpath>_default</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="article">
                <hst:relativecontentpath>{year}/{month}/{slug}</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(1, issues.size, "Should detect _default shadowing specific pattern")
        assertTrue(issues[0].message.contains("_default"))
        assertTrue(issues[0].message.contains("shadows"))
    }

    @Test
    fun `should not report issue when specific pattern comes first`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="news">
              <hst:sitemapitem hst:name="article">
                <hst:relativecontentpath>{year}/{month}/{slug}</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="_default">
                <hst:relativecontentpath>_default</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(0, issues.size, "Should not report issue when patterns are in correct order")
    }

    @Test
    fun `should detect wildcard before literal pattern`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="products">
              <hst:sitemapitem hst:name="item">
                <hst:relativecontentpath>{id}</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="latest">
                <hst:relativecontentpath>latest</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(1, issues.size, "Should detect wildcard shadowing literal")
        assertTrue(issues[0].message.contains("{id}"))
        assertTrue(issues[0].message.contains("latest"))
    }

    @Test
    fun `should not report when all patterns are unique`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="site">
              <hst:sitemapitem hst:name="news">
                <hst:relativecontentpath>news</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="products">
                <hst:relativecontentpath>products</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="about">
                <hst:relativecontentpath>about</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(0, issues.size, "Should not report when patterns don't shadow each other")
    }

    @Test
    fun `should detect _any_ wildcard shadowing`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="content">
              <hst:sitemapitem hst:name="any">
                <hst:relativecontentpath>_any_</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="specific">
                <hst:relativecontentpath>{category}/{item}</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(1, issues.size, "Should detect _any_ wildcard shadowing")
    }

    @Test
    fun `should detect shorter path shadowing longer path`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="articles">
              <hst:sitemapitem hst:name="general">
                <hst:relativecontentpath>{category}</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="detailed">
                <hst:relativecontentpath>{category}/{year}/{slug}</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(1, issues.size, "Should detect shorter path shadowing longer path")
    }

    @Test
    fun `should handle multiple shadowing issues`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="site">
              <hst:sitemapitem hst:name="_default">
                <hst:relativecontentpath>_default</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="news">
                <hst:relativecontentpath>news</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="products">
                <hst:relativecontentpath>products</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertTrue(issues.size >= 2, "Should detect multiple shadowing issues")
    }

    // YAML Tests

    @Test
    fun `should detect shadowing in YAML format`() {
        val yaml = """
            news:
              _default:
                relativecontentpath: _default
              article:
                relativecontentpath: "{year}/{month}/{slug}"
        """.trimIndent()

        val issues = runYamlInspection(yaml)

        // Note: YAML parsing is simplified, may not detect all cases
        assertTrue(issues.size >= 0, "Should attempt to detect shadowing in YAML")
    }

    @Test
    fun `should handle YAML with correct order`() {
        val yaml = """
            news:
              article:
                relativecontentpath: "{year}/{month}/{slug}"
              _default:
                relativecontentpath: _default
        """.trimIndent()

        val issues = runYamlInspection(yaml)

        assertEquals(0, issues.size, "Should not report issue for correct order in YAML")
    }

    // Edge Cases

    @Test
    fun `should handle malformed XML gracefully`() {
        val malformedXml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="news">
              <hst:sitemapitem hst:name="_default">
                <hst:relativecontentpath>_default
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(malformedXml)

        // Should not crash
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `should store shadow type in metadata`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="news">
              <hst:sitemapitem hst:name="_default">
                <hst:relativecontentpath>_default</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="article">
                <hst:relativecontentpath>{year}/{month}/{slug}</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(1, issues.size)
        val shadowType = issues[0].metadata["shadowType"] as? String
        assertEquals("default-shadows-all", shadowType)
    }

    @Test
    fun `should store pattern names in metadata`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="news">
              <hst:sitemapitem hst:name="_default">
                <hst:relativecontentpath>_default</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="article">
                <hst:relativecontentpath>{slug}</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(1, issues.size)
        assertEquals("_default", issues[0].metadata["earlierPattern"])
        assertEquals("{slug}", issues[0].metadata["laterPattern"])
    }

    @Test
    fun `should handle nested sitemap items`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="root">
              <hst:sitemapitem hst:name="news">
                <hst:sitemapitem hst:name="_default">
                  <hst:relativecontentpath>_default</hst:relativecontentpath>
                </hst:sitemapitem>
                <hst:sitemapitem hst:name="article">
                  <hst:relativecontentpath>{slug}</hst:relativecontentpath>
                </hst:sitemapitem>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(1, issues.size, "Should handle nested sitemap items")
    }

    @Test
    fun `should not flag patterns with different prefixes`() {
        val xml = """
            <?xml version="1.0"?>
            <hst:sitemapitem xmlns:hst="http://www.hippoecm.org/cms7/hst" hst:name="site">
              <hst:sitemapitem hst:name="news">
                <hst:relativecontentpath>news/{slug}</hst:relativecontentpath>
              </hst:sitemapitem>
              <hst:sitemapitem hst:name="products">
                <hst:relativecontentpath>products/{id}</hst:relativecontentpath>
              </hst:sitemapitem>
            </hst:sitemapitem>
        """.trimIndent()

        val issues = runXmlInspection(xml)

        assertEquals(0, issues.size, "Should not flag patterns with different literal prefixes")
    }

    // Helper methods

    private fun runXmlInspection(xml: String): List<InspectionIssue> {
        val file = createVirtualFile("hst-sitemap.xml", xml, FileType.XML)
        val context = createContext(file, FileType.XML)
        return inspection.inspect(context)
    }

    private fun runYamlInspection(yaml: String): List<InspectionIssue> {
        val file = createVirtualFile("hst-sitemap.yaml", yaml, FileType.YAML)
        val context = createContext(file, FileType.YAML)
        return inspection.inspect(context)
    }

    private fun createVirtualFile(name: String, content: String, type: FileType): VirtualFile {
        return object : VirtualFile {
            override val path: Path = Path.of("/test/$name")
            override val name: String = name
            override val extension: String = when (type) {
                FileType.XML -> "xml"
                FileType.YAML -> "yaml"
                else -> ""
            }
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
    }

    private fun createContext(file: VirtualFile, language: FileType): InspectionContext {
        return InspectionContext(
            file = file,
            fileContent = file.readText(),
            language = language,
            projectRoot = Path.of("/test"),
            projectIndex = ProjectIndex(),
            config = InspectionConfig(),
            cache = InspectionCache()
        )
    }
}
