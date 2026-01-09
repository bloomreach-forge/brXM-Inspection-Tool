package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoadBalancerAffinity409InspectionTest {

    private val inspection = LoadBalancerAffinity409Inspection()

    @Test
    fun `should detect missing sticky session configuration`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <!-- No sticky-session configured -->
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size, "Should detect missing sticky session")
        assertEquals(Severity.WARNING, issues[0].severity)
        assertTrue(issues[0].message.contains("sticky session"))
    }

    @Test
    fun `should detect sticky session explicitly disabled`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <sticky-session>false</sticky-session>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("disabled"))
    }

    @Test
    fun `should detect sticky session disabled with no value`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <sticky-session>no</sticky-session>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
    }

    @Test
    fun `should allow sticky session enabled`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <sticky-session>true</sticky-session>
                    <session-cookie>JSESSIONID</session-cookie>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should allow sticky session enabled")
    }

    @Test
    fun `should detect missing session cookie routing`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <sticky-session>true</sticky-session>
                    <!-- Missing session-cookie -->
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size, "Should detect missing session cookie")
        assertTrue(issues[0].message.contains("JSESSIONID"))
    }

    @Test
    fun `should detect multiple server configuration without load balancer`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <server>
                    <name>server1</name>
                </server>
                <server>
                    <name>server2</name>
                </server>
                <!-- No load-balancer element -->
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size, "Should detect multi-server without load balancer")
        assertTrue(issues[0].message.contains("Multi-server"))
    }

    @Test
    fun `should allow single server without load balancer`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <server>
                    <name>server1</name>
                </server>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should allow single server without load balancer")
    }

    @Test
    fun `should detect sticky session disabled in properties`() {
        val content = """
            server.port=8080
            load.balancer.sticky-session=false
            logging.level=INFO
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("disabled"))
    }

    @Test
    fun `should allow sticky session enabled in properties`() {
        val content = """
            server.port=8080
            load.balancer.sticky-session=true
            load.balancer.session-cookie=JSESSIONID
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES)

        assertEquals(0, issues.size, "Should allow sticky session enabled in properties")
    }

    @Test
    fun `should skip commented properties`() {
        val content = """
            # load.balancer.sticky-session=false
            server.port=8080
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES)

        assertEquals(0, issues.size, "Should skip commented lines")
    }

    @Test
    fun `should detect sticky session disabled as no`() {
        val content = """
            load.balancer.sticky-session=no
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES)

        assertEquals(1, issues.size)
    }

    @Test
    fun `should detect sticky session disabled as disabled`() {
        val content = """
            load.balancer.sticky-session=disabled
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES)

        assertEquals(1, issues.size)
    }

    @Test
    fun `should handle empty web app configuration`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should handle empty configuration")
    }

    @Test
    fun `should detect mixed XML configuration issues`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <sticky-session>false</sticky-session>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size, "Should detect disabled sticky session")
        assertTrue(issues[0].message.contains("disabled"))
    }

    @Test
    fun `should include metadata about configuration`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <sticky-session>false</sticky-session>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("issue"))
        assertEquals("stickySessionDisabled", issues[0].metadata["issue"])
    }

    @Test
    fun `should provide helpful description with examples`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(1, issues.size)
        val desc = issues[0].description
        assertTrue(desc.contains("409 Conflict"), "Should mention 409 error")
        assertTrue(desc.contains("sticky session"), "Should explain sticky sessions")
        assertTrue(desc.contains("JSESSIONID"), "Should mention JSESSIONID")
    }

    @Test
    fun `should handle properties with affinity keyword`() {
        val content = """
            load.balancer.affinity=false
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES)

        assertEquals(1, issues.size, "Should detect affinity setting"
        )
    }

    @Test
    fun `should allow affinity enabled`() {
        val content = """
            load.balancer.affinity=true
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES)

        assertEquals(0, issues.size, "Should allow affinity enabled")
    }

    @Test
    fun `should handle properties with jsessionid configuration`() {
        val content = """
            load.balancer.sticky-session=true
            server.servlet.session.cookie.name=JSESSIONID
        """.trimIndent()

        val issues = runInspection(content, FileType.PROPERTIES)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect multiple issues in same config`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <server>
                    <name>server1</name>
                </server>
                <server>
                    <name>server2</name>
                </server>
                <load-balancer>
                    <sticky-session>false</sticky-session>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        // Should detect sticky session disabled (and possibly session cookie missing)
        assertTrue(issues.isNotEmpty(), "Should detect configuration issues")
    }

    @Test
    fun `should allow properly configured load balancer`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <sticky-session>true</sticky-session>
                    <session-cookie>JSESSIONID</session-cookie>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should allow properly configured load balancer")
    }

    @Test
    fun `should handle case variations in XML`() {
        val xml = """
            <?xml version="1.0"?>
            <web-app>
                <load-balancer>
                    <sticky-session>TRUE</sticky-session>
                </load-balancer>
            </web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should handle case variations")
    }

    @Test
    fun `should skip invalid XML gracefully`() {
        val xml = """
            This is not valid XML
            <web-app>
        """.trimIndent()

        val issues = runInspection(xml, FileType.XML)

        assertEquals(0, issues.size, "Should handle invalid XML gracefully")
    }

    private fun runInspection(content: String, fileType: FileType, fileName: String = "config.xml"): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/config/$fileName")
            override val name: String = fileName
            override val extension: String = when (fileType) {
                FileType.XML -> "xml"
                FileType.PROPERTIES -> "properties"
                else -> "txt"
            }
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }

        val context = InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = content,
            language = fileType,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }
}
