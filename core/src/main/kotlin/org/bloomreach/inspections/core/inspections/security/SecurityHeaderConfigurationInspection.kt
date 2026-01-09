package org.bloomreach.inspections.core.inspections.security

import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects incorrect X-Frame-Options security header configuration.
 *
 * The Experience Manager requires specific X-Frame-Options header values to function.
 * Setting it to DENY prevents the Experience Manager from loading in an iframe,
 * resulting in a completely blank page despite successful authentication.
 *
 * This inspection detects:
 * - Spring Security .frameOptions().deny() calls
 * - Direct header assignments with value "DENY"
 *
 * Allowed values:
 * - SAMEORIGIN: Allow framing from same origin (recommended for Experience Manager)
 * - ALLOW-FROM: Allow specific trusted domains
 * - Absence of header: Allow all framing (less secure but functional)
 */
class SecurityHeaderConfigurationInspection : Inspection() {
    override val id = "security.security-header-configuration"
    override val name = "X-Frame-Options Security Header Misconfiguration"
    override val description = """
        Detects X-Frame-Options header set to DENY, which breaks Experience Manager UI.

        The Experience Manager relies on being able to load within an iframe. When the
        X-Frame-Options header is set to DENY, the Experience Manager cannot load,
        resulting in a completely blank page.

        This is a critical configuration error that makes the Experience Manager unusable.

        **Incorrect Configuration:**
        - X-Frame-Options: DENY (blocks all framing)
        - Spring Security: .frameOptions().deny()

        **Correct Configuration:**
        - X-Frame-Options: SAMEORIGIN (allow same-origin framing)
        - Spring Security: .frameOptions().sameOrigin()
        - Or absent: Allow all framing
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        if (context.language != FileType.JAVA) {
            return emptyList()
        }

        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = SecurityHeaderVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }
}

private class SecurityHeaderVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(methodCall: MethodCallExpr, ctx: InspectionContext) {
        super.visit(methodCall, ctx)

        // Check for Spring Security pattern: .frameOptions().deny()
        if (isFrameOptionsDenyCall(methodCall)) {
            issues.add(createSecurityHeaderIssue(methodCall, "Spring Security", ".frameOptions().deny()"))
        }

        // Check for direct header setting: addHeader("X-Frame-Options", "DENY")
        if (isDirectHeaderDenyCall(methodCall)) {
            issues.add(createSecurityHeaderIssue(methodCall, "Direct Header", "X-Frame-Options: DENY"))
        }
    }

    private fun isFrameOptionsDenyCall(methodCall: MethodCallExpr): Boolean {
        // Look for pattern: .deny() where parent is frameOptions()
        if (methodCall.nameAsString != "deny") {
            return false
        }

        val scope = methodCall.scope.orElse(null) as? MethodCallExpr ?: return false
        if (scope.nameAsString != "frameOptions") {
            return false
        }

        // Make sure this is in a security context (not just any deny() call)
        return isInSecurityContext(methodCall)
    }

    private fun isDirectHeaderDenyCall(methodCall: MethodCallExpr): Boolean {
        // Look for pattern: .addHeader("X-Frame-Options", "DENY")
        if (methodCall.nameAsString != "addHeader" && methodCall.nameAsString != "setHeader") {
            return false
        }

        val args = methodCall.arguments
        if (args.size < 2) {
            return false
        }

        val firstArg = args[0]
        val secondArg = args[1]

        // Check first argument is "X-Frame-Options"
        if (firstArg !is StringLiteralExpr) {
            return false
        }

        val headerName = firstArg.value
        if (!headerName.equals("X-Frame-Options", ignoreCase = true)) {
            return false
        }

        // Check second argument is "DENY"
        if (secondArg !is StringLiteralExpr) {
            return false
        }

        val headerValue = secondArg.value
        return headerValue.equals("DENY", ignoreCase = true)
    }

    private fun isInSecurityContext(methodCall: MethodCallExpr): Boolean {
        try {
            val methodStr = methodCall.toString()
            return methodStr.contains("frameOptions") || methodStr.contains("headers()")
        } catch (e: Exception) {
            return false
        }
    }

    private fun createSecurityHeaderIssue(
        methodCall: MethodCallExpr,
        context: String,
        pattern: String
    ): InspectionIssue {
        val range = methodCall.range.map { r ->
            TextRange(r.begin.line, r.begin.column, r.end.line, r.end.column)
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = this@SecurityHeaderVisitor.context.file,
            severity = Severity.ERROR,
            message = "X-Frame-Options set to DENY - Experience Manager requires framing",
            description = """
                **Problem:** X-Frame-Options header is set to DENY

                The Experience Manager requires the ability to load within an iframe for the
                content editing interface to function. Setting X-Frame-Options to DENY prevents
                this, resulting in a completely blank page.

                **Current Configuration:** $context
                Pattern: $pattern

                **Impact:**
                - Experience Manager displays blank page
                - Content editing interface is unusable
                - Users cannot manage content despite successful authentication
                - No error messages displayed to guide troubleshooting

                **Solution:** Change X-Frame-Options to a permissive value

                **For Spring Security:**

                FROM:
                ```java
                @Configuration
                public class SecurityConfig {
                    @Bean
                    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                        http.headers()
                            .frameOptions().deny();  // ❌ WRONG
                        return http.build();
                    }
                }
                ```

                TO:
                ```java
                @Configuration
                public class SecurityConfig {
                    @Bean
                    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                        http.headers()
                            .frameOptions().sameOrigin();  // ✅ CORRECT
                        return http.build();
                    }
                }
                ```

                **For Direct Header Setting:**

                FROM:
                ```java
                response.addHeader("X-Frame-Options", "DENY");  // ❌ WRONG
                ```

                TO:
                ```java
                response.addHeader("X-Frame-Options", "SAMEORIGIN");  // ✅ CORRECT
                ```

                **Acceptable Header Values:**
                - **SAMEORIGIN** (recommended): Allow framing only from same-origin requests
                  - Secure for Experience Manager
                  - Prevents clickjacking from other domains
                - **ALLOW-FROM https://trusted-domain.com**: Allow specific trusted domains
                  - Allows framing from specific domain
                  - Supported in older browsers only
                - **Absent**: Allow framing from any origin
                  - Makes Experience Manager functional
                  - Less secure but acceptable for internal systems

                **Why This Matters:**

                The Experience Manager uses nested iframes for the content editing interface.
                The X-Frame-Options header is a security header that controls whether a page
                can be loaded in an iframe. When set to DENY:
                1. Browser prevents the page from loading in iframe
                2. Iframe content appears blank
                3. No JavaScript errors logged
                4. Appears to be a network/authentication issue to users

                **References:**
                - [Channel Manager Troubleshooting - Blank Pages](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [MDN: X-Frame-Options Header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Frame-Options)
                - [OWASP: Clickjacking Defense](https://owasp.org/www-community/attacks/Clickjacking)
                - [Spring Security Documentation](https://spring.io/projects/spring-security)
            """.trimIndent(),
            range = range,
            metadata = mapOf("headerValue" to "DENY", "context" to context)
        )
    }
}
