package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectApprovalCountInspectionTest {

    private val inspection = ProjectApprovalCountInspection()

    @Test
    fun `should detect approval count of 1`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="brxm:requiredNumberOfApprovals" sv:type="Long">
                    <sv:value>1</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("1"))
    }

    @Test
    fun `should detect approval count of 0`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="numberOfApprovals" sv:type="Long">
                    <sv:value>0</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml).size)
    }

    @Test
    fun `should not flag approval count of 2`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="brxm:requiredNumberOfApprovals" sv:type="Long">
                    <sv:value>2</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should not flag approval count of 3`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="brxm:requiredNumberOfApprovals">
                    <sv:value>3</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should not flag XML without approval properties`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="jcr:primaryType">
                    <sv:value>hst:channel</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should detect generic approval property name`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="requiredApprovals">
                    <sv:value>1</sv:value>
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
                <sv:property sv:name="brxm:requiredNumberOfApprovals">
                    <sv:value>1</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertEquals("brxm:requiredNumberOfApprovals", issues[0].metadata["propertyName"])
        assertEquals("1", issues[0].metadata["value"])
    }

    @Test
    fun `should report ERROR severity`() {
        val xml = """
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="numberOfApprovals"><sv:value>1</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    private fun runInspection(content: String): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/project/project-config.xml")
            override val name: String = "project-config.xml"
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
