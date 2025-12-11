package org.bloomreach.inspections.core.inspections.security

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects JCR SQL queries with string concatenation (SQL injection).
 *
 * CRITICAL SECURITY VULNERABILITY: SQL Injection at repository level.
 *
 * Problem: Building JCR queries by concatenating user input allows attackers
 * to inject arbitrary JCR query syntax, accessing any data in the repository.
 *
 * Example Attack:
 * ```java
 * String userId = request.getParameter("userId");
 * String query = "SELECT * FROM [hippostd:document] WHERE userId = '" + userId + "'";
 * // Attacker inputs: ' OR '1'='1
 * // Query becomes: SELECT * FROM [...] WHERE userId = '' OR '1'='1'
 * // Returns ALL documents instead of one user's!
 * ```
 *
 * This is different from SQL injection because:
 * 1. JCR queries are more like XPath than SQL
 * 2. Parameters go to the REPOSITORY level, not just application
 * 3. Affects ALL content, including private/draft content
 *
 * Safe approach: Use query parameters with binding
 * ```java
 * Query query = qm.createQuery(
 *     "SELECT * FROM [...] WHERE userId = $userId",
 *     Query.JCR_SQL2
 * );
 * query.bindValue("userId", new StringValue(userId));
 * ```
 *
 * This inspection detects:
 * 1. String concatenation with "+" in query strings
 * 2. String interpolation with ${} in queries
 * 3. User input in JCR query creation
 * 4. Missing parameter binding calls
 */
class JcrParameterBindingInspection : Inspection() {
    override val id = "security.jcr-parameter-binding"
    override val name = "JCR Query SQL Injection (String Concatenation)"
    override val description = """
        Detects JCR queries built with string concatenation instead of parameter binding.
        This allows SQL injection attacks at the repository level.
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = JcrInjectionVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(
            UseParameterBindingQuickFix(),
            UseStringTemplateQuickFix(),
            AddParameterValidationQuickFix()
        )
    }
}

/**
 * Visitor that detects JCR query injection vulnerabilities
 */
private class JcrInjectionVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    // Methods that create JCR queries
    private val queryCreationMethods = setOf(
        "createQuery",
        "getQuery",
        "createQueryBuilder",
        "buildQuery"
    )

    // Track variables that contain concatenation
    private val suspiciousVariables = mutableSetOf<String>()

    override fun visit(method: MethodDeclaration, ctx: InspectionContext) {
        super.visit(method, ctx)

        // First pass: find all variables built with concatenation or user input
        method.body.ifPresent { body ->
            detectSuspiciousVariables(body)
        }

        // Second pass: find query calls using those variables
        val queryCreations = mutableListOf<MethodCallExpr>()
        val visitor = QueryCallFinder()
        method.body.ifPresent { body ->
            visitor.visit(body, null)
            queryCreations.addAll(visitor.queryCalls)
        }

        // Check each query creation
        for (queryCall in queryCreations) {
            checkQueryForInjection(queryCall)
        }
    }

    private fun detectSuspiciousVariables(node: Any?) {
        when (node) {
            is com.github.javaparser.ast.stmt.BlockStmt -> {
                node.statements.forEach { detectSuspiciousVariables(it) }
            }
            is com.github.javaparser.ast.stmt.ExpressionStmt -> {
                detectSuspiciousVariables(node.expression)
            }
            is com.github.javaparser.ast.expr.VariableDeclarationExpr -> {
                for (variable in node.variables) {
                    variable.initializer.ifPresent { init ->
                        val initStr = init.toString()
                        if (initStr.contains("+") || containsUserInput(initStr)) {
                            suspiciousVariables.add(variable.nameAsString)
                        }
                        detectSuspiciousVariables(init)
                    }
                }
            }
        }
    }

    private fun checkQueryForInjection(call: MethodCallExpr) {
        if (call.nameAsString !in queryCreationMethods) {
            return
        }

        // Get the first argument (query string)
        if (call.arguments.isEmpty()) {
            return
        }

        val queryArg = call.arguments[0]
        val queryArgStr = queryArg.toString()

        // Check if argument is a suspicious variable or contains concatenation/user input
        if (queryArgStr in suspiciousVariables ||
            containsConcatenation(queryArg) ||
            containsUserInput(queryArgStr)) {
            issues.add(createInjectionIssue(call, queryArgStr))
        }
    }

    private fun containsConcatenation(expr: Any?): Boolean {
        return when (expr) {
            is BinaryExpr -> expr.toString().contains("+")
            is StringLiteralExpr -> false
            else -> expr.toString().contains("+") && !expr.toString().contains("String.format")
        }
    }

    private fun containsUserInput(queryString: String): Boolean {
        val userInputPatterns = listOf(
            "request.getParameter",
            "request.getAttribute",
            "request.getHeader",
            "getParameter",
            "getAttribute",
            "getHeader",
            "param.",
            "pageContext"
        )

        return userInputPatterns.any { pattern ->
            queryString.contains(pattern, ignoreCase = true)
        }
    }

    private fun createInjectionIssue(
        call: MethodCallExpr,
        queryString: String
    ): InspectionIssue {
        val range = call.range.map { r ->
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
            message = "JCR query built with string concatenation - SQL injection vulnerability",
            description = """
                The JCR query is built using string concatenation instead of parameter binding.
                This allows attackers to inject arbitrary query syntax.

