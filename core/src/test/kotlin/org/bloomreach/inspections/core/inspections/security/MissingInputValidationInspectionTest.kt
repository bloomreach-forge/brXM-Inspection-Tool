package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Paths

class MissingInputValidationInspectionTest {

    private val inspection = MissingInputValidationInspection()

    @Test
    fun `should detect unvalidated input in SQL query`() {
        val code = """
            package com.example;

            public class UserController {
                public void searchUsers() throws Exception {
                    String searchTerm = getParameter("search");
                    String query = "SELECT * FROM users WHERE name = '" + searchTerm + "'";
                    executeQuery(query);
                }

                private String getParameter(String name) { return null; }
                private void executeQuery(String query) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("Unvalidated input"))
        assertTrue(issues[0].message.contains("executeQuery"))
    }

    @Test
    fun `should not flag validated input`() {
        val code = """
            package com.example;

            public class UserController {
                public void searchUsers() throws Exception {
                    String searchTerm = getParameter("search");

                    // Validation
                    if (searchTerm == null || searchTerm.length() > 100) {
                        throw new IllegalArgumentException("Invalid input");
                    }
                    if (!searchTerm.matches("[a-zA-Z0-9]+")) {
                        throw new IllegalArgumentException("Invalid characters");
                    }

                    String query = "SELECT * FROM users WHERE name = '" + searchTerm + "'";
                    executeQuery(query);
                }

                private String getParameter(String name) { return null; }
                private void executeQuery(String query) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect unvalidated file write`() {
        val code = """
            package com.example;

            import java.io.File;

            public class FileUploadController {
                public void uploadFile() throws Exception {
                    FileItem file = getFile("upload");
                    file.write(new File("/uploads/" + file.getName()));
                }

                private FileItem getFile(String name) { return null; }

                interface FileItem {
                    String getName();
                    void write(File file) throws Exception;
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("write"))
    }

    @Test
    fun `should detect unvalidated redirect`() {
        val code = """
            package com.example;

            public class RedirectController {
                public void redirect() throws Exception {
                    String url = getParameter("returnUrl");
                    sendRedirect(url);
                }

                private String getParameter(String name) { return null; }
                private void sendRedirect(String url) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("sendRedirect"))
    }

    @Test
    fun `should not flag input with isEmpty check`() {
        val code = """
            package com.example;

            public class SafeController {
                public void process() throws Exception {
                    String input = getParameter("data");

                    if (input.isEmpty()) {
                        return;
                    }

                    setAttribute("data", input);
                }

                private String getParameter(String name) { return null; }
                private void setAttribute(String name, String value) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should not flag input with matches validation`() {
        val code = """
            package com.example;

            public class ValidatingController {
                public void process() throws Exception {
                    String input = getParameter("id");

                    if (!input.matches("[0-9]+")) {
                        throw new IllegalArgumentException("Invalid ID");
                    }

                    String query = "SELECT * FROM items WHERE id = " + input;
                    createQuery(query);
                }

                private String getParameter(String name) { return null; }
                private void createQuery(String query) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect multiple unvalidated inputs`() {
        val code = """
            package com.example;

            public class MultiInputController {
                public void process() throws Exception {
                    String username = getParameter("username");
                    String password = getParameter("password");

                    String query1 = "SELECT * FROM users WHERE username = '" + username + "'";
                    String query2 = "AND password = '" + password + "'";

                    createQuery(query1);
                    createQuery(query2);
                }

                private String getParameter(String name) { return null; }
                private void createQuery(String query) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(2, issues.size)
    }

    @Test
    fun `should not flag non-input variables`() {
        val code = """
            package com.example;

            public class SafeController {
                public void process() throws Exception {
                    String hardcoded = "SELECT * FROM users";
                    createQuery(hardcoded);
                }

                private void createQuery(String query) {}
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    private fun runInspection(
        code: String,
        file: VirtualFile = createVirtualFile("Test.java", code)
    ): List<InspectionIssue> {
        val context = InspectionContext(
            projectRoot = Paths.get("/test"),
            file = file,
            fileContent = code,
            language = FileType.JAVA,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }

    private fun createVirtualFile(name: String, content: String): VirtualFile {
        return object : VirtualFile {
            override val path: java.nio.file.Path = Paths.get("/test/$name")
            override val name: String = name
            override val extension: String = "java"
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
    }
}
