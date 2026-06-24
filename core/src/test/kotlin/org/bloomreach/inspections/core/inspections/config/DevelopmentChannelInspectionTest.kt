package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevelopmentChannelInspectionTest {

    private val inspection = DevelopmentChannelInspection()

    private fun hstXml(nodeName: String, channelType: String = "hst:channel") = """
        <?xml version="1.0"?>
        <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="$nodeName">
            <sv:property sv:name="jcr:primaryType" sv:type="Name">
                <sv:value>$channelType</sv:value>
            </sv:property>
        </sv:node>
    """.trimIndent()

    @Test
    fun `should detect channel ending in -dev`() {
        val issues = runInspection(hstXml("mysite-dev"))
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("mysite-dev"))
    }

    @Test
    fun `should detect channel ending in -test`() {
        assertEquals(1, runInspection(hstXml("mysite-test")).size)
    }

    @Test
    fun `should detect channel ending in -uat`() {
        assertEquals(1, runInspection(hstXml("mysite-uat")).size)
    }

    @Test
    fun `should detect channel ending in -staging`() {
        assertEquals(1, runInspection(hstXml("mysite-staging")).size)
    }

    @Test
    fun `should detect channel starting with dev-`() {
        assertEquals(1, runInspection(hstXml("dev-mysite")).size)
    }

    @Test
    fun `should detect channel named exactly qa`() {
        assertEquals(1, runInspection(hstXml("qa")).size)
    }

    @Test
    fun `should not flag legitimate production channel names`() {
        assertEquals(0, runInspection(hstXml("mysite")).size)
        assertEquals(0, runInspection(hstXml("corporate-site")).size)
        assertEquals(0, runInspection(hstXml("webshop")).size)
    }

    @Test
    fun `should not flag non-HST XML files`() {
        val xml = """
            <?xml version="1.0"?>
            <config><property name="channel" value="mysite-dev"/></config>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should include matched keyword in metadata`() {
        val issues = runInspection(hstXml("project-staging"))
        assertEquals(1, issues.size)
        assertEquals("staging", issues[0].metadata["matchedKeyword"])
        assertEquals("project-staging", issues[0].metadata["channelName"])
    }

    @Test
    fun `should report ERROR severity`() {
        val issues = runInspection(hstXml("mysite-dev"))
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should detect multiple dev channels in one file`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:node sv:name="site-dev">
                    <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                </sv:node>
                <sv:node sv:name="site-uat">
                    <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                </sv:node>
            </sv:node>
        """.trimIndent()
        assertEquals(2, runInspection(xml).size)
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
