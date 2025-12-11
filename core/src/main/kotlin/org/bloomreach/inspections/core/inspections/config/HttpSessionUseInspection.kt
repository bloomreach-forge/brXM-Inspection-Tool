package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects usage of HttpSession.setAttribute() in HST components.
 *
 * In HST applications, you should use HstRequest.setAttribute() instead (10% of config issues).
 *
 * Common mistakes:
 * - Using HttpSession for request-scoped data
 * - Storing large objects in session
 * - Not cleaning up session attributes
 */
class HttpSessionUseInspection : Inspection() {
    override val id = "config.http-session-use"
    override val name = "HttpSession Usage in HST"
    override val description = """
        Detects usage of javax.servlet.http.HttpSession.setAttribute().
        In HST, use HstRequest.setAttribute() for request-scoped data instead.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Skip test files
        if (isTestFile(context)) {
            return emptyList()
        }

        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = HttpSessionVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") ||
               path.contains("\\test\\") ||
               path.endsWith("test.java") ||
               path.endsWith("test.kt")
    }
}

/**
 * Visitor for detecting HttpSession usage
 */
private class HttpSessionVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(methodCall: MethodCallExpr, ctx: InspectionContext) {
        super.visit(methodCall, ctx)

        val methodName = methodCall.nameAsString

        // Check for HttpSession.setAttribute() or getAttribute()
        if (methodName !in setOf("setAttribute", "getAttribute", "removeAttribute", "invalidate")) {
            return
        }

        // Check if the scope looks like HttpSession
        val scope = methodCall.scope.orElse(null)?.toString() ?: return

        // Detect HttpSession usage patterns
        val isHttpSession = scope.contains("HttpSession") ||
                           scope.contains("getSession()") ||
                           scope == "session" ||
                           scope.contains("request.getSession") ||
                           scope.contains("httpServletRequest.getSession")

        if (!isHttpSession) {
            return
        }

        // Flag the usage
        issues.add(createHttpSessionIssue(methodCall, methodName))
    }

    private fun createHttpSessionIssue(
        methodCall: MethodCallExpr,
        methodName: String
    ): InspectionIssue {
        val range = methodCall.name.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "HttpSession.$methodName() should not be used in HST components",
            description = """
                HttpSession usage in HST applications is an anti-pattern.
                Use HstRequest attributes for request-scoped data instead.

                **Problem**: Code uses HttpSession.$methodName()

                **Why This Is Wrong**:
                - HttpSession stores data server-side for the entire session
                - HST provides better mechanisms for data scoping
                - Sessions consume memory and don't scale horizontally
                - Session data persists longer than needed

                **Fix: Use HstRequest Attributes**
                ```java
                // L WRONG - HttpSession
                HttpSession session = request.getSession();
                session.setAttribute("products", products);

                //  CORRECT - HstRequest
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    List<Product> products = fetchProducts();
                    request.setAttribute("products", products);
                    // Data available only for this request
                }
                ```

                **When to Use Each**:

                **HstRequest.setAttribute()** - Request-scoped data:
                - Data needed only for rendering one page
                - Component-to-template communication
                - Query results, content beans
                - Most component data should go here

                **HstRequest.getModel()** - Model attributes:
                - Shared data across components in same request
                - Better alternative to request attributes
                - Type-safe access

                **HttpSession** - RARELY, only for:
                - User login state (but use security framework)
                - Shopping cart (but consider database)
                - Multi-step wizards (but consider URL parameters)

                **Real-World Problems**:
                ```java
                // Memory leak - session never cleaned up
                session.setAttribute("largeObject", someHugeList);

                // Doesn't work in cluster without sticky sessions
                session.setAttribute("data", data);

                // Data lives too long - stale data served
                session.setAttribute("products", getProducts());
                // User sees old products on next page
                ```

                **Better Patterns**:

                **Pattern 1: Request Attributes (Most Common)**
                ```java
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    String searchTerm = request.getParameter("q");
                    List<Result> results = search(searchTerm);
                    request.setAttribute("results", results); // 
                }
                ```

                **Pattern 2: Component Model**
                ```java
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    HstRequestContext reqContext = request.getRequestContext();
                    reqContext.setAttribute("sharedData", data); // 
                }
                ```

                **Pattern 3: URL Parameters (For Navigation State)**
                ```java
                // Instead of session for pagination:
                String url = "/products?page=" + pageNum + "&size=" + pageSize;
                response.sendRedirect(url); //  Stateless
                ```

                **When Session IS Needed (Rare)**:
                ```java
                // User authentication state
                if (user.isLoggedIn()) {
                    request.getSession().setAttribute("userId", user.getId());
                    // But better to use Spring Security or similar
                }
                ```

                **Performance Impact**:
                - Request attributes: Fast, no serialization, GC'd immediately
                - Session attributes: Slow, serialized, persisted, memory overhead

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/request-processing/
            """.trimIndent(),
            range = range
        )
    }
}
