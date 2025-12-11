package org.bloomreach.inspections.core.inspections.security

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects REST endpoints without proper authentication.
 *
 * From community analysis: 20% of integration issues involve authentication problems.
 *
 * Common issues:
 * - JAX-RS endpoints without @RolesAllowed or authentication checks
 * - Public endpoints that should be protected
 * - Missing CORS configuration
 * - Inconsistent authentication across endpoints
 *
 * Best practice: All REST endpoints should have explicit authentication/authorization.
 */
class RestAuthenticationInspection : Inspection() {
    override val id = "security.rest-authentication"
    override val name = "Missing REST Authentication"
    override val description = """
        Detects REST endpoints that may lack proper authentication.

        All REST endpoints should have explicit authentication unless intentionally public.

        This inspection checks for:
        - JAX-RS methods without @RolesAllowed or similar annotations
        - Spring REST controllers without security annotations
        - Missing authentication checks in endpoint methods
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    // JAX-RS annotations
    private val restMethodAnnotations = setOf(
        "GET", "POST", "PUT", "DELETE", "PATCH",
        "Path", "RequestMapping", "GetMapping",
        "PostMapping", "PutMapping", "DeleteMapping"
    )

    // Security annotations
    private val securityAnnotations = setOf(
        "RolesAllowed", "PermitAll", "DenyAll",
        "Secured", "PreAuthorize", "PostAuthorize"
    )

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Skip security inspections for test files (they often test authentication)
        if (isTestFile(context)) {
            return emptyList()
        }

        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = RestEndpointVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") ||
               path.contains("\\test\\") ||
               path.endsWith("test.java") ||
               path.endsWith("test.kt") ||
               path.endsWith("tests.java") ||
               path.endsWith("tests.kt")
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(
            AddRolesAllowedQuickFix(),
            AddPermitAllQuickFix()
        )
    }
}

/**
 * Visitor to detect REST endpoints without authentication
 */
private class RestEndpointVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    private var currentClassHasSecurity = false
    private val restMethodAnnotations = setOf(
        "GET", "POST", "PUT", "DELETE", "PATCH",
        "Path", "RequestMapping", "GetMapping",
        "PostMapping", "PutMapping", "DeleteMapping"
    )
    private val securityAnnotations = setOf(
        "RolesAllowed", "PermitAll", "DenyAll",
        "Secured", "PreAuthorize", "PostAuthorize"
    )

    override fun visit(classDecl: ClassOrInterfaceDeclaration, ctx: InspectionContext) {
        // Check if class has class-level security
        currentClassHasSecurity = classDecl.annotations.any { anno ->
            securityAnnotations.contains(anno.nameAsString)
        }

        super.visit(classDecl, ctx)
    }

    override fun visit(method: MethodDeclaration, ctx: InspectionContext) {
        super.visit(method, ctx)

        // Check if this is a REST endpoint
        val isRestEndpoint = method.annotations.any { anno ->
            restMethodAnnotations.contains(anno.nameAsString)
        }

        if (!isRestEndpoint) {
            return
        }

        // Check if method has security annotation
        val hasMethodSecurity = method.annotations.any { anno ->
            securityAnnotations.contains(anno.nameAsString)
        }

        // Check if method body has manual authentication check
        val hasManualAuthCheck = method.body.map { body ->
            val bodyStr = body.toString()
            bodyStr.contains("checkAuthentication") ||
            bodyStr.contains("isAuthenticated") ||
            bodyStr.contains("getRemoteUser") ||
            bodyStr.contains("getUserPrincipal") ||
            bodyStr.contains("SecurityContext")
        }.orElse(false)

        // If no security at class or method level, and no manual check, report issue
        if (!currentClassHasSecurity && !hasMethodSecurity && !hasManualAuthCheck) {
            issues.add(createMissingAuthIssue(method))
        }
    }

    private fun createMissingAuthIssue(method: MethodDeclaration): InspectionIssue {
        val range = method.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        val httpMethod = method.annotations
            .firstOrNull { restMethodAnnotations.contains(it.nameAsString) }
            ?.nameAsString ?: "REST"

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = inspection.severity,
            message = "REST endpoint '${method.nameAsString}' missing authentication",
            description = """
                REST endpoint '${method.nameAsString}' ($httpMethod) does not have authentication configured.

                **Security Risk**: CRITICAL - Unauthorized access to API endpoints

                **Why This Is Dangerous**:
                - API endpoints are directly accessible via HTTP
                - No authentication means anyone can call the endpoint
                - Can expose sensitive data or operations
                - Common target for automated attacks
                - May violate compliance requirements (GDPR, HIPAA, etc.)

