package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticRequestSessionInspectionTest {

    private val inspection = StaticRequestSessionInspection()

    @Test
    fun `should detect static HttpServletRequest field`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;

            public class RequestHolder {
                private static HttpServletRequest request; // ⚠️ DANGEROUS
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect static HttpServletRequest")
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("Static field"))
    }

    @Test
    fun `should detect static HttpSession field`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpSession;

            public class SessionManager {
                private static HttpSession session; // ⚠️ DANGEROUS
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect static HttpSession")
    }

    @Test
    fun `should detect static JCR Session field`() {
        val code = """
            package com.example;

            import javax.jcr.Session;

            public class RepositoryService {
                private static Session jcrSession; // ⚠️ DANGEROUS
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect static JCR Session")
    }

    @Test
    fun `should detect static HstRequest field`() {
        val code = """
            package com.example;

            import org.hippoecm.hst.core.request.HstRequest;

            public class MyComponent {
                private static HstRequest request; // ⚠️ DANGEROUS
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect static HstRequest")
    }

    @Test
    fun `should detect static PageContext field`() {
        val code = """
            package com.example;

            import javax.servlet.jsp.PageContext;

            public class JspComponent {
                private static PageContext pageContext; // ⚠️ DANGEROUS
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect static PageContext")
    }

    @Test
    fun `should allow non-static request fields`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;

            public class RequestHolder {
                private HttpServletRequest request; // ✓ OK - not static
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should allow non-static request fields")
    }

    @Test
    fun `should allow static ThreadLocal of request type`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;

            public class RequestContextHolder {
                private static final ThreadLocal<HttpServletRequest> requestHolder =
                    new ThreadLocal<>(); // ✓ OK - ThreadLocal is thread-safe
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should allow static ThreadLocal<Request>")
    }

    @Test
    fun `should allow static ServletContext`() {
        val code = """
            package com.example;

            import javax.servlet.ServletContext;

            public class AppConfig {
                private static ServletContext context; // ✓ OK - ServletContext is thread-safe
            }
        """.trimIndent()

        val issues = runInspection(code)

        // ServletContext is actually application-scoped, not request-scoped
        // So this should be allowed
        assertEquals(0, issues.size, "Should allow static ServletContext")
    }

    @Test
    fun `should detect multiple dangerous static fields`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpSession;
            import javax.jcr.Session;

            public class RequestManager {
                private static HttpServletRequest request;  // ⚠️ 1
                private static HttpSession session;          // ⚠️ 2
                private static Session jcrSession;           // ⚠️ 3
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(3, issues.size, "Should detect all dangerous static fields")
    }

    @Test
    fun `should detect static field in HST component`() {
        val code = """
            package com.example;

            import org.hippoecm.hst.core.component.HstComponentException;
            import org.hippoecm.hst.core.component.BaseHstComponent;
            import org.hippoecm.hst.core.request.HstRequest;
            import org.hippoecm.hst.core.request.HstResponse;

            public class MyComponent extends BaseHstComponent {
                private static HstRequest currentRequest; // ⚠️ DANGEROUS

                @Override
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    currentRequest = request; // Thread safety violation!
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect static HstRequest in HST component")
    }

    @Test
    fun `should detect static custom Request-like class`() {
        val code = """
            package com.example;

            public class CustomRequest {
                private String url;
                private String method;
            }

            public class RequestHolder {
                private static CustomRequest myRequest; // ⚠️ Likely dangerous
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect static custom Request-like class")
    }

    @Test
    fun `should not flag static logger fields`() {
        val code = """
            package com.example;

            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            public class Service {
                private static final Logger logger = LoggerFactory.getLogger(Service.class); // ✓ OK
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag static Logger fields")
    }

    @Test
    fun `should provide quick fix suggestions`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;

            public class RequestHolder {
                private static HttpServletRequest request;
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        val quickFixes = inspection.getQuickFixes(issues[0])
        assertTrue(quickFixes.isNotEmpty(), "Should provide quick fixes")
        assertTrue(quickFixes.any { it.name.contains("ThreadLocal") }, "Should suggest ThreadLocal")
        assertTrue(quickFixes.any { it.name.contains("@Inject") || it.name.contains("inject") }, "Should suggest injection")
        assertTrue(quickFixes.any { it.name.contains("local") }, "Should suggest local variable")
    }

    @Test
    fun `should detect static Response field`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletResponse;

            public class ResponseManager {
                private static HttpServletResponse response; // ⚠️ DANGEROUS
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect static HttpServletResponse")
    }

    @Test
    fun `should include metadata in issue`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;

            public class RequestHolder {
                private static HttpServletRequest request;
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("fieldName"), "Should include fieldName")
        assertTrue(issues[0].metadata.containsKey("fieldType"), "Should include fieldType")
        assertEquals("CRITICAL", issues[0].metadata["severity"], "Should mark as CRITICAL")
    }

    // Helper methods

    private fun runInspection(code: String): List<InspectionIssue> {
        val file = createVirtualFile("RequestHolder.java", code)
        val context = InspectionContext(
            file = file,
            fileContent = code,
            language = FileType.JAVA,
            projectRoot = Path.of("/test"),
            projectIndex = ProjectIndex(),
            config = InspectionConfig(),
            cache = InspectionCache()
        )

        return inspection.inspect(context)
    }

    private fun createVirtualFile(name: String, content: String): VirtualFile {
        return object : VirtualFile {
            override val path: Path = Path.of("/test/src/$name")
            override val name: String = name
            override val extension: String = "java"
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
    }
}
