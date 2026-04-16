package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HstConfigurationBloatInspectionTest {

    private val inspection = HstConfigurationBloatInspection()

    private fun xmlWithChannels(count: Int): String {
        val channels = (1..count).joinToString("\n") { i ->
            """
            <sv:node sv:name="site$i">
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
            </sv:node>""".trimIndent()
        }
        return """<?xml version="1.0"?><sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">$channels</sv:node>"""
    }

    @Test
    fun `should not flag file with fewer than threshold channels`() {
        assertEquals(0, runInspection(xmlWithChannels(4)).size)
    }

    @Test
    fun `should flag file at threshold`() {
        assertEquals(1, runInspection(xmlWithChannels(5)).size)
    }

    @Test
    fun `should flag file with many channels`() {
        assertEquals(1, runInspection(xmlWithChannels(10)).size)
    }

    @Test
    fun `should include channel count in message`() {
        val issues = runInspection(xmlWithChannels(7))
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("7"))
    }

    @Test
    fun `should include count in metadata`() {
        val issues = runInspection(xmlWithChannels(6))
        assertEquals(1, issues.size)
        assertEquals("6", issues[0].metadata["channelCount"])
    }

    @Test
    fun `should not flag non-HST XML`() {
        val xml = """<?xml version="1.0"?><config><property name="foo" value="bar"/></config>"""
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should count hst-site nodes too`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:site</sv:value></sv:property>
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:site</sv:value></sv:property>
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:site</sv:value></sv:property>
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:site</sv:value></sv:property>
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:site</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml).size)
    }

    @Test
    fun `should report ERROR severity`() {
        val issues = runInspection(xmlWithChannels(5))
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should handle empty file gracefully`() {
        assertEquals(0, runInspection("<?xml version=\"1.0\"?><root/>").size)
    }

    private fun runInspection(content: String): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/project/hst-config.xml")
            override val name: String = "hst-config.xml"
            override val extension: String = "xml"
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
        return inspection.inspect(InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = content,
            language = FileType.XML,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        ))
    }
}
