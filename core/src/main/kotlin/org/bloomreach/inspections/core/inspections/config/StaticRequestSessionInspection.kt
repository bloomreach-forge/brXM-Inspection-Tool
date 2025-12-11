package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects request/session objects stored in static fields.
 *
 * CRITICAL CONCURRENCY BUG: Threading issue causing request data leakage.
 *
 * Problem: Static fields are shared across ALL requests/users.
 * - Request A stores its data in static field
 * - Request B overwrites the same static field
 * - Request A reads Request B's data (data leakage!)
 * - This happens randomly, making it VERY hard to debug
 *
 * Impact:
 * - Data corruption
 * - User data leakage (critical security issue)
 * - Non-deterministic bugs (race conditions)
 * - Session hijacking
 * - Private data exposure
 *
 * Example Problem:
 * ```java
 * public class UserController extends BaseHstComponent {
 *     private static HttpServletRequest currentRequest; // ⚠️ DEADLY
 *
 *     public void doBeforeRender(HstRequest request) {
 *         currentRequest = request.getHttpServletRequest(); // Thread A stores request
 *         // ...
 *     }
 *
 *     // Thread B comes in, overwrites currentRequest
 *     // Thread A reads request from Thread B! 💥
 * }
 * ```
 *
 * This inspection detects:
 * 1. Static HttpServletRequest/Response fields
 * 2. Static JCR Session fields
 * 3. Static HST Request/Response fields
 * 4. Static PageContext fields
 * 5. Any request-scoped object stored statically
 *
 * Safe pattern: Use ThreadLocal or proper injection
 */
class StaticRequestSessionInspection : Inspection() {
    override val id = "config.static-request-session"
    override val name = "Static Request/Session Storage (Concurrency Bug)"
    override val description = """
        Detects request/session objects stored in static fields.
        This causes data leakage between concurrent requests.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = StaticSessionVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(
            UseThreadLocalQuickFix(),
            UseInjectAnnotationQuickFix(),
            UseLocalVariableQuickFix()
        )
    }
}

/**
 * Visitor that detects static request/session fields
 */
private class StaticSessionVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    // Types that should never be static
    private val dangerousTypes = setOf(
        // Java Servlet
        "HttpServletRequest", "ServletRequest", "HttpServletResponse", "ServletResponse",
        "HttpSession", "ServletContext", "PageContext",
        // JCR
        "Session", "Node", "Property",
        // Hippo/HST
        "HstRequest", "HstResponse", "HstRequestContext", "PageContext",
        "RequestContext", "ComponentContext",
        // Spring
        "HttpMessageConverter",
        // Commons
        "Request", "Response"
    )

    private val requestScopePatterns = listOf(
        "Request", "Response", "Session", "PageContext"
    )

    private val applicationScopeTypes = setOf(
        "ServletContext"
    )

    override fun visit(field: FieldDeclaration, ctx: InspectionContext) {
        super.visit(field, ctx)

        // Check if field is static
        if (!field.isStatic) {
            return
        }

        // Check if field type is request-scoped
        val fieldType = field.elementType.asString()

        if (isRequestScopedType(fieldType)) {
            for (variable in field.variables) {
                issues.add(createStaticSessionIssue(field, variable.nameAsString, fieldType))
            }
        }
    }

    private fun isRequestScopedType(typeName: String): Boolean {
        // Check if it's application-scoped (safe to store statically)
        if (typeName in applicationScopeTypes) {
            return false
        }

        // Check exact matches
        if (typeName in dangerousTypes) {
            return true
        }

        // Check contains patterns (e.g., "MyCustomRequest")
        for (pattern in requestScopePatterns) {
            if (typeName.contains(pattern, ignoreCase = true) &&
                !typeName.contains("ThreadLocal", ignoreCase = true) &&
                !typeName.contains("Static", ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    private fun createStaticSessionIssue(
        field: FieldDeclaration,
        variableName: String,
        fieldType: String
    ): InspectionIssue {
        val range = field.range.map { r ->
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
            severity = inspection.severity,
            message = "Static field storing request-scoped object '$variableName' of type '$fieldType'",
            description = """
                A request/session-scoped object is stored in a static field.
                This is a CRITICAL concurrency bug causing data leakage between requests.

                **Critical Impact**: Data leakage, security vulnerability, race conditions

                **Problem**: Static fields are shared across ALL requests:
                - Thread A (Request 1) stores data in static field
                - Thread B (Request 2) overwrites the same field
                - Thread A reads Request 2's data (data leakage!)
                - Non-deterministic bugs (intermittent issues)

                **Example Problem**:
                ```java
                public class UserController {
                    private static HttpServletRequest request;  // ⚠️ DEADLY
                    private static JCR.Session session;         // ⚠️ DEADLY

                    public void doBeforeRender(HstRequest hstReq) {
                        request = hstReq.getHttpServletRequest(); // Thread A stores
                        // ...
                        String userId = request.getParameter("userId"); // Thread B's data!
                    }
                }
                ```

