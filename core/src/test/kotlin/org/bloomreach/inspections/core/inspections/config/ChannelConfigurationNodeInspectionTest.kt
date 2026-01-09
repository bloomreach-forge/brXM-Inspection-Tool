package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelConfigurationNodeInspectionTest {

    private val inspection = ChannelConfigurationNodeInspection()

    @Test
    fun `should detect channel node directly under configuration`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:property sv:name="jcr:primaryType" sv:type="Name">
                    <sv:value>nt:folder</sv:value>
                </sv:property>
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>hst:channel</sv:value>
                    </sv:property>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(1, issues.size, "Should detect channel directly under configuration")
        assertEquals(Severity.WARNING, issues[0].severity)
        assertTrue(issues[0].message.contains("hst:channel"))
        assertTrue(issues[0].message.contains("workspace"))
    }

    @Test
    fun `should allow channel under workspace`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:property sv:name="jcr:primaryType" sv:type="Name">
                    <sv:value>nt:folder</sv:value>
                </sv:property>
                <sv:node sv:name="hst:workspace">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>nt:folder</sv:value>
                    </sv:property>
                    <sv:node sv:name="hst:channel">
                        <sv:property sv:name="jcr:primaryType" sv:type="Name">
                            <sv:value>hst:channel</sv:value>
                        </sv:property>
                    </sv:node>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(0, issues.size, "Should allow channel under workspace")
    }

    @Test
    fun `should detect locked true on configuration with channels`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:property sv:name="jcr:primaryType" sv:type="Name">
                    <sv:value>nt:folder</sv:value>
                </sv:property>
                <sv:property sv:name="hst:locked" sv:type="Boolean">
                    <sv:value>true</sv:value>
                </sv:property>
                <sv:node sv:name="hst:workspace">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>nt:folder</sv:value>
                    </sv:property>
                    <sv:node sv:name="hst:channel">
                        <sv:property sv:name="jcr:primaryType" sv:type="Name">
                            <sv:value>hst:channel</sv:value>
                        </sv:property>
                    </sv:node>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(1, issues.size)
        assertEquals(Severity.WARNING, issues[0].severity)
        assertTrue(issues[0].message.contains("read-only"))
    }

    @Test
    fun `should allow locked false`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:property sv:name="jcr:primaryType" sv:type="Name">
                    <sv:value>nt:folder</sv:value>
                </sv:property>
                <sv:property sv:name="hst:locked" sv:type="Boolean">
                    <sv:value>false</sv:value>
                </sv:property>
                <sv:node sv:name="hst:workspace">
                    <sv:property sv:name="jcr:primaryType" sv:type="Name">
                        <sv:value>nt:folder</sv:value>
                    </sv:property>
                    <sv:node sv:name="hst:channel">
                        <sv:property sv:name="jcr:primaryType" sv:type="Name">
                            <sv:value>hst:channel</sv:value>
                        </sv:property>
                    </sv:node>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(0, issues.size, "Should allow locked=false")
    }

    @Test
    fun `should detect multiple channel nodes with wrong placement`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:property sv:name="jcr:primaryType" sv:type="Name">
                    <sv:value>nt:folder</sv:value>
                </sv:property>
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                </sv:node>
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(2, issues.size, "Should flag both incorrectly placed channels")
    }

    @Test
    fun `should not flag locked on non-configuration nodes`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:workspace">
                <sv:property sv:name="jcr:primaryType" sv:type="Name">
                    <sv:value>nt:folder</sv:value>
                </sv:property>
                <sv:property sv:name="hst:locked" sv:type="Boolean">
                    <sv:value>true</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(0, issues.size, "hst:locked on non-configuration nodes is OK")
    }

    @Test
    fun `should allow nested configuration with correct structure`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:config">
                <sv:node sv:name="hst:configurations">
                    <sv:node sv:name="mysite">
                        <sv:property sv:name="jcr:primaryType"><sv:value>nt:folder</sv:value></sv:property>
                        <sv:node sv:name="hst:workspace">
                            <sv:property sv:name="jcr:primaryType"><sv:value>nt:folder</sv:value></sv:property>
                            <sv:node sv:name="hst:channel">
                                <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                            </sv:node>
                        </sv:node>
                    </sv:node>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(0, issues.size, "Should allow correctly nested structure")
    }

    @Test
    fun `should handle configuration with workspace but no channels`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:property sv:name="jcr:primaryType"><sv:value>nt:folder</sv:value></sv:property>
                <sv:property sv:name="hst:locked" sv:type="Boolean">
                    <sv:value>true</sv:value>
                </sv:property>
                <sv:node sv:name="hst:workspace">
                    <sv:property sv:name="jcr:primaryType"><sv:value>nt:folder</sv:value></sv:property>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(0, issues.size, "Should not flag locked workspace with no channels")
    }

    @Test
    fun `should provide helpful description with examples`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(1, issues.size)
        val issue = issues[0]
        assertTrue(issue.description.contains("workspace"), "Should mention workspace")
        assertTrue(issue.description.contains("read-only"), "Should explain impact")
        assertTrue(issue.description.contains("hst:workspace"), "Should show correct structure")
        assertTrue(issue.description.contains("xml"), "Should include XML examples")
    }

    @Test
    fun `should include metadata about node placement issue`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("issue"))
        assertEquals("wrongPlacement", issues[0].metadata["issue"])
        assertTrue(issues[0].metadata.containsKey("nodeName"))
    }

    @Test
    fun `should handle empty configuration node`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:property sv:name="jcr:primaryType"><sv:value>nt:folder</sv:value></sv:property>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect multiple configurations in one file`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:nodes xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:node sv:name="hst:configuration">
                    <sv:node sv:name="hst:channel">
                        <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                    </sv:node>
                </sv:node>
                <sv:node sv:name="hst:configuration">
                    <sv:node sv:name="hst:channel">
                        <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                    </sv:node>
                </sv:node>
            </sv:nodes>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(2, issues.size, "Should detect issues in multiple configurations")
    }

    @Test
    fun `should handle mixed correct and incorrect placements`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:node sv:name="hst:channel">
                    <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                </sv:node>
                <sv:node sv:name="hst:workspace">
                    <sv:node sv:name="hst:channel">
                        <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                    </sv:node>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(1, issues.size, "Should only flag the incorrectly placed one")
    }

    @Test
    fun `should handle case variations in locked`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hst:configuration">
                <sv:property sv:name="hst:locked" sv:type="Boolean">
                    <sv:value>TRUE</sv:value>
                </sv:property>
                <sv:node sv:name="hst:workspace">
                    <sv:node sv:name="hst:channel">
                        <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                    </sv:node>
                </sv:node>
            </sv:node>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(1, issues.size, "Should detect TRUE (uppercase)")
    }

    @Test
    fun `should not flag valid bootstrap file structure`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sv:nodes xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:node sv:name="hst:config">
                    <sv:node sv:name="hst:sites">
                        <sv:node sv:name="mysite">
                            <sv:node sv:name="hst:workspace">
                                <sv:node sv:name="hst:channel">
                                    <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
                                    <sv:property sv:name="hst:channelinfo">channel-info.properties</sv:property>
                                </sv:node>
                            </sv:node>
                        </sv:node>
                    </sv:node>
                </sv:node>
            </sv:nodes>
        """.trimIndent()

        val issues = runInspection(xml)

        assertEquals(0, issues.size, "Should not flag valid bootstrap structure")
    }

    private fun runInspection(xml: String): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/bootstrap/channel-config.xml")
            override val name: String = "channel-config.xml"
            override val extension: String = "xml"
            override fun readText(): String = xml
            override fun exists(): Boolean = true
            override fun size(): Long = xml.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }

        val context = InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = xml,
            language = FileType.XML,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }
}
