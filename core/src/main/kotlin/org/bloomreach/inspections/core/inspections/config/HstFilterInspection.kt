package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects issues in HST Filter implementations.
 *
 * HST filters must implement proper lifecycle and chain handling.
 */
class HstFilterInspection : Inspection() {
    override val id = "config.hst-filter"
    override val name = "HST Filter Implementation Issues"
    override val description = """
        Detects issues in HST Filter implementations.
        Filters must call chain.doFilter() and handle exceptions properly.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
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
        val visitor = FilterVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") || path.contains("\\test\\")
    }
}

private class FilterVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(classDecl: ClassOrInterfaceDeclaration, ctx: InspectionContext) {
        super.visit(classDecl, ctx)

        if (classDecl.isInterface) {
            return
        }

        val implementsFilter = classDecl.implementedTypes.any {
            it.nameAsString.contains("Filter")
        }

        if (!implementsFilter) {
            return
        }

        classDecl.methods.forEach { method ->
            if (method.nameAsString == "doFilter") {
                checkDoFilterMethod(method, classDecl)
            }
        }
    }

    private fun checkDoFilterMethod(method: MethodDeclaration, classDecl: ClassOrInterfaceDeclaration) {
        if (!method.body.isPresent) {
            return
        }

        val body = method.body.get().toString()

        // Check if filter calls chain.doFilter()
        val callsChain = body.contains("chain.doFilter") ||
                        body.contains("filterChain.doFilter")

        if (!callsChain) {
            issues.add(createMissingChainCallIssue(method, classDecl))
        }
    }

    private fun createMissingChainCallIssue(
        method: MethodDeclaration,
        classDecl: ClassOrInterfaceDeclaration
    ): InspectionIssue {
        val range = method.name.range.map { r ->
            TextRange(r.begin.line, r.begin.column, r.end.line, r.end.column)
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "Filter doFilter() must call chain.doFilter() to continue request processing",
            description = "HST filters must call chain.doFilter() to pass control to the next filter in the chain.",
            range = range
        )
    }
}