                **Consequences**:
                1. **Data Leakage**: User A sees User B's data
                2. **Security Breach**: Accessing another user's profile
                3. **Hard to Debug**: Only occurs under load/concurrency
                4. **Race Conditions**: Intermittent failures
                5. **Session Hijacking**: Attackers can manipulate shared session

                **Solution 1: Use ThreadLocal (Recommended)**:
                ```java
                public class UserController {
                    private static final ThreadLocal<HttpServletRequest> requestHolder =
                        new ThreadLocal<>();

                    public void doBeforeRender(HstRequest hstReq) {
                        // Each thread has its own storage
                        requestHolder.set(hstReq.getHttpServletRequest());
                        // ...
                    }

                    private HttpServletRequest getCurrentRequest() {
                        return requestHolder.get();
                    }

                    // IMPORTANT: Clean up in finally block!
                    public void cleanup() {
                        requestHolder.remove(); // Prevent memory leaks
                    }
                }
                ```

                **Solution 2: Use Constructor Injection (Spring)**:
                ```java
                @Component
                public class UserController {
                    private final HttpServletRequest request;

                    // Spring injects request-scoped proxy
                    public UserController(HttpServletRequest request) {
                        this.request = request; // Each request gets own instance
                    }
                }
                ```

                **Solution 3: Use HST RequestContext (Hippo)**:
                ```java
                public class MyComponent extends BaseHstComponent {
                    public void doBeforeRender(HstRequest request) {
                        // Get from threadLocal via RequestContextProvider
                        HstRequestContext ctx = RequestContextProvider.get();
                        HttpServletRequest servletRequest = ctx.getServletRequest();
                    }
                }
                ```

                **Solution 4: Use Local Variables (Best)**:
                ```java
                public class UserService {
                    // Pass as parameters instead of storing
                    public void processRequest(HttpServletRequest request) {
                        String userId = request.getParameter("userId");
                        // Process with local variable
                    }
                }
                ```

                **Testing to Verify the Bug**:
                1. Load test with multiple concurrent users
                2. Check logs for cross-user data access
                3. Use JProfiler/YourKit to inspect static field values
                4. Look for intermittent "wrong user" errors

                **How to Fix Existing Code**:
                1. [ ] Identify all static request/session fields
                2. [ ] Remove static modifier OR
                3. [ ] Wrap in ThreadLocal<> OR
                4. [ ] Use dependency injection (preferred)
                5. [ ] Add cleanup code to prevent memory leaks
                6. [ ] Load test to verify fix
                7. [ ] Add code review rule to prevent recurrence

                **Prevention**:
                - Enable static analysis in CI/CD
                - Code review requirement: "Is this really static?"
                - Use SonarQube rule to block this pattern
                - Use Spring/CDI dependency injection instead

                **Related CMS Issues**:
                - HST components sharing request data
                - Repository sessions leaking between requests
                - User data visible to other users (security)

                **OWASP**: A1:2021 - Broken Access Control
                **CWE**: CWE-362 - Concurrent Execution using Shared Resource

                **References**:
                - [ThreadLocal API](https://docs.oracle.com/javase/8/docs/api/java/lang/ThreadLocal.html)
                - [Spring Request Scoped Beans](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans-factory-scopes-request)
                - [Concurrency Issues in Web Apps](https://www.owasp.org/index.php/Java_Concurrency)
                - [Bloomreach HST Best Practices](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "fieldName" to variableName,
                "fieldType" to fieldType,
                "severity" to "CRITICAL",
                "bugType" to "concurrency",
                "cwe" to "CWE-362"
            )
        )
    }
}

/**
 * Quick fix: Convert to ThreadLocal
 */
private class UseThreadLocalQuickFix : BaseQuickFix(
    name = "Convert to ThreadLocal",
    description = "Wraps static field in ThreadLocal for thread safety"
) {
    override fun apply(context: QuickFixContext) {
        // Complex refactoring - need to:
        // 1. Add ThreadLocal declaration
        // 2. Update all accesses
        // 3. Add cleanup code
        // For MVP, just document the pattern
    }
}

/**
 * Quick fix: Use @Inject annotation
 */
private class UseInjectAnnotationQuickFix : BaseQuickFix(
    name = "Use @Inject instead of static",
    description = "Converts to Spring/CDI dependency injection"
) {
    override fun apply(context: QuickFixContext) {
        // Would require understanding the class structure
        // and adding proper injection pattern
    }
}

/**
 * Quick fix: Use local variable
 */
private class UseLocalVariableQuickFix : BaseQuickFix(
    name = "Use local variable instead",
    description = "Passes as parameter instead of storing in static field"
) {
    override fun apply(context: QuickFixContext) {
        // Would require refactoring method signatures
    }
}
