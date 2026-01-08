package org.bloomreach.inspections.core.inspections.security

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.IfStmt
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects user input accessed without validation in dangerous contexts.
 *
 * Missing input validation leads to:
 * - SQL injection
 * - XSS attacks
 * - Path traversal
 * - Command injection
 * - DoS through file size attacks
 *
 * From community analysis: Input validation gaps found in component parameters and file uploads.
 */
class MissingInputValidationInspection : Inspection() {
    override val id = "security.missing-input-validation"
    override val name = "Missing Input Validation"
    override val description = """
        Detects user input used without validation in dangerous contexts.

        Missing input validation allows attackers to:
        1. Inject SQL, XSS, or commands into queries/responses
        2. Upload malicious files or exceed size limits
        3. Traverse directories via path manipulation
        4. Perform open redirects
        5. Cause denial of service

        This inspection checks for:
        - getParameter() without validation before SQL queries
        - File uploads without size or type checks
        - Input used in redirects without validation
        - Parameters used in file operations without sanitization
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.ERROR
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
        val visitor = InputValidationVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/src/test/") || path.contains("\\src\\test\\")
    }

    fun buildDescription(inputMethod: String, dangerousContext: String, variableName: String): String {
        return """
            Input validation missing: Variable '$variableName' from $inputMethod() used in dangerous context: $dangerousContext().

            **Security Risk**: CRITICAL

            **What Can Happen**:
            - **SQL Injection**: Attacker injects SQL commands to read/modify/delete data
            - **XSS**: Attacker injects JavaScript to steal sessions or deface pages
            - **Path Traversal**: Attacker accesses files outside intended directory
            - **Command Injection**: Attacker executes arbitrary system commands
            - **DoS**: Attacker uploads huge files to exhaust disk/memory

            **How to Fix**:

            **1. Validate Input Length and Format**:
            ```java
            String userInput = getParameter("search");

            // Null check
            if (userInput == null) {
                throw new IllegalArgumentException("Parameter 'search' is required");
            }

            // Length check
            if (userInput.length() > 100) {
                throw new IllegalArgumentException("Input too long");
            }

            // Format validation with regex
            if (!userInput.matches("[a-zA-Z0-9]+")) {
                throw new IllegalArgumentException("Invalid characters in input");
            }
            ```

            **2. Use Parameterized Queries (SQL Injection Prevention)**:
            ```java
            // ❌ WRONG - String concatenation
            String query = "SELECT * FROM docs WHERE title = '" + userInput + "'";

            // ✅ CORRECT - Parameterized query
            String query = "SELECT * FROM docs WHERE title = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, userInput);
            ```

            **3. Validate File Uploads**:
            ```java
            FileItem file = getFile("upload");

            // Check size
            if (file.getSize() > 10 * 1024 * 1024) { // 10 MB
                throw new IllegalArgumentException("File too large");
            }

            // Check file type
            String contentType = file.getContentType();
            if (!contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Only images allowed");
            }

            // Sanitize filename
            String filename = file.getName();
            filename = filename.replaceAll("[^a-zA-Z0-9.-]", "_");
            ```

            **4. Use Whitelisting (Preferred over Blacklisting)**:
            ```java
            // ✅ GOOD - Whitelist allowed values
            String action = getParameter("action");
            Set<String> allowedActions = Set.of("view", "edit", "delete");
            if (!allowedActions.contains(action)) {
                throw new IllegalArgumentException("Invalid action");
            }
            ```

            **5. Escape Output (XSS Prevention)**:
            ```java
            String userInput = getParameter("name");

            // In JSP
            <c:out value="${'$'}{userInput}" />

            // In FreeMarker
            ${'$'}{userInput?html}

            // In Java
            String escaped = StringEscapeUtils.escapeHtml4(userInput);
            ```

            **6. Validate Redirects**:
            ```java
            String url = getParameter("returnUrl");

            // Whitelist allowed domains
            if (!url.startsWith("/") && !url.startsWith("https://trusted.com")) {
                throw new IllegalArgumentException("Invalid redirect URL");
            }

            response.sendRedirect(url);
            ```

            **Best Practices**:
            - Validate ALL user input at entry points
            - Use whitelist validation when possible
            - Apply principle of least privilege
            - Log validation failures for security monitoring
            - Use framework validation (JSR-303 Bean Validation)
            - Never trust client-side validation alone

