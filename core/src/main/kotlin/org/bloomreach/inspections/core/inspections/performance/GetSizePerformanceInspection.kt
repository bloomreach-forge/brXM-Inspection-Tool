package org.bloomreach.inspections.core.inspections.performance

import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects use of HstQueryResult.getSize() which can cause performance issues.
 *
 * getSize() counts actual loaded results.
 * Use getTotalSize() to get the total count without loading all results.
 */
class GetSizePerformanceInspection : Inspection() {
    override val id = "performance.get-size"
    override val name = "HstQueryResult.getSize() Performance Issue"
    override val description = """
        Detects use of HstQueryResult.getSize() which only counts loaded results.
        Use getTotalSize() to get the total result count efficiently.
    """.trimIndent()
    override val category = InspectionCategory.PERFORMANCE
    override val severity = Severity.WARNING
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
        val visitor = GetSizeVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") || path.contains("\\test\\")
    }
}

private class GetSizeVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(methodCall: MethodCallExpr, ctx: InspectionContext) {
        super.visit(methodCall, ctx)

        if (methodCall.nameAsString != "getSize") {
            return
        }

        val scope = methodCall.scope.orElse(null)?.toString() ?: return
        val isQueryResult = scope.contains("QueryResult") || scope.contains("query")

        if (isQueryResult) {
            issues.add(createGetSizeIssue(methodCall))
        }
    }

    private fun createGetSizeIssue(methodCall: MethodCallExpr): InspectionIssue {
        val range = methodCall.name.range.map { r ->
            TextRange(r.begin.line, r.begin.column, r.end.line, r.end.column)
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "getSize() only counts loaded results - use getTotalSize() for total count",
            description = "HstQueryResult.getSize() returns size of loaded results page. Use getTotalSize() to get total matching documents.",
            range = range
        )
    }
}
