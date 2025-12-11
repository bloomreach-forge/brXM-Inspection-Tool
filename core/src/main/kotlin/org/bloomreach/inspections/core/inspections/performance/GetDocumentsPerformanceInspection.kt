package org.bloomreach.inspections.core.inspections.performance

import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects use of HippoFolder.getDocuments() which can cause performance issues.
 *
 * getDocuments() loads all documents into memory at once.
 * For large folders, use getDocumentIterator() instead (8% of performance issues).
 */
class GetDocumentsPerformanceInspection : Inspection() {
    override val id = "performance.get-documents"
    override val name = "HippoFolder.getDocuments() Performance Issue"
    override val description = """
        Detects use of HippoFolder.getDocuments() which loads all documents into memory.
        Use getDocumentIterator() for better performance with large folders.
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
        val visitor = GetDocumentsVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") || path.contains("\\test\\")
    }
}

private class GetDocumentsVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(methodCall: MethodCallExpr, ctx: InspectionContext) {
        super.visit(methodCall, ctx)

        if (methodCall.nameAsString != "getDocuments") {
            return
        }

        issues.add(createGetDocumentsIssue(methodCall))
    }

    private fun createGetDocumentsIssue(methodCall: MethodCallExpr): InspectionIssue {
        val range = methodCall.name.range.map { r ->
            TextRange(r.begin.line, r.begin.column, r.end.line, r.end.column)
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "getDocuments() loads all documents into memory - use getDocumentIterator() instead",
            description = "HippoFolder.getDocuments() loads ALL documents into memory. Use getDocumentIterator() for better performance.",
            range = range
        )
    }
}