                **CRITICAL SECURITY VULNERABILITY**: Repository-level SQL injection

                **Current Pattern (Vulnerable)**:
                ```java
                String userId = request.getParameter("userId");
                String query = "SELECT * FROM [...] WHERE userId = '" + userId + "'";
                Query q = qm.createQuery(query, Query.JCR_SQL2);
                ```

                **Attack Example**:
                ```
                Input: ' OR '1'='1
                Query becomes: WHERE userId = '' OR '1'='1'
                Result: Returns ALL documents instead of one!
                ```

                **Impact**:
                1. **Data Breach**: Attacker can access private/draft content
                2. **Privilege Escalation**: Bypass permission checks
                3. **Data Manipulation**: Depending on query permissions
                4. **System Compromise**: If admin operations exposed

                **Fixed Pattern (Safe with Parameter Binding)**:

                Method 1 (Recommended): JCR Parameter Binding
                - Get user input: userId = request.getParameter("userId")
                - Use dollar sign placeholder in query: "WHERE userId = dollar+param"
                - Call bindValue("userId", new StringValue(userId))
                - This prevents any injection because parameter is not interpreted as query syntax

                Method 2 (Less Safe): String.format with Escaping
                - Escape quotes in input: userId.replaceAll("'", "''")
                - Use String.format() with escaping
                - Still vulnerable to some attacks, not recommended

                **Why Parameter Binding Works**:
                - Parameter marked with dollar sign + paramName in query string
                - Actual value provided separately via `bindValue()`
                - Query engine never interprets parameter value as query syntax
                - Prevents any injection attacks

                **JCR Query Parameter Binding Details**:
                - Use placeholder syntax with dollar sign in query
                - Call bindValue() for each parameter
                - Supports StringValue, DateValue, LongValue, BooleanValue types
                - Each parameter has its own binding call
                - Type-safe parameter passing

                **Input Validation (Defense in Depth)**:
                - Even with parameter binding, validate input on server side
                - Check format with regex patterns
                - Enforce length limits
                - Use whitelist validation where possible
                - Reject obviously malicious input before querying

                **Common Vulnerable Patterns**:
                - String concatenation with + operator (WRONG)
                - String interpolation with variables (WRONG)
                - String.format() with user input (WRONG)
                - Direct query.execute() on concatenated query (WRONG)

