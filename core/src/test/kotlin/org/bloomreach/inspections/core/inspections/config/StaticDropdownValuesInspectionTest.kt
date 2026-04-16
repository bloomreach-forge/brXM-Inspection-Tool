package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticDropdownValuesInspectionTest {

    private val inspection = StaticDropdownValuesInspection()

    @Test
    fun `should detect static label-value pairs in source property`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="source">
                    <sv:value>Monday|monday,Tuesday|tuesday,Wednesday|wednesday</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("Static dropdown"))
    }

    @Test
    fun `should detect single label-value pair`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="source">
                    <sv:value>Left|left</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml).size)
    }

    @Test
    fun `should not flag dynamic sourceId`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="sourceId">
                    <sv:value>weekdays</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should not flag JCR path values in source`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="source">
                    <sv:value>/content/documents/myproject</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should not flag XML without pipe characters`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="source">
                    <sv:value>plainvalue</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should include static values in metadata`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="source">
                    <sv:value>Red|red,Blue|blue</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("staticValues"))
        assertTrue(issues[0].metadata["staticValues"].toString().contains("|"))
    }

    @Test
    fun `should detect multiple static dropdowns in same file`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="source">
                    <sv:value>Red|red,Blue|blue</sv:value>
                </sv:property>
                <sv:property sv:name="source">
                    <sv:value>Small|small,Medium|medium,Large|large</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(2, runInspection(xml).size)
    }

    @Test
    fun `should report ERROR severity`() {
        val xml = """
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="source"><sv:value>A|a,B|b</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should handle empty XML gracefully`() {
        val xml = """<?xml version="1.0"?><sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0"/>"""
        assertEquals(0, runInspection(xml).size)
    }

    private fun runInspection(content: String): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/project/namespaces/myns.xml")
            override val name: String = "myns.xml"
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
