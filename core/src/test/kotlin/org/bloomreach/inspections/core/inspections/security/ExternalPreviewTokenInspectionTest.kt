package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalPreviewTokenInspectionTest {

    private val inspection = ExternalPreviewTokenInspection()

    @Test
    fun `should detect previewTokensEnabled set to true in XML`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hst:previewTokensEnabled">
                    <sv:value>true</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml, FileType.XML)
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("preview token"))
    }

    @Test
    fun `should detect previewToken property with true value`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="previewToken">
                    <sv:value>true</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml, FileType.XML).size)
    }

    @Test
    fun `should not flag previewToken set to false`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hst:previewTokensEnabled">
                    <sv:value>false</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml, FileType.XML).size)
    }

    @Test
    fun `should detect previewToken enabled in YAML`() {
        val yaml = """
            channel:
              previewTokensEnabled: true
        """.trimIndent()
        assertEquals(1, runInspection(yaml, FileType.YAML, "channel.yaml").size)
    }

    @Test
    fun `should not flag previewToken disabled in YAML`() {
        val yaml = """
            channel:
              previewTokensEnabled: false
        """.trimIndent()
        assertEquals(0, runInspection(yaml, FileType.YAML, "channel.yaml").size)
    }

    @Test
    fun `should detect case-insensitive true variants`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="previewTokenEnabled">
                    <sv:value>yes</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml, FileType.XML).size)
    }

    @Test
    fun `should not flag XML without preview token references`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml, FileType.XML).size)
    }

    @Test
    fun `should report WARNING severity`() {
        val xml = """
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="previewToken"><sv:value>true</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml, FileType.XML)
        assertEquals(Severity.WARNING, issues[0].severity)
    }

    @Test
    fun `should include property name in metadata`() {
        val xml = """
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="hst:previewTokensEnabled"><sv:value>true</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        val issues = runInspection(xml, FileType.XML)
        assertEquals("hst:previewTokensEnabled", issues[0].metadata["propertyName"])
    }

    private fun runInspection(content: String, fileType: FileType, fileName: String = "channel.xml"): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/project/$fileName")
            override val name: String = fileName
            override val extension: String = fileName.substringAfterLast('.')
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
        return inspection.inspect(InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = content,
            language = fileType,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        ))
    }
}