                **Problem Pattern**:
                ```java
                // ❌ PROBLEM - No authentication
                @Path("/api/users")
                public class UserResource {
                    @GET
                    @Path("/{id}")
                    public Response getUser(@PathParam("id") String id) {
                        // Anyone can access this!
                        return Response.ok(userService.getUser(id)).build();
                    }
                }
                ```

                **Correct Patterns**:

                **1. JAX-RS with @RolesAllowed** (Recommended):
                ```java
                @Path("/api/users")
                public class UserResource {
                    @GET
                    @Path("/{id}")
                    @RolesAllowed({"admin", "user"})  // Add this!
                    public Response getUser(@PathParam("id") String id) {
                        return Response.ok(userService.getUser(id)).build();
                    }
                }
                ```

                **2. Spring with @PreAuthorize**:
                ```java
                @RestController
                @RequestMapping("/api/users")
                public class UserController {
                    @GetMapping("/{id}")
                    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")  // Add this!
                    public User getUser(@PathVariable String id) {
                        return userService.getUser(id);
                    }
                }
                ```

                **3. Class-Level Security** (applies to all methods):
                ```java
                @Path("/api/users")
                @RolesAllowed("user")  // Applies to all endpoints
                public class UserResource {
                    @GET
                    @Path("/{id}")
                    public Response getUser(@PathParam("id") String id) {
                        return Response.ok(userService.getUser(id)).build();
                    }

                    @POST
                    @RolesAllowed("admin")  // Override for specific method
                    public Response createUser(User user) {
                        return Response.ok(userService.create(user)).build();
                    }
                }
                ```

                **4. Manual Authentication Check** (when annotations don't work):
                ```java
                @GET
                @Path("/{id}")
                public Response getUser(@PathParam("id") String id, @Context SecurityContext sc) {
                    // Manual check
                    if (sc.getUserPrincipal() == null) {
                        return Response.status(Response.Status.UNAUTHORIZED).build();
                    }

                    // Check role
                    if (!sc.isUserInRole("user")) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                    }

                    return Response.ok(userService.getUser(id)).build();
                }
                ```

                **5. Public Endpoint** (explicitly mark as public):
                ```java
                @GET
                @Path("/health")
                @PermitAll  // Explicitly mark as public
                public Response healthCheck() {
                    return Response.ok("OK").build();
                }
                ```

                **Common Bloomreach Roles**:
                - `admin` - Full administrative access
                - `author` - Content authors
                - `editor` - Content editors
                - `developer` - Developers
                - `xm.rest.user` - REST API access
                - `xm.rest.admin` - REST API administrative access

                **Configuration**:
                Ensure authentication is configured in web.xml:
                ```xml
                <security-constraint>
                    <web-resource-collection>
                        <web-resource-name>REST API</web-resource-name>
                        <url-pattern>/api/*</url-pattern>
                    </web-resource-collection>
                    <auth-constraint>
                        <role-name>xm.rest.user</role-name>
                    </auth-constraint>
                </security-constraint>
                ```

                **Testing**:
                ```bash
                # Test without auth (should fail)
                curl http://localhost:8080/api/users/123

                # Test with auth
                curl -u admin:admin http://localhost:8080/api/users/123
                ```

                **When to Use Each Approach**:
                - @RolesAllowed: Standard approach for most endpoints
                - @PermitAll: Explicitly public endpoints (health checks, documentation)
                - @DenyAll: Deprecated or disabled endpoints
                - Manual check: Custom authentication logic

                **Related Issues**:
                - CORS configuration for cross-origin requests
                - CSRF protection for state-changing operations
                - Rate limiting for public APIs

                **References**:
                - [JAX-RS Security](https://docs.oracle.com/javaee/7/tutorial/security-jaxrs002.htm)
                - [Bloomreach REST API Security](https://xmdocumentation.bloomreach.com/)
                - [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "methodName" to method.nameAsString,
                "httpMethod" to httpMethod
            )
        )
    }
}

/**
 * Quick fix: Add @RolesAllowed annotation
 */
private class AddRolesAllowedQuickFix : BaseQuickFix(
    name = "Add @RolesAllowed",
    description = "Adds @RolesAllowed annotation requiring authentication"
) {
    override fun apply(context: QuickFixContext) {
        // Implementation would add the annotation to the method
        // For now, this is a placeholder
    }
}

/**
 * Quick fix: Add @PermitAll annotation (for intentionally public endpoints)
 */
private class AddPermitAllQuickFix : BaseQuickFix(
    name = "Add @PermitAll (public endpoint)",
    description = "Marks endpoint as intentionally public"
) {
    override fun apply(context: QuickFixContext) {
        // Implementation would add the @PermitAll annotation
        // For now, this is a placeholder
    }
}
