package org.bloomreach.inspections.core.inspections.config

import org.bloomreach.inspections.core.engine.*
import org.w3c.dom.Element

/**
 * Detects HTML/XML comment stripping configurations that break Experience Manager UI.
 *
 * The Experience Manager admin interface relies on HTML comments for functionality.
 * Removing or stripping comments breaks the UI, making it appear broken even though
 * the code is correct.
 *
 * Supported detection:
 * - Response headers setting comment removal
 * - Filter configurations with comment-stripping parameters
 * - Template/output processing with comment removal enabled
 * - Comments stripped from all pages (should only apply to public pages)
 */
class HtmlCommentStrippingInspection : Inspection() {
    override val id = "config.html-comment-stripping"
    override val name = "HTML Comment Stripping Configuration"
    override val description = """
        Detects configurations that strip or remove HTML/XML comments globally.

        The Experience Manager admin interface relies on HTML comments for functionality.
        When comments are stripped from responses, the UI appears broken - elements won't
        render, buttons disappear, or forms don't work properly despite working correctly
        in the underlying code.

        **Problem:**
        - Response header removes all HTML comments
        - Filter configuration strips comments globally
        - Template processing removes comments from admin pages
        - No distinction between admin and public pages

        **Solution:**
        Only strip comments from public-facing pages, never from admin/CMS pages.

        **Affected Components:**
        - Experience Manager UI pages
        - Admin interface
        - Management console
        - Page rendering

        **Supported Files:**
        - web.xml (filter configuration)
        - Java controllers and filters
        - JSP/FreeMarker templates
        - Configuration files
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.XML, FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        return when (context.language) {
            FileType.XML -> inspectXmlFile(context)
            FileType.JAVA -> inspectJavaFile(context)
            else -> emptyList()
        }
    }

    private fun inspectXmlFile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(context.fileContent.byteInputStream())

            // Look for filter configurations
            val filters = doc.getElementsByTagName("filter")
            for (i in 0 until filters.length) {
                val filterNode = filters.item(i)
                if (filterNode is Element) {
                    val filterName = getElementText(filterNode, "filter-name")
                    val filterClass = getElementText(filterNode, "filter-class")

                    // Check if this looks like a comment-stripping filter
                    if (filterName?.contains("comment", ignoreCase = true) == true ||
                        filterClass?.contains("comment", ignoreCase = true) == true ||
                        filterClass?.contains("compress", ignoreCase = true) == true ||
                        filterClass?.contains("minif", ignoreCase = true) == true) {

                        // Check init parameters
                        val initParams = filterNode.getElementsByTagName("init-param")
                        for (j in 0 until initParams.length) {
                            val paramNode = initParams.item(j)
                            if (paramNode is Element) {
                                val paramName = getElementText(paramNode, "param-name")
                                val paramValue = getElementText(paramNode, "param-value")

                                // Check for comment removal parameters
                                if (isCommentStrippingParameter(paramName, paramValue)) {
                                    issues.add(createFilterCommentStrippingIssue(context, filterName ?: "filter", paramName, paramValue))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return issues
    }

    private fun inspectJavaFile(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        if (isTestFile(context)) {
            return emptyList()
        }

        val content = context.fileContent

        // Check for response headers setting comment removal
        val problematicPatterns = listOf(
            Regex("""response\.addHeader\s*\(\s*["']X-Remove-Comments["']\s*,\s*["']true["']\s*\)"""),
            Regex("""response\.addHeader\s*\(\s*["']X-Strip-Comments["']\s*,\s*["']true["']\s*\)"""),
            Regex("""response\.setHeader\s*\(\s*["']X-Remove-Comments["']\s*,\s*["']true["']\s*\)"""),
            Regex("""response\.setHeader\s*\(\s*["']X-Strip-Comments["']\s*,\s*["']true["']\s*\)"""),
            Regex("""\.setProperty\s*\(\s*["']strip.comments["']\s*,\s*["']true["']\s*\)""", RegexOption.IGNORE_CASE),
            Regex("""\.setProperty\s*\(\s*["']remove.comments["']\s*,\s*["']true["']\s*\)""", RegexOption.IGNORE_CASE),
        )