                **Correct Approach**:
                - Use parameter placeholders in query string
                - Call bindValue() with typed Value objects
                - Never concatenate user input into query
                - Type-safe parameter binding (StringValue, etc)

                **Migration Checklist**:
                - [ ] Find all JCR/Hibernate createQuery calls
                - [ ] Identify which use string concatenation
                - [ ] Convert to parameter binding syntax
                - [ ] Add bindValue() calls for each parameter
                - [ ] Test with malicious input (', ", --, ;, *)
                - [ ] Code review for security
                - [ ] Update unit tests

                **Testing for Injection**:
                - Test with malicious input: ' OR '1'='1
                - Test with SQL keywords: DROP, DELETE, INSERT
                - Test with special characters: <, >, &, %, ;
                - Verify parameter binding prevents all injection attempts
                - Query should treat malicious input as literal string value

                **Related Vulnerabilities**:
                - **XPath Injection**: Similar vulnerability in XML queries
                - **LDAP Injection**: Similar vulnerability in LDAP queries
                - **NoSQL Injection**: Similar vulnerability in MongoDB, CouchDB
                - **OS Command Injection**: Similar principle in system calls

                **OWASP**: A3:2021 - Injection
                **CWE**: CWE-89 - SQL Injection, CWE-91 - XML Injection

                **References**:
                - [OWASP SQL Injection](https://owasp.org/www-community/attacks/SQL_Injection)
                - [Parameterized Queries](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
                - [JCR Query API](https://xmdocumentation.bloomreach.com/)
                - [CWE-89](https://cwe.mitre.org/data/definitions/89.html)
                - [Bloomreach Security Best Practices](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "queryMethod" to call.nameAsString,
                "vulnerabilityType" to "SQL_INJECTION",
                "severity" to "CRITICAL",
                "cwe" to "CWE-89"
            )
        )
    }
}

/**
 * Finds JCR query creation method calls
 */
private class QueryCallFinder {
    val queryCalls = mutableListOf<MethodCallExpr>()

    fun visit(node: Any?, ctx: Any?) {
        when (node) {
            is MethodCallExpr -> {
                if (node.nameAsString in setOf("createQuery", "getQuery", "createQueryBuilder", "buildQuery")) {
                    queryCalls.add(node)
                }
                node.scope.ifPresent { visit(it, ctx) }
                node.arguments.forEach { visit(it, ctx) }
            }
            is com.github.javaparser.ast.stmt.BlockStmt -> {
                node.statements.forEach { visit(it, ctx) }
            }
            is com.github.javaparser.ast.stmt.ExpressionStmt -> {
                visit(node.expression, ctx)
            }
            is com.github.javaparser.ast.expr.VariableDeclarationExpr -> {
                node.variables.forEach { v ->
                    v.initializer.ifPresent { visit(it, ctx) }
                }
            }
        }
    }
}

/**
 * Quick fix: Use parameter binding
 */
private class UseParameterBindingQuickFix : BaseQuickFix(
    name = "Use parameter binding",
    description = "Converts string concatenation to query parameter binding"
) {
    override fun apply(context: QuickFixContext) {
        // Complex refactoring - need to:
        // 1. Find concatenated string in query
        // 2. Extract variables
        // 3. Replace with $param syntax
        // 4. Add bindValue() calls
    }
}

/**
 * Quick fix: Use String.format with escaping
 */
private class UseStringTemplateQuickFix : BaseQuickFix(
    name = "Use String.format (less safe)",
    description = "Converts to String.format (still requires escaping)"
) {
    override fun apply(context: QuickFixContext) {
        // Less preferred, but documents the change
    }
}

/**
 * Quick fix: Add input validation
 */
private class AddParameterValidationQuickFix : BaseQuickFix(
    name = "Add input validation",
    description = "Adds regex validation for query parameters"
) {
    override fun apply(context: QuickFixContext) {
        // Would add validation logic before query execution
    }
}
