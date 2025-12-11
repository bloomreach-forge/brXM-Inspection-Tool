package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects usage of System.out and System.err calls.
 *
 * Console output should use proper logging framework.
 */
class SystemOutCallsInspection : Inspection() {
    override val id = "config.system-out-calls"
    override val name = "System.out/err Usage"
    override val description = """
        Detects usage of System.out and System.err.
        Use a proper logging framework (SLF4J, Log4j) instead.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.INFO
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        if (isTestFile(context)) {
            return emptyList()
        }

        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = SystemOutVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") || path.contains("\\test\\")
    }
}

private class SystemOutVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(methodCall: MethodCallExpr, ctx: InspectionContext) {
        super.visit(methodCall, ctx)

        val methodName = methodCall.nameAsString
        if (methodName !in setOf("print", "println", "printf")) {
            return
        }

        val scope = methodCall.scope.orElse(null)?.toString() ?: return
        
        val isSystemOut = scope == "System.out" || scope == "System.err"
        
        if (isSystemOut) {
            issues.add(createSystemOutIssue(methodCall, scope))
        }
    }

    private fun createSystemOutIssue(methodCall: MethodCallExpr, scope: String): InspectionIssue {
        val range = methodCall.name.range.map { r ->
            TextRange(r.begin.line, r.begin.column, r.end.line, r.end.column)
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.INFO,
            message = "$scope.${methodCall.nameAsString}() should use logging framework instead",
            description = """
                Using System.out/System.err is an anti-pattern in production code.
                Use a proper logging framework like SLF4J or Log4j.

                **Problem**: Code uses $scope.${methodCall.nameAsString}()

                **Why This Is Bad**:
                - No log levels (cannot filter by severity)
                - No timestamps or context information
                - Cannot redirect to files or external systems
                - Poor performance (synchronized output)
                - Cannot be disabled in production

                **Fix: Use SLF4J Logger**
                ```java
                // Add logger field
                private static final Logger log = LoggerFactory.getLogger(MyClass.class);

                // Replace System.out.println
                log.info("Message: {}", value);
                log.debug("Debug info: {}", data);
                log.error("Error occurred", exception);
                ```

                **Benefits of Logging Framework**:
                - Configurable log levels (DEBUG, INFO, WARN, ERROR)
                - Structured logging with context
                - Can route to files, databases, monitoring systems
                - Better performance (async logging)
                - Production-ready

                **Reference**: https://www.slf4j.org/
            """.trimIndent(),
            range = range
        )
    }
}
