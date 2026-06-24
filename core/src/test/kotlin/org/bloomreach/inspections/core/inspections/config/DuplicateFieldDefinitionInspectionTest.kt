package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateFieldDefinitionInspectionTest {

    private val inspection = DuplicateFieldDefinitionInspection()

    private fun docTypeXml(fields: String) = """
        <?xml version="1.0"?>
        <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
            <sv:property sv:name="jcr:primaryType">
                <sv:value>hipposys:templatetype</sv:value>
            </sv:property>
            <sv:node sv:name="editor:templates">
                $fields
            </sv:node>
        </sv:node>
    """.trimIndent()

    @Test
    fun `should detect numbered field duplicates`() {
        val xml = docTypeXml("""
            <sv:node sv:name="image1"><sv:property sv:name="field"><sv:value>ns:image1</sv:value></sv:property></sv:node>
            <sv:node sv:name="image2"><sv:property sv:name="field"><sv:value>ns:image2</sv:value></sv:property></sv:node>
        """.trimIndent())
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("ns:image"))
    }

    @Test
    fun `should detect three numbered fields`() {
        val xml = docTypeXml("""
            <sv:property sv:name="field"><sv:value>myns:link1</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>myns:link2</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>myns:link3</sv:value></sv:property>
        """.trimIndent())
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata["fields"].toString().contains("myns:link"))
    }

    @Test
    fun `should not flag unique field names`() {
        val xml = docTypeXml("""
            <sv:property sv:name="field"><sv:value>ns:title</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>ns:summary</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>ns:image</sv:value></sv:property>
        """.trimIndent())
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should not flag non-document-type XML`() {
        val xml = """
            <?xml version="1.0"?>
            <config>
                <item>ns:link1</item>
                <item>ns:link2</item>
            </config>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should include base name and fields in metadata`() {
        val xml = docTypeXml("""
            <sv:property sv:name="field"><sv:value>ns:banner1</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>ns:banner2</sv:value></sv:property>
        """.trimIndent())
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata["baseName"].toString().contains("ns:banner"))
        assertTrue(issues[0].metadata["fields"].toString().contains("ns:banner1"))
        assertTrue(issues[0].metadata["fields"].toString().contains("ns:banner2"))
    }

    @Test
    fun `should detect multiple groups of duplicates`() {
        val xml = docTypeXml("""
            <sv:property sv:name="field"><sv:value>ns:image1</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>ns:image2</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>ns:link1</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>ns:link2</sv:value></sv:property>
        """.trimIndent())
        assertEquals(2, runInspection(xml).size)
    }

    @Test
    fun `should report HINT severity`() {
        val xml = docTypeXml("""
            <sv:property sv:name="field"><sv:value>ns:image1</sv:value></sv:property>
            <sv:property sv:name="field"><sv:value>ns:image2</sv:value></sv:property>
        """.trimIndent())
        assertEquals(Severity.HINT, runInspection(xml)[0].severity)
    }

    @Test
    fun `should handle empty document type gracefully`() {
        val xml = docTypeXml("")
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
