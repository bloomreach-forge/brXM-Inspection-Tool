package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityHeaderConfigurationInspectionTest {

    private val inspection = SecurityHeaderConfigurationInspection()

    @Test
    fun `should detect frameOptions deny in Spring Security config`() {
        val code = """
            package com.example.security;

            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                public void configure(HttpSecurity http) throws Exception {
                    http.headers()
                        .frameOptions()
                        .deny();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect frameOptions().deny()")
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("X-Frame-Options"))
        assertTrue(issues[0].message.contains("DENY"))
    }

    @Test
    fun `should detect direct addHeader with X-Frame-Options DENY`() {
        val code = """
            package com.example.security;

            import jakarta.servlet.http.HttpServletResponse;

            public class SecurityFilter {
                public void addSecurityHeaders(HttpServletResponse response) {
                    response.addHeader("X-Frame-Options", "DENY");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size, "Should detect direct addHeader with DENY")
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("X-Frame-Options"))
    }

    @Test
    fun `should detect setHeader with X-Frame-Options DENY`() {
        val code = """
            package com.example.security;

            import jakarta.servlet.http.HttpServletResponse;

            public class SecurityFilter {
                public void addSecurityHeaders(HttpServletResponse response) {
                    response.setHeader("X-Frame-Options", "DENY");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should handle case insensitive X-Frame-Options DENY`() {
        val code = """
            package com.example.security;

            public class SecurityFilter {
                public void test(HttpServletResponse response) {
                    response.addHeader("X-Frame-Options", "deny");
                    response.addHeader("X-Frame-Options", "Deny");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(2, issues.size, "Should detect both lowercase and mixed case DENY")
    }

    @Test
    fun `should allow frameOptions sameOrigin`() {
        val code = """
            package com.example.security;

            public class SecurityConfig {
                public void configure(HttpSecurity http) throws Exception {
                    http.headers()
                        .frameOptions()
                        .sameOrigin();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag SAMEORIGIN")
    }

    @Test
    fun `should allow frameOptions allowFrom`() {
        val code = """
            package com.example.security;

            public class SecurityConfig {
                public void configure(HttpSecurity http) throws Exception {
                    http.headers()
                        .frameOptions()
                        .allowFrom("https://trusted.example.com");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should allow allowFrom with trusted domain")
    }

    @Test
    fun `should allow X-Frame-Options SAMEORIGIN header`() {
        val code = """
            package com.example.security;

            public class SecurityFilter {
                public void addSecurityHeaders(HttpServletResponse response) {
                    response.addHeader("X-Frame-Options", "SAMEORIGIN");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag SAMEORIGIN")
    }

    @Test
    fun `should allow X-Frame-Options ALLOW-FROM header`() {
        val code = """
            package com.example.security;

            public class SecurityFilter {
                public void addSecurityHeaders(HttpServletResponse response) {
                    response.addHeader("X-Frame-Options", "ALLOW-FROM https://trusted.example.com");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should allow ALLOW-FROM")
    }

    @Test
    fun `should not flag other headers with value DENY`() {
        val code = """
            package com.example.security;

            public class SecurityFilter {
                public void addSecurityHeaders(HttpServletResponse response) {
                    response.addHeader("X-Permitted-Cross-Domain-Policies", "DENY");
                    response.addHeader("X-Content-Type-Options", "nosniff");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag other headers with DENY value")
    }

    @Test
    fun `should not flag X-Frame-Options with non-DENY values`() {
        val code = """
            package com.example.security;

            public class SecurityFilter {
                public void addSecurityHeaders(HttpServletResponse response) {
                    response.addHeader("X-Frame-Options", "");
                    response.addHeader("X-Frame-Options", "SAMEORIGIN");
                    response.addHeader("X-Frame-Options", "ALLOW-FROM https://example.com");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag non-DENY values")
    }

    @Test
    fun `should detect multiple X-Frame-Options DENY in same file`() {
        val code = """
            package com.example.security;

            public class SecurityConfig {
                public void configure1(HttpSecurity http1) throws Exception {
                    http1.headers().frameOptions().deny();
                }

                public void configure2(HttpSecurity http2) throws Exception {
                    http2.headers().frameOptions().deny();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(2, issues.size, "Should detect multiple deny calls")
    }

    @Test
    fun `should provide helpful description with examples`() {
        val code = """
            package com.example.security;

            public class SecurityConfig {
                public void configure(HttpSecurity http) throws Exception {
                    http.headers().frameOptions().deny();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        val issue = issues[0]
        assertTrue(issue.description.contains("Experience Manager"), "Should explain impact on Experience Manager")
        assertTrue(issue.description.contains("iframe"), "Should mention iframe")
        assertTrue(issue.description.contains("SAMEORIGIN"), "Should mention correct solution")
        assertTrue(issue.description.contains("Spring Security"), "Should include Spring Security example")
        assertTrue(issue.description.contains("blank page"), "Should explain user-facing issue")
    }

    @Test
    fun `should include metadata about configuration context`() {
        val code = """
            package com.example.security;

            public class SecurityConfig {
                public void configure(HttpSecurity http) throws Exception {
                    http.headers().frameOptions().deny();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("headerValue"))
        assertEquals("DENY", issues[0].metadata["headerValue"])
        assertTrue(issues[0].metadata.containsKey("context"))
    }

    @Test
    fun `should detect deny in chained method call`() {
        val code = """
            package com.example.security;

            public class SecurityConfig {
                protected void configure(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests()
                        .anyRequest().authenticated()
                        .and()
                        .headers()
                        .frameOptions()
                        .deny();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
    }

    @Test
    fun `should not flag deny in other contexts`() {
        val code = """
            package com.example;

            public class AccessControl {
                public void denyAccess(User user) {
                    user.setDenied(true);
                }

                public Response handleDeny() {
                    return Response.denied();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size, "Should not flag deny() in non-security contexts")
    }

    private fun runInspection(code: String): List<InspectionIssue> {
        val file = object : VirtualFile {
            override val path: Path = Path.of("/src/SecurityConfig.java")
            override val name: String = "SecurityConfig.java"
            override val extension: String = "java"
            override fun readText(): String = code
            override fun exists(): Boolean = true
            override fun size(): Long = code.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }

        val context = InspectionContext(
            projectRoot = Path.of("/project"),
            file = file,
            fileContent = code,
            language = FileType.JAVA,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }
}
