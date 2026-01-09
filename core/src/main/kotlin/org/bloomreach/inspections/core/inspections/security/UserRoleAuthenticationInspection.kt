package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.java.JavaParser
import org.bloomreach.inspections.core.parsers.ParseResult
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.visitor.VoidVisitorAdapter

/**
 * Detects missing or incorrect role authentication checks for Channel Manager operations.
 *
 * Channel Manager requires users to have the 'xm.channel.user' role. This inspection detects:
 * 1. Methods that handle channel operations without any role checks
 * 2. Methods checking for wrong roles (e.g., "admin" instead of "xm.channel.user")
 * 3. Missing @Secured or @PreAuthorize annotations on channel endpoints
 *
 * Supported patterns:
 * - @Secured("ROLE_xm.channel.user") - Correct
 * - @PreAuthorize("hasRole('xm.channel.user')") - Correct
 * - @Secured("admin") - Wrong role
 * - userService.hasRole("admin") without "xm.channel.user" - Wrong role
 * - No role checks on channel endpoints - Missing
 */
class UserRoleAuthenticationInspection : Inspection() {
    override val id = "security.user-role-authentication"
    override val name = "Missing or Incorrect Channel Manager Role Authentication"
    override val description = """
        Detects missing or incorrect role checks for Channel Manager operations.

        Channel Manager requires users to have the 'xm.channel.user' role. Methods
        that handle channel operations, modifications, or administrative tasks must
        verify this role. Missing or incorrect role checks allow unauthorized access.

        **Problem:**
        - Method handles channels without checking xm.channel.user role
        - Role check verifies wrong role (e.g., "admin" instead of "xm.channel.user")
        - @Secured or @PreAuthorize annotation missing from channel endpoints
        - Role check easily bypassed or incomplete

        **Solution:**
        Add proper role authentication using Spring Security annotations or checks.

        **Affected Operations:**
        - Creating/editing/deleting channels
        - Modifying channel properties
        - Accessing channel management endpoints
        - Changing channel configuration

        **Supported Files:**
        - Java controllers and services
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    private val channelOperationPatterns = listOf(
        Regex(".*[Cc]hannel.*"),
        Regex(".*[Ww]orkspace.*"),
        Regex(".*[Cc]onfiguration.*"),
        Regex(".*[Mm]anager.*")
    )

    private val channelMethodPatterns = listOf(
        "getChannel", "createChannel", "updateChannel", "deleteChannel",
        "saveChannel", "editChannel", "modifyChannel",
        "getWorkspace", "updateWorkspace",
        "getConfiguration", "updateConfiguration"
    )

    private val validRoles = setOf(
        "xm.channel.user", "ROLE_xm.channel.user",
        "channel.user", "ROLE_channel.user",
        "channel_user", "ROLE_channel_user"
    )

    private val insecureRoles = setOf(
        "admin", "ADMIN", "ROLE_ADMIN",
        "user", "USER", "ROLE_USER",
        "authenticated", "AUTHENTICATED", "ROLE_AUTHENTICATED"
    )

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        if (context.language != FileType.JAVA) {
            return emptyList()
        }

        if (isTestFile(context)) {
            return emptyList()
        }

        return try {
            val parser = JavaParser.instance
            val parseResult = parser.parse(context.fileContent)

            if (parseResult !is ParseResult.Success) {
                return emptyList()
            }

            val visitor = RoleAuthenticationVisitor(this, context)
            visitor.visit(parseResult.ast, null)
            visitor.issues
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val name = context.file.name.lowercase()
        return name.endsWith("test.java") || name.endsWith("tests.java")
    }

    private fun isChannelRelatedMethod(methodName: String): Boolean {
        return channelMethodPatterns.any { it.equals(methodName, ignoreCase = true) } ||
               channelOperationPatterns.any { it.matches(methodName) }
    }

    private fun hasSecurityAnnotation(method: MethodDeclaration): Boolean {
        return method.annotations.any { ann ->
            ann.nameAsString.let { name ->
                name == "Secured" || name == "PreAuthorize" ||
                name == "org.springframework.security.access.annotation.Secured" ||
                name == "org.springframework.security.access.prepost.PreAuthorize"
            }
        }
    }

    private fun hasCorrectRoleInAnnotation(method: MethodDeclaration): Boolean {
        return method.annotations.any { ann ->
            val name = ann.nameAsString
            if (name == "Secured" || name == "org.springframework.security.access.annotation.Secured") {
                checkSecuredAnnotation(ann)
            } else if (name == "PreAuthorize" || name == "org.springframework.security.access.prepost.PreAuthorize") {
                checkPreAuthorizeAnnotation(ann)
            } else {
                false
            }
        }
    }

    private fun checkSecuredAnnotation(ann: AnnotationExpr): Boolean {
        val valueStr = ann.toString()
        return validRoles.any { role ->
            valueStr.contains("\"$role\"") || valueStr.contains("'$role'")
        }
    }

    private fun checkPreAuthorizeAnnotation(ann: AnnotationExpr): Boolean {
        val valueStr = ann.toString()
        return validRoles.any { role ->
            valueStr.contains("'$role'") || valueStr.contains("\"$role\"") ||
            valueStr.contains("hasRole('$role')") || valueStr.contains("hasRole(\"$role\")")
        }
    }

    private fun hasIncorrectRoleCheck(method: MethodDeclaration): Boolean {
        val methodContent = method.toString()

        // Check for insecure role checks
        insecureRoles.forEach { role ->
            if (methodContent.contains("hasRole(\"$role\")") ||
                methodContent.contains("hasRole('$role')") ||
                methodContent.contains("\"$role\"")) {
                return true
            }
        }

        return false
    }

    private fun createMissingRoleCheckIssue(context: InspectionContext, method: MethodDeclaration): InspectionIssue {
        val lineNumber = method.range.map { it.begin.line }.orElse(1)
        val range = TextRange.wholeLine(lineNumber)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.ERROR,
            message = "Channel operation '${method.nameAsString}' missing role authentication check",
            description = """
                **Problem:** Method handles channel operations without verifying user role

