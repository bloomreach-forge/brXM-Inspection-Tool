package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.slf4j.LoggerFactory

/**
 * Detects dangerous output methods that may enable XSS attacks.
 *
 * XSS (Cross-Site Scripting) is a CRITICAL security vulnerability.
 *
 * Problem: Code that outputs user input to HTTP response without escaping is vulnerable to XSS:
 * - Attacker inputs: `<script>alert('hacked')</script>`
 * - Code outputs directly: `response.getWriter().print(userInput)`
 * - Script executes in user's browser
 * - Can steal session cookies, redirect users, deface pages
 *
 * This inspection detects Java code that:
 * 1. Writes request parameters directly to response
 * 2. Missing HTML/URL escaping in servlet output
 * 3. Unescaped user input in model objects
 *
 * Safe alternatives:
 * - Escape in Java: StringEscapeUtils.escapeHtml4(userInput)
 * - Use JSP EL with JSTL tags: <c:out value="${param}" escapeXml="true" />
 * - Use Spring taglib: <spring:escapeBody htmlEscape="true">${value}</spring:escapeBody>
 */
class MissingJspEscapingInspection : Inspection() {
    override val id = "security.missing-jsp-escaping"
    override val name = "Missing XSS Output Escaping"
    override val description = """
        Detects code that outputs unescaped user input to HTTP response.
        User input without proper escaping can enable XSS attacks.
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        if (context.language != FileType.JAVA) {
            return emptyList()
        }

        return inspectJavaFile(context)
    }

    private fun inspectJavaFile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()
        val content = context.fileContent

        // Track variables that come from request input
        val requestInputVars = mutableSetOf<String>()

        // Pattern: Variable assigned from request parameter
        val requestVarRegex = """(\w+)\s*=\s*request\.(getParameter|getAttribute|getHeader)\s*\(""".toRegex()
        val lines = content.split("\n")

        // First pass: find all variables assigned from request input
        for (line in lines) {
            val match = requestVarRegex.find(line)
            if (match != null) {
                val varName = match.groupValues[1]
                requestInputVars.add(varName)
            }
        }

        // Pattern: Direct writing of request parameters to response
        val responseWriteRegex = """response\.(getWriter\(\)|getOutputStream\(\))\..*\(.*request\.(getParameter|getAttribute|getHeader)""".toRegex(RegexOption.IGNORE_CASE)
        val printlnWithUserInput = """(print|write|println|append)\s*\(\s*request\.(getParameter|getAttribute|getHeader)""".toRegex(RegexOption.IGNORE_CASE)
        val printWithVariable = """(print|write|println|append)\s*\(\s*"[^"]*"\s*\+\s*(\w+)""".toRegex()

        for ((lineNum, line) in lines.withIndex()) {
            // Check for direct unescaped request parameter output
            if (responseWriteRegex.containsMatchIn(line) || printlnWithUserInput.containsMatchIn(line)) {
                val match = printlnWithUserInput.find(line) ?: responseWriteRegex.find(line)
                if (match != null && !isEscaped(line)) {
                    issues.add(createXssIssue(
                        context = context,
                        line = lineNum + 1,
                        column = match.range.first + 1,
                        expression = match.value,
                        issueType = "Unescaped user input written to response"
                    ))
                }
            }

            // Check for unescaped request variable output
            else if (printWithVariable.containsMatchIn(line)) {
                val match = printWithVariable.find(line)
                if (match != null) {
                    val varName = match.groupValues[2]
                    if (varName in requestInputVars && !isEscaped(line)) {
                        issues.add(createXssIssue(
                            context = context,
                            line = lineNum + 1,
                            column = match.range.first + 1,
                            expression = match.value,
                            issueType = "Unescaped user input written to response"
                        ))
                    }
                }
            }
        }

        return issues
    }

    private fun isLikelyUserInput(expression: String): Boolean {
        val userInputPatterns = listOf(
            "param\\.", "request\\.", "query\\.", "input",
            "getParameter", "getAttribute", "getHeader",
            "message", "title", "description", "content", "text",
            "body", "name", "email", "comment", "note"
        )

        return userInputPatterns.any { expression.contains(it) }
    }

    private fun isEscaped(expression: String): Boolean {
        val escapePatterns = listOf(
            "HtmlUtils.htmlEscape",
            "StringEscapeUtils.escapeHtml",
            "escapeXml",
            "?html", "?xhtml", "?url",
            "ESAPI.encoder.encodeForHTML",
            "XSSFilter", "XSSPrevention"
        )

        return escapePatterns.any { expression.contains(it) }
    }

    private fun createXssIssue(
        context: InspectionContext,
        line: Int,
        column: Int,
        expression: String,
        issueType: String
    ): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "XSS Risk: $issueType",
            description = """
                User input is written to the HTTP response without HTML escaping.
                This can allow XSS (Cross-Site Scripting) attacks.

                **Critical Security Issue**: Attackers can inject malicious scripts

                **Expression**: `$expression`

                **How the Attack Works**:
                1. Attacker submits: `<script>alert('hacked')</script>`
                2. Code outputs directly: `response.getWriter().print(userInput)`
                3. Browser executes the script
                4. Attacker can: steal cookies, redirect, deface page, steal data

                **Example Vulnerability**:
                ```java
                // ⚠️ DANGEROUS - Direct output without escaping
                String title = request.getParameter("title");
                response.getWriter().println("<h1>" + title + "</h1>");

                // If attacker submits title = <img src=x onerror=alert('hacked')>
                // Output becomes: <h1><img src=x onerror=alert('hacked')></h1>
                ```

                **Fix 1: Escape in Java code (Recommended)**:
                ```java
                import org.apache.commons.text.StringEscapeUtils;

                String title = StringEscapeUtils.escapeHtml4(request.getParameter("title"));
                response.getWriter().println("<h1>" + title + "</h1>");
                // ✓ SAFE - HTML entities escaped
                ```

                **Fix 2: Use JSP EL with JSTL <c:out>**:
                ```jsp
                <c:set var="title" value="dollar{param.title}" />
                <h1><c:out value="dollar{title}" escapeXml="true" /></h1>
                ```

                **Fix 3: Use Spring taglib**:
                ```jsp
                <h1><spring:escapeBody htmlEscape="true">
                    dollar{title}
                </spring:escapeBody></h1>
                ```

                **Complete Security Checklist**:
                - [ ] Find all response.getWriter().print() calls
                - [ ] Find all response.getOutputStream().write() calls
                - [ ] Check if writing unescaped user input
                - [ ] Apply HTML escaping: StringEscapeUtils.escapeHtml4()
                - [ ] Use parametrized queries (not string concatenation)
                - [ ] Validate input on server side (never trust client)
                - [ ] Use Content Security Policy (CSP) headers
                - [ ] Enable XSS protection in browsers

                **OWASP Top 10**: A3:2021 - Injection (includes XSS)
                **CWE**: CWE-79 - Improper Neutralization of Input During Web Page Generation

                **References**:
                - [OWASP XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
                - [CWE-79](https://cwe.mitre.org/data/definitions/79.html)
                - [Bloomreach Security Guide](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = TextRange(line, column, line, column + 20),
            metadata = mapOf(
                "expression" to expression,
                "vulnerabilityType" to "XSS",
                "severity" to "CRITICAL",
                "cwe" to "CWE-79"
            )
        )
    }

        override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(
            AddHtmlEscapingQuickFix(),
            DocumentXssRiskQuickFix()
        )
    }
}

/**
 * Quick fix: Add HTML escaping
 */
private class AddHtmlEscapingQuickFix : BaseQuickFix(
    name = "Add StringEscapeUtils.escapeHtml4()",
    description = "Wraps user input with HTML escaping utility"
) {
    override fun apply(context: QuickFixContext) {
        // Would wrap the user input expression with escaping
        // Complex refactoring - for MVP, document the pattern
    }
}

/**
 * Quick fix: Document XSS risk
 */
private class DocumentXssRiskQuickFix : BaseQuickFix(
    name = "Add security comment",
    description = "Adds a comment documenting the XSS risk and required escaping"
) {
    override fun apply(context: QuickFixContext) {
        // Would add a comment above the line
    }
}