            **References**:
            - OWASP Input Validation: https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html
            - OWASP SQL Injection Prevention: https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html
            - CWE-20: Improper Input Validation
        """.trimIndent()
    }
}

private class InputValidationVisitor(
    private val inspection: MissingInputValidationInspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    // Methods that retrieve user input
    private val inputMethods = setOf(
        "getParameter",
        "getFile",
        "getParameterValues",
        "getHeader",
        "getCookie"
    )

    // Methods that represent dangerous contexts
    private val dangerousContexts = setOf(
        "createQuery",      // SQL injection
        "executeQuery",     // SQL execution
        "execute",          // Command injection
        "write",            // File operation
        "sendRedirect",     // Open redirect
        "setAttribute",     // XSS via attributes
        "setProperty"       // JCR injection
    )

    // Methods that indicate validation
    private val validationMethods = setOf(
        "matches",          // Regex validation
        "validate",         // Explicit validation
        "sanitize",         // Sanitization
        "escape",           // Escaping
        "isEmpty",          // Null/empty check
        "isBlank",          // Whitespace check
        "length"            // Length check
    )

    // Track variables that hold user input
    private val inputVariables = mutableMapOf<String, InputSource>()

    // Track validated variables
    private val validatedVariables = mutableSetOf<String>()

    private data class InputSource(
        val variableName: String,
        val inputMethod: String,
        val line: Int,
        val derivedFrom: String? = null  // Track if this variable is derived from another
    )

    override fun visit(method: MethodDeclaration, ctx: InspectionContext) {
        // Clear state for each method
        inputVariables.clear()
        validatedVariables.clear()

        super.visit(method, ctx)
    }

    override fun visit(variable: VariableDeclarator, ctx: InspectionContext) {
        super.visit(variable, ctx)

        val varName = variable.nameAsString

        // Check if variable is initialized with user input
        variable.initializer.ifPresent { init ->
            val initStr = init.toString()

            // Direct input from input method
            if (init is MethodCallExpr) {
                val methodName = init.nameAsString
                if (inputMethods.contains(methodName)) {
                    val line = init.begin.map { it.line }.orElse(0)
                    inputVariables[varName] = InputSource(varName, methodName, line)
                }
            }

            // Check if initializer uses any tracked input variables (derived input)
            // Create a snapshot of keys before we might modify inputVariables
            val trackedVars = inputVariables.keys.toList()
            trackedVars.forEach { trackedVar ->
                if (trackedVar != varName && initStr.contains(trackedVar)) {
                    val source = inputVariables[trackedVar]!!
                    inputVariables[varName] = InputSource(varName, source.inputMethod, source.line, derivedFrom = trackedVar)
                }
            }
        }
    }

    override fun visit(ifStmt: IfStmt, ctx: InspectionContext) {
        super.visit(ifStmt, ctx)

        // Check if the if statement validates any input variables
        val condition = ifStmt.condition.toString()

        inputVariables.keys.forEach { varName ->
            // Check for null checks, length checks, regex validation
            if (condition.contains(varName)) {
                if (condition.contains("null") ||
                    condition.contains("isEmpty") ||
                    condition.contains("isBlank") ||
                    condition.contains("length") ||
                    condition.contains("matches") ||
                    condition.contains("validate")) {
                    validatedVariables.add(varName)

                    // Also mark all derived variables as validated
                    inputVariables.forEach { (derivedVar, source) ->
                        if (source.derivedFrom == varName) {
                            validatedVariables.add(derivedVar)
                        }
                    }
                }
            }
        }
    }

    override fun visit(call: MethodCallExpr, ctx: InspectionContext) {
        super.visit(call, ctx)

        val methodName = call.nameAsString

        // Check if this is a validation method being called on input variables
        if (validationMethods.contains(methodName) && call.scope.isPresent) {
            val scope = call.scope.get().toString()
            if (inputVariables.containsKey(scope)) {
                validatedVariables.add(scope)

                // Also mark all derived variables as validated
                inputVariables.forEach { (derivedVar, source) ->
                    if (source.derivedFrom == scope) {
                        validatedVariables.add(derivedVar)
                    }
                }
            }
        }

        // Check if this is a dangerous context using unvalidated input
        if (dangerousContexts.contains(methodName)) {
            call.arguments.forEach { arg ->
                val argStr = arg.toString()

                // Check if argument uses an unvalidated input variable
                inputVariables.forEach { (varName, source) ->
                    if (argStr.contains(varName)) {
                        // Check if this variable or its source is validated
                        val isValidated = validatedVariables.contains(varName) ||
                                (source.derivedFrom != null && validatedVariables.contains(source.derivedFrom))

                        if (!isValidated) {
                            // Found unvalidated input in dangerous context
                            val line = call.begin.map { it.line }.orElse(0)
                            issues.add(
                                InspectionIssue(
                                    inspection = inspection,
                                    file = context.file,
                                    severity = inspection.severity,
                                    message = "Unvalidated input '$varName' from ${source.inputMethod}() used in ${methodName}()",
                                    description = inspection.buildDescription(source.inputMethod, methodName, varName),
                                    range = TextRange.wholeLine(line),
                                    metadata = mapOf(
                                        "inputMethod" to source.inputMethod,
                                        "dangerousContext" to methodName,
                                        "variableName" to varName,
                                        "line" to line
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