                The method '${method.nameAsString}' appears to handle Channel Manager operations
                but does not check if the user has the required 'xm.channel.user' role.
                This allows unauthorized users to modify channels.

                **Current Code:**
                ```java
                public ${method.typeAsString} ${method.nameAsString}(...) {
                    // Missing role check!
                }
                ```

                **Impact:**
                - Unauthorized users can access channel management functions
                - Security vulnerability: privilege escalation
                - Channels can be modified by users who shouldn't have access
                - Potential data integrity issues

                **Solution:** Add Spring Security role check using @Secured or @PreAuthorize

                **Option 1: Using @Secured annotation (recommended)**
                ```java
                @Secured("ROLE_xm.channel.user")
                public ${method.typeAsString} ${method.nameAsString}(...) {
                    // Now protected
                }
                ```

                **Option 2: Using @PreAuthorize annotation**
                ```java
                @PreAuthorize("hasRole('xm.channel.user')")
                public ${method.typeAsString} ${method.nameAsString}(...) {
                    // Now protected
                }
                ```

                **Option 3: Programmatic check inside method**
                ```java
                public ${method.typeAsString} ${method.nameAsString}(...) {
                    if (!userService.hasRole("xm.channel.user")) {
                        throw new AccessDeniedException("User lacks xm.channel.user role");
                    }
                    // Method logic
                }
                ```

                **Important Notes:**
                - Always use 'xm.channel.user' role, not generic 'admin' or 'user' roles
                - Annotation-based checks (@Secured) are preferred over programmatic checks
                - Ensure Spring Security is properly configured in application context
                - All channel-related endpoints must be protected

