package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentTypeLockInspectionTest {

    private val inspection = ContentTypeLockInspection()

    @Test
    fun `should detect hipposys-locked set to true`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hipposys:locked">
                    <sv:value>true</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("lock"))
    }

    @Test
    fun `should detect jcr-lockOwner presence`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="jcr:lockOwner">
                    <sv:value>admin</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml).size)
    }

    @Test
    fun `should not flag hipposys-locked set to false`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hipposys:locked">
                    <sv:value>false</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should not flag XML without lock properties`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="jcr:primaryType">
                    <sv:value>hipposys:templatetype</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should detect lock property with yes value`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hipposys:locked">
                    <sv:value>yes</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml).size)
    }

    @Test
    fun `should include property name in metadata`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hipposys:locked">
                    <sv:value>true</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertEquals("hipposys:locked", issues[0].metadata["propertyName"])
    }

    @Test
    fun `should detect multiple locked properties`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hipposys:locked"><sv:value>true</sv:value></sv:property>
                <sv:property sv:name="jcr:lockOwner"><sv:value>editor</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(2, runInspection(xml).size)
    }

    @Test
    fun `should report WARNING severity`() {
        val xml = """
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hipposys:locked"><sv:value>true</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(Severity.WARNING, runInspection(xml)[0].severity)
    }

    @Test
    fun `should handle empty XML gracefully`() {
        assertEquals(0, runInspection("<?xml version=\"1.0\"?><root/>").size)
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
