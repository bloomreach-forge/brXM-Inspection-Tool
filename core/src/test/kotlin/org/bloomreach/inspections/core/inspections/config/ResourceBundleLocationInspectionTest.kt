package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class ResourceBundleLocationInspectionTest {

    private val inspection = ResourceBundleLocationInspection()

    private val bundleXml = """
        <?xml version="1.0"?>
        <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
            <sv:property sv:name="jcr:primaryType">
                <sv:value>hippo:bundle</sv:value>
            </sv:property>
            <sv:property sv:name="hippo:translation" sv:type="String">
                <sv:value>Submit</sv:value>
            </sv:property>
        </sv:node>
    """.trimIndent()

    @Test
    fun `should flag bundle file not in administration or translations path`() {
        val issues = runInspection(bundleXml, "/project/bootstrap/content/mybundles.xml")
        assertEquals(1, issues.size)
    }

    @Test
    fun `should not flag bundle file in administration path`() {
        val issues = runInspection(bundleXml, "/project/bootstrap/administration/mybundles.xml")
        assertEquals(0, issues.size)
    }

    @Test
    fun `should not flag bundle file in translations path`() {
        val issues = runInspection(bundleXml, "/project/bootstrap/translations/mybundles.xml")
        assertEquals(0, issues.size)
    }

    @Test
    fun `should not flag bundle file named resourcebundle`() {
        val issues = runInspection(bundleXml, "/project/bootstrap/myns-resourcebundle.xml")
        assertEquals(0, issues.size)
    }

    @Test
    fun `should not flag XML without hippo-bundle content`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="jcr:primaryType"><sv:value>hst:channel</sv:value></sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(0, runInspection(xml, "/project/hst-config.xml").size)
    }

    @Test
    fun `should not flag bundle with correct JCR path in XML`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0" sv:name="hippo:translations">
                <sv:property sv:name="jcr:primaryType"><sv:value>hippo:bundle</sv:value></sv:property>
                <!-- hippo:configuration path included -->
            </sv:node>
        """.trimIndent()
        // Content contains both hippo:translations and hippo:configuration → not flagged
        val xmlWithPath = xml.replace("<!-- hippo:configuration path included -->",
            "<!-- hippo:configuration/hippo:translations -->")
        assertEquals(0, runInspection(xmlWithPath, "/project/bootstrap/some/bundles.xml").size)
    }

    @Test
    fun `should detect hipposys-resourcebundles type outside standard location`() {
        val xml = """
            <?xml version="1.0"?>
            <sv:node xmlns:sv="http://www.jcp.org/jcr/sv/1.0">
                <sv:property sv:name="jcr:primaryType">
                    <sv:value>hipposys:resourcebundles</sv:value>
                </sv:property>
            </sv:node>
        """.trimIndent()
        assertEquals(1, runInspection(xml, "/project/bootstrap/misc/bundles.xml").size)
    }

    @Test
    fun `should report HINT severity`() {
        val issues = runInspection(bundleXml, "/project/bootstrap/content/bundles.xml")
        assertEquals(Severity.HINT, issues[0].severity)
    }

    private fun runInspection(content: String, filePath: String): List<InspectionIssue> {
        val path = Path.of(filePath)
        val file = object : VirtualFile {
            override val path: Path = path
            override val name: String = path.fileName.toString()
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