                **Valid Role Names:**
                - xm.channel.user (preferred)
                - channel.user
                - Other organization-specific roles that include channel management

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [Spring Security Role-Based Access Control](https://docs.spring.io/spring-security/reference/servlet/authorization/index.html)
                - [brXM Security Configuration](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "methodName" to method.nameAsString,
                "issue" to "missingRoleCheck",
                "requiredRole" to "xm.channel.user",
                "suggestion" to "@Secured(\"ROLE_xm.channel.user\")"
            )
        )
    }

    private fun createIncorrectRoleCheckIssue(context: InspectionContext, method: MethodDeclaration): InspectionIssue {
        val lineNumber = method.range.map { it.begin.line }.orElse(1)
        val range = TextRange.wholeLine(lineNumber)

        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = Severity.ERROR,
            message = "Channel operation '${method.nameAsString}' uses incorrect role for authentication",
            description = """
                **Problem:** Method checks for wrong role instead of 'xm.channel.user'

                The method appears to check user permissions, but verifies the wrong role.
                This either grants too much access or blocks legitimate channel manager users.

                **Current Code:**
                ```java
                public ${method.typeAsString} ${method.nameAsString}(...) {
                    // Checking for wrong role (admin, user, etc.)
                }
                ```

                **Impact:**
                - Role-based access control is ineffective
                - Wrong users may get access to channel management
                - Or, authorized channel managers are blocked

                **Solution:** Use the correct 'xm.channel.user' role

                **Correct Pattern:**
                ```java
                @Secured("ROLE_xm.channel.user")
                public ${method.typeAsString} ${method.nameAsString}(...) {
                    // Correctly protects channel operations
                }
                ```

                **Common Mistakes:**
                ❌ Checking for "admin" role only (too broad, wrong purpose)
                ❌ Checking for generic "user" role (insufficient)
                ❌ Checking for "authenticated" (not specific enough)
                ✅ Checking for "xm.channel.user" (correct)

                **Role Hierarchy:**
                ```
                ROLE_ADMIN
                  ├─ ROLE_xm.channel.user  ✅ Has channel access
                  ├─ ROLE_editor
                  └─ ROLE_viewer

                ROLE_USER
                  └─ Generic user role (NOT sufficient for channels)
                ```

                **References:**
                - [Channel Manager Troubleshooting](https://xmdocumentation.bloomreach.com/library/concepts/channels/channel-manager-troubleshooting.html)
                - [Role-Based Access Control in brXM](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "methodName" to method.nameAsString,
                "issue" to "incorrectRoleCheck",
                "correctRole" to "xm.channel.user",
                "suggestion" to "@Secured(\"ROLE_xm.channel.user\")"
            )
        )
    }

    private inner class RoleAuthenticationVisitor(
        private val inspection: UserRoleAuthenticationInspection,
        private val context: InspectionContext
    ) : VoidVisitorAdapter<Void?>() {

        val issues = mutableListOf<InspectionIssue>()

        override fun visit(method: MethodDeclaration, arg: Void?) {
            super.visit(method, arg)

            // Only check methods that appear to be channel-related
            val methodName = method.nameAsString
            if (!isChannelRelatedMethod(methodName)) {
                return
            }

            // Skip private helper methods
            if (method.isPrivate) {
                return
            }

            // Check for @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
            val isEndpoint = method.annotations.any { ann ->
                ann.nameAsString.let { name ->
                    name.endsWith("Mapping") || name.contains("Request")
                }
            }

            if (!isEndpoint) {
                return
            }

            // Check 1: Missing security annotation
            if (!hasSecurityAnnotation(method)) {
                issues.add(createMissingRoleCheckIssue(context, method))
                return
            }

            // Check 2: Has security annotation but wrong role
            if (hasSecurityAnnotation(method) && !hasCorrectRoleInAnnotation(method)) {
                issues.add(createIncorrectRoleCheckIssue(context, method))
                return
            }

            // Check 3: Programmatic role check with wrong role
            if (hasIncorrectRoleCheck(method)) {
                issues.add(createIncorrectRoleCheckIssue(context, method))
            }
        }
    }
}
