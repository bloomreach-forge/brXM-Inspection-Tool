package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissingJspEscapingInspectionTest {

    private val inspection = MissingJspEscapingInspection()

    @Test
    fun `should detect direct output of request parameter`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;

            public class UserServlet {
                public void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    String title = request.getParameter("title");
                    response.getWriter().println("<h1>" + title + "</h1>");
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        assertEquals(1, issues.size, "Should detect unescaped output of request parameter")
        assertTrue(issues[0].message.contains("XSS Risk"))
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should detect response print with getParameter`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;

            public class UserServlet {
                public void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    response.getWriter().print(request.getParameter("content"));
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        assertEquals(1, issues.size, "Should detect print with getParameter")
    }

    @Test
    fun `should detect response println with getAttribute`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;

            public class UserServlet {
                public void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    response.getWriter().println(request.getAttribute("user"));
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        assertEquals(1, issues.size, "Should detect println with getAttribute")
    }

    @Test
    fun `should not flag escaped content`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import org.apache.commons.text.StringEscapeUtils;

            public class UserServlet {
                public void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    String title = StringEscapeUtils.escapeHtml4(request.getParameter("title"));
                    response.getWriter().println("<h1>" + title + "</h1>");
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        // After escaping, should not trigger the simple pattern matching
        // (Pattern looks for direct getParameter in print, escaped would be in separate variable)
        assertTrue(issues.isEmpty(), "Should pass with escaped content")
    }

    @Test
    fun `should not flag simple response writes without user input`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletResponse;

            public class UserServlet {
                public void doGet(HttpServletResponse response) throws Exception {
                    response.getWriter().println("<h1>Static Title</h1>");
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        assertEquals(0, issues.size, "Should not flag static output")
    }

    @Test
    fun `should provide quick fix suggestions`() {
        val code = """
            package com.example;

            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;

            public class UserServlet {
                public void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    response.getWriter().print(request.getParameter("title"));
                }
            }
        """.trimIndent()

        val issues = runInspection(code, FileType.JAVA)

        assertEquals(1, issues.size)
        val quickFixes = inspection.getQuickFixes(issues[0])
        assertTrue(quickFixes.isNotEmpty(), "Should provide quick fixes")
        assertTrue(
            quickFixes.any { it.name.contains("escap") },
            "Should suggest escaping"
        )
    }

    // Helper methods

    private fun runInspection(content: String, fileType: FileType): List<InspectionIssue> {
        val file = createVirtualFile("test.jsp", content)
        val context = InspectionContext(
            file = file,
            fileContent = content,
            language = fileType,
            projectRoot = Path.of("/test"),
            projectIndex = ProjectIndex(),
            config = InspectionConfig(),
            cache = InspectionCache()
        )

        return inspection.inspect(context)
    }

    private fun createVirtualFile(name: String, content: String): VirtualFile {
        return object : VirtualFile {
            override val path: Path = Path.of("/test/$name")
            override val name: String = name
            override val extension: String = "java"
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
    }
}
