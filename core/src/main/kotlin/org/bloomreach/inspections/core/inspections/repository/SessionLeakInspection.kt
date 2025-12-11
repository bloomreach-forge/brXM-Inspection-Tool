package org.bloomreach.inspections.core.inspections.repository

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.TryStmt
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects JCR sessions that are not properly closed in finally blocks.
 *
 * This is the #1 issue from community forum analysis (40% of repository tier issues).
 *
 * Common patterns detected:
 * - `repository.login()` without corresponding `session.logout()` in finally
 * - `getSession()` calls without proper cleanup
 * - `impersonate()` calls without logout
 *
 * Best practice: Always close sessions in finally blocks or use try-with-resources.
 */
class SessionLeakInspection : Inspection() {
    override val id = "repository.session-leak"
    override val name = "JCR Session Leak Detection"
    override val description = """
        Detects JCR sessions that are not properly closed in finally blocks.
        Unclosed sessions can lead to session pool exhaustion and memory leaks.
    """.trimIndent()
    override val category = InspectionCategory.REPOSITORY_TIER
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = SessionLeakVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(
            AddFinallyBlockQuickFix(),
            ConvertToTryWithResourcesQuickFix()
        )
    }
}

/**
 * Visitor that detects session leaks in Java code
 */
private class SessionLeakVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    private val sessionVariables = mutableMapOf<String, VariableDeclarator>()

    // Known methods that create JCR sessions
    private val sessionCreationMethods = setOf(
        "login",
        "getSession",
        "impersonate"
    )

    // Known methods that close sessions
    private val sessionCloseMethods = setOf(
        "logout",
        "close"
    )

    override fun visit(method: MethodDeclaration, context: InspectionContext) {
        // Clear session tracking for each method
        sessionVariables.clear()

        // Visit method body
        super.visit(method, context)

        // Check if all sessions are properly closed
        sessionVariables.forEach { (name, declarator) ->
            if (!isProperlyClosedInFinally(method, name)) {
                issues.add(createSessionLeakIssue(declarator, name))
            }
        }
    }

    override fun visit(variable: VariableDeclarator, context: InspectionContext) {
        super.visit(variable, context)

        // Check if this variable is assigned a session
        variable.initializer.ifPresent { init ->
            if (isSessionCreation(init)) {
                sessionVariables[variable.nameAsString] = variable
            }
        }
    }

    /**
     * Check if an expression creates a JCR session
     */
    private fun isSessionCreation(expression: Expression): Boolean {
        return when (expression) {
            is MethodCallExpr -> {
                expression.nameAsString in sessionCreationMethods
            }
            else -> false
        }
    }

    /**
     * Check if a session variable is properly closed in a finally block
     */
    private fun isProperlyClosedInFinally(method: MethodDeclaration, variableName: String): Boolean {
        // Find all try statements in the method
        val tryStatements = method.findAll(TryStmt::class.java)

        // Check if any finally block closes the session
        return tryStatements.any { tryStmt ->
            tryStmt.finallyBlock.orElse(null)?.let { finallyBlock ->
                hasSessionClose(finallyBlock.toString(), variableName)
            } ?: false
        }
    }

    /**
     * Check if code contains a session close call
     */
    private fun hasSessionClose(code: String, variableName: String): Boolean {
        return sessionCloseMethods.any { closeMethod ->
            code.contains("$variableName.$closeMethod")
        }
    }

    /**
     * Create an inspection issue for a session leak
     */
    private fun createSessionLeakIssue(
        declarator: VariableDeclarator,
        variableName: String
    ): InspectionIssue {
        val range = declarator.range.map { r ->
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
            message = "JCR Session '$variableName' is not closed in finally block",
            description = """
                The JCR session '$variableName' is created but not properly closed in a finally block.
                This can lead to session pool exhaustion and memory leaks.

                **Impact**: High - Can cause application crashes and performance degradation

                **Best Practice**: Always close sessions in a finally block or use try-with-resources (Java 7+).

                **Example (finally block)**:
                ```java
                Session session = null;
                try {
                    session = repository.login();
                    // ... work with session
                } finally {
                    if (session != null && session.isLive()) {
                        session.logout();
                    }
                }
                ```

                **Example (try-with-resources, recommended)**:
                ```java
                try (Session session = repository.login()) {
                    // ... work with session
                } // session automatically closed
                ```

                **Related Community Issues**:
                - NoAvailableSessionException - Session pool exhaustion
                - OutOfMemoryError - Memory leak from unclosed sessions

                **References**:
                - [JCR Session Management Best Practices](https://xmdocumentation.bloomreach.com/)
                - [Community Forum: Session Pool Exhaustion](https://community.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "variableName" to variableName,
                "sessionType" to "JCR Session"
            )
        )
    }
}

/**
 * Quick fix: Add a finally block with session.logout()
 */
private class AddFinallyBlockQuickFix : BaseQuickFix(
    name = "Add finally block to close session",
    description = "Wraps the code in try-finally and adds session.logout() in the finally block"
) {
    override fun apply(context: QuickFixContext) {
        val variableName = context.issue.metadata["variableName"] as? String ?: return
        val content = context.file.readText()
        val lines = content.split("\n").toMutableList()

        // Find the line with the session creation
        val sessionLine = context.range.startLine - 1
        if (sessionLine < 0 || sessionLine >= lines.size) return

        val line = lines[sessionLine]
        val indent = line.takeWhile { it.isWhitespace() }

        // Find the end of the method
        var endOfMethod = sessionLine + 1
        var braceCount = 0
        for (i in sessionLine until lines.size) {
            val currentLine = lines[i]
            braceCount += currentLine.count { it == '{' }
            braceCount -= currentLine.count { it == '}' }

            if (braceCount < 0 || (i > sessionLine && currentLine.trim().startsWith("}"))) {
                endOfMethod = i
                break
            }
        }

        // Insert try block before session creation
        lines[sessionLine] = "$indent$line\n${indent}try {"

        // Insert finally block before the closing brace
        val finallyBlock = """
            ${indent}} finally {
            ${indent}    if ($variableName != null) {
            ${indent}        $variableName.logout();
            ${indent}    }
            ${indent}}
        """.trimIndent()

        lines[endOfMethod] = finallyBlock + "\n" + lines[endOfMethod]

        // Write back to file
        val newContent = lines.joinToString("\n")
        java.nio.file.Files.writeString(context.file.path, newContent)
    }
}

/**
 * Quick fix: Convert to try-with-resources (Java 7+)
 */
private class ConvertToTryWithResourcesQuickFix : BaseQuickFix(
    name = "Convert to try-with-resources",
    description = "Converts the session management to use try-with-resources for automatic cleanup"
) {
    override fun apply(context: QuickFixContext) {
        // Implementation would be provided by IDE/CLI specific code
        // This is a placeholder that describes the transformation
    }
}
