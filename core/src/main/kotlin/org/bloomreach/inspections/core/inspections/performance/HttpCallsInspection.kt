package org.bloomreach.inspections.core.inspections.performance

import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects synchronous HTTP calls in HST components.
 *
 * HTTP calls block request threads and cause poor performance.
 * Consider async calls or caching.
 */
class HttpCallsInspection : Inspection() {
    override val id = "performance.http-calls"
    override val name = "Synchronous HTTP Calls in Components"
    override val description = """
        Detects synchronous HTTP/REST calls that can block request processing.
        Consider async calls, caching, or moving to background jobs.
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
        val visitor = HttpCallsVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") || path.contains("\\test\\")
    }
}

private class HttpCallsVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    private val httpMethodNames = setOf(
        "execute", "get", "post", "put", "delete", "patch",
        "sendGet", "sendPost", "doGet", "doPost"
    )

    private val httpClientTypes = setOf(
        "HttpClient", "RestTemplate", "WebClient",
        "URLConnection", "HttpURLConnection"
    )

    override fun visit(methodCall: MethodCallExpr, ctx: InspectionContext) {
        super.visit(methodCall, ctx)

        val methodName = methodCall.nameAsString
        if (methodName !in httpMethodNames) {
            return
        }

        val scope = methodCall.scope.orElse(null)?.toString() ?: ""
        val isHttpCall = httpClientTypes.any { scope.contains(it) } ||
                        scope.contains("client") ||
                        scope.contains("rest")

        if (isHttpCall) {
            issues.add(createHttpCallIssue(methodCall))
        }
    }

    override fun visit(objectCreation: ObjectCreationExpr, ctx: InspectionContext) {
        super.visit(objectCreation, ctx)

        val typeName = objectCreation.typeAsString
        if (typeName.contains("URL") && typeName.contains("Connection")) {
            issues.add(createHttpClientCreationIssue(objectCreation))
        }
    }

    private fun createHttpCallIssue(methodCall: MethodCallExpr): InspectionIssue {
        val range = methodCall.name.range.map { r ->
            TextRange(r.begin.line, r.begin.column, r.end.line, r.end.column)
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "Synchronous HTTP call blocks request thread - consider async or caching",
            description = "HTTP calls in components block request processing. Use async patterns or cache results.",
            range = range
        )
    }

    private fun createHttpClientCreationIssue(objectCreation: ObjectCreationExpr): InspectionIssue {
        val range = objectCreation.range.map { r ->
            TextRange(r.begin.line, r.begin.column, r.end.line, r.end.column)
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "URLConnection created - HTTP calls block request threads",
            description = "Creating HTTP connections in components can cause performance issues.",
            range = range
        )
    }
}