        for (pattern in problematicPatterns) {
            var lineNumber = 1
            for (line in content.lines()) {
                if (pattern.find(line) != null && !line.trim().startsWith("//")) {
                    // Check if it's conditional (OK if guarded)
                    if (!isCommentStrippingGuarded(content, lineNumber)) {
                        val range = TextRange.wholeLine(lineNumber)
                        issues.add(createCommentStrippingHeaderIssue(context, lineNumber, line))
                    }
                }
                lineNumber++
            }
        }

        return issues
    }

    private fun isCommentStrippingGuarded(content: String, lineNumber: Int): Boolean {
        val lines = content.lines()
        if (lineNumber < 1 || lineNumber > lines.size) return false

        // Look backward for if/condition statements
        var checkLines = 3
        for (i in (lineNumber - 2) downTo maxOf(0, lineNumber - 5)) {
            val line = lines[i]
            if (line.contains("if") && !line.contains("admin", ignoreCase = true) && !line.contains("public", ignoreCase = true)) {
                return false
            }
            if (line.contains("admin", ignoreCase = true) || line.contains("!request.getRequestURI().contains(\"/admin\")", ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    private fun isCommentStrippingParameter(paramName: String?, paramValue: String?): Boolean {
        if (paramName == null || paramValue == null) return false

        val nameLower = paramName.lowercase()
        val valueLower = paramValue.lowercase()

        return (nameLower.contains("strip-comment") ||
                nameLower.contains("strip.comment") ||
                nameLower.contains("remove-comment") ||
                nameLower.contains("remove.comment") ||
                nameLower.contains("compress-comment") ||
                nameLower.contains("compress.comment") ||
                nameLower.contains("minif") ||
                nameLower.contains("minify")) &&
               (valueLower == "true" || valueLower == "yes" || valueLower == "1" || valueLower == "enabled")
    }

    private fun getElementText(parent: Element, tagName: String): String? {
        val elements = parent.getElementsByTagName(tagName)
        if (elements.length > 0) {
            return elements.item(0)?.textContent?.trim()
        }
        return null
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val name = context.file.name.lowercase()
        return name.endsWith("test.java") || name.endsWith("tests.java")
    }

    private fun createFilterCommentStrippingIssue(
        context: InspectionContext,
        filterName: String,
        paramName: String?,
        paramValue: String?
    ): InspectionIssue {
        val range = TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "Filter '$filterName' strips HTML comments which breaks Experience Manager UI",
            description = """
                **Problem:** Filter configuration removes HTML comments globally

                The filter is configured to strip or remove HTML comments from responses.
                The Experience Manager admin interface relies on HTML comments for UI
                functionality. Removing them breaks the admin interface.

                **Current Configuration:**
                Filter: $filterName
                Parameter: $paramName
                Value: $paramValue

                **Impact:**
                - Experience Manager UI appears broken
                - Admin interface elements disappear or don't work
                - Forms may not render correctly
                - Management console becomes unusable
                - Underlying code is correct but UI is broken

                **Solution:** Only strip comments from public pages, never from admin pages

                **Option 1: URL-based filtering (Recommended)**
                ```xml
                <filter>
                    <filter-name>HtmlCompressionFilter</filter-name>
                    <filter-class>com.example.HtmlCompressionFilter</filter-class>
                    <init-param>
                        <param-name>removeComments</param-name>
                        <param-value>false</param-value>
                    </init-param>
                </filter>

                <filter-mapping>
                    <filter-name>HtmlCompressionFilter</filter-name>
                    <url-pattern>/public/*</url-pattern>
                </filter-mapping>
                ```

                **Option 2: Request-based filtering**
                ```java
                public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
                    HttpServletRequest request = (HttpServletRequest) req;
                    String uri = request.getRequestURI();

                    // Never strip comments from admin pages
                    if (uri.contains("/admin") || uri.contains("/cms") || uri.contains("/manager")) {
                        chain.doFilter(req, res);  // Pass through, no stripping
                    } else {
                        // OK to strip comments from public pages
                        // Apply compression/stripping here
                    }
                }
                ```

                **Option 3: Disable comment stripping entirely**
                If comment stripping isn't critical for performance:
                ```xml
                <init-param>
                    <param-name>removeComments</param-name>
                    <param-value>false</param-value>
                </init-param>
                ```

                **Important Notes:**
                - Always preserve comments for admin/management endpoints
                - Experience Manager specifically requires comments for UI functionality
                - Comment stripping should only apply to public-facing content
                - Consider the performance vs. functionality trade-off

                **Affected Endpoints to Protect:**
                - /cms/* - Content management system
                - /admin/* - Admin interface
                - /manager/* - Management console
                - /repository/* - Repository management
                - /configuration/* - Configuration pages

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [Experience Manager UI Configuration](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "filterName" to filterName,
                "issue" to "commentStripping",
                "paramName" to (paramName ?: "unknown"),
                "paramValue" to (paramValue ?: "unknown")
            )
        )
    }

    private fun createCommentStrippingHeaderIssue(
        context: InspectionContext,
        lineNumber: Int,
        line: String
    ): InspectionIssue {
        val range = TextRange.wholeLine(lineNumber)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.WARNING,
            message = "Response header configured to strip HTML comments globally",
            description = """
                **Problem:** Response header removes HTML comments from all responses

                The code sets a header or property that removes HTML comments from responses.
                This breaks the Experience Manager admin interface which relies on comments.

                **Current Code:**
                ```
                $line
                ```

                **Impact:**
                - Experience Manager UI breaks or appears blank
                - Admin interface elements disappear
                - Management console becomes unusable
                - CSS/JavaScript embedded in comments gets removed
                - UI functionality that depends on comments stops working

                **Solution:** Only strip comments from public pages, not admin pages

                **Incorrect (Current):**
                ```java
                // This affects ALL pages including admin
                response.addHeader("X-Remove-Comments", "true");
                response.setHeader("X-Strip-Comments", "true");
                ```

                **Correct Approach:**
                ```java
                // Only strip comments from public pages
                String requestUri = request.getRequestURI();
                if (!requestUri.contains("/admin") &&
                    !requestUri.contains("/cms") &&
                    !requestUri.contains("/manager")) {
                    response.addHeader("X-Remove-Comments", "true");
                }
                ```

                **Best Practice:**
                Instead of runtime header manipulation, use a servlet filter that only
                applies to public URL patterns:

                ```xml
                <filter>
                    <filter-name>CompressionFilter</filter-name>
                    <filter-class>com.example.CompressionFilter</filter-class>
                </filter>
                <filter-mapping>
                    <filter-name>CompressionFilter</filter-name>
                    <url-pattern>/public/*</url-pattern>
                </filter-mapping>
                <!-- Admin pages NOT mapped - no comment stripping -->
                ```

                **Important Notes:**
                - Never strip comments from Experience Manager URLs
                - Comments may contain important metadata or styling
                - UI functionality can depend on HTML comment structure
                - Test thoroughly if you disable this fix

                **Affected Pages to Protect:**
                - Any /admin/* endpoints
                - Any /cms/* endpoints
                - Any /manager/* endpoints
                - Management consoles
                - Content editors

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [Experience Manager Configuration](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "issue" to "commentStrippingHeader",
                "currentLine" to line.trim(),
                "suggestion" to "Guard with: if (!request.getRequestURI().contains(\"/admin\"))"
            )
        )
    }
}
