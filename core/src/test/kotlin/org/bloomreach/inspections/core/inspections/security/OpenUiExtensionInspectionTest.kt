package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenUiExtensionInspectionTest {

    private val inspection = OpenUiExtensionInspection()

    @Test
    fun `should detect OpenUiStringFieldPlugin`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="plugin.class">
                    <sv:value>org.hippoecm.frontend.editor.plugins.openui.OpenUiStringFieldPlugin</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("Open UI Extension"))
    }

    @Test
    fun `should detect generic OpenUiPlugin class`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="plugin.class">
                    <sv:value>com.example.cms.OpenUiPlugin</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml).size)
    }

    @Test
    fun `should detect OpenUiField class`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="plugin.class">
                    <sv:value>org.onehippo.cms.channelmanager.content.document.util.OpenUiStringField</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml).size)
    }

    @Test
    fun `should not flag standard CKEditor plugin`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="plugin.class">
                    <sv:value>org.hippoecm.frontend.editor.plugins.field.PropertyFieldPlugin</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should not flag XML without openui references`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml).size)
    }

    @Test
    fun `should detect multiple Open UI extensions`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="plugin.class">
                    <sv:value>com.example.OpenUiPlugin</sv:value>
                </sv:property>
                <sv:property sv:name="plugin.class">
                    <sv:value>com.example.OpenUiStringFieldPlugin</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(2, runInspection(xml).size)
    }

    @Test
    fun `should include plugin class name in metadata`() {
        val xml = """
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="plugin.class">
                    <sv:value>org.example.OpenUiPlugin</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml)
        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("pluginClass"))
        assertTrue(issues[0].metadata["pluginClass"].toString().contains("OpenUi"))
    }

    @Test
    fun `should report WARNING severity`() {
        val xml = """
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="plugin.class">
                    <sv:value>org.example.OpenUiPlugin</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(Severity.WARNING, runInspection(xml)[0].severity)
    }

    @Test
    fun `should handle empty file gracefully`() {
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
