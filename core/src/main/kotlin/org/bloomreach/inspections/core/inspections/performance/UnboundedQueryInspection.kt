package org.bloomreach.inspections.core.inspections.performance

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects JCR queries without setLimit() calls.
 *
 * This is a common performance issue (15% of community problems).
 *
 * Problem: Unbounded queries can return thousands of results, causing:
 * - Memory exhaustion (OutOfMemoryError)
 * - Slow response times
 * - Database overload
 * - Application crashes
 *
 * Best practice: Always set a reasonable limit on queries (typically 100-1000).
 */
class UnboundedQueryInspection : Inspection() {
    override val id = "performance.unbounded-query"
    override val name = "Unbounded JCR Query"
    override val description = """
        Detects JCR queries without limits that may cause performance issues.
        Large result sets can exhaust memory and degrade performance.
    """.trimIndent()
    override val category = InspectionCategory.PERFORMANCE
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = QueryVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        val defaultLimit = issue.metadata["suggestedLimit"] as? Int ?: 100
        return listOf(
            AddQueryLimitQuickFix(defaultLimit)
        )
    }
}

/**
 * Visitor that detects unbounded queries
 */
private class QueryVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    // Track query variables and whether they have limits
    private val queryVariables = mutableMapOf<String, QueryInfo>()

    // Known methods that create JCR queries
    private val queryCreationMethods = setOf(
        "createQuery",
        "getQuery",
        "executeQuery"
    )

    // Known methods that execute queries
    private val queryExecutionMethods = setOf(
        "execute",
        "getResultIterator",
        "getResult"
    )

    override fun visit(method: MethodDeclaration, ctx: InspectionContext) {
        // Clear tracking for each method
        queryVariables.clear()

        // Visit method body
        super.visit(method, ctx)

        // Check all queries found in this method
        queryVariables.values.forEach { queryInfo ->
            if (!queryInfo.hasLimit && queryInfo.isExecuted) {
                issues.add(createUnboundedQueryIssue(queryInfo))
            }
        }
    }

    override fun visit(variable: VariableDeclarator, ctx: InspectionContext) {
        super.visit(variable, ctx)

        // Check if this variable is assigned a query
        variable.initializer.ifPresent { init ->
            if (isQueryCreation(init)) {
                queryVariables[variable.nameAsString] = QueryInfo(
                    variableName = variable.nameAsString,
                    declarator = variable,
                    creationExpression = init,
                    hasLimit = false,
                    isExecuted = false
                )
            }
        }
    }

    override fun visit(call: MethodCallExpr, ctx: InspectionContext) {
        super.visit(call, ctx)

        // Check if this is a setLimit call on a query
        if (call.nameAsString == "setLimit") {
            call.scope.ifPresent { scope ->
                val queryVar = extractVariableName(scope)
                if (queryVar != null && queryVariables.containsKey(queryVar)) {
                    queryVariables[queryVar]?.hasLimit = true
                }
            }
        }

        // Check if this is an execution call
        if (call.nameAsString in queryExecutionMethods) {
            call.scope.ifPresent { scope ->
                val queryVar = extractVariableName(scope)
                if (queryVar != null && queryVariables.containsKey(queryVar)) {
                    queryVariables[queryVar]?.isExecuted = true
                }
            }
        }

        // Check for inline query execution (without variable)
        if (call.nameAsString in queryExecutionMethods) {
            call.scope.ifPresent { scope ->
                if (isQueryCreation(scope)) {
                    // Inline query: queryManager.createQuery(...).execute()
                    // This is unbounded
                    issues.add(createInlineQueryIssue(call))
                }
            }
        }
    }

    private fun isQueryCreation(expression: Expression): Boolean {
        return when (expression) {
            is MethodCallExpr -> expression.nameAsString in queryCreationMethods
            else -> false
        }
    }

    private fun extractVariableName(expression: Expression): String? {
        return expression.toString().split('.').firstOrNull()
    }

    private fun createUnboundedQueryIssue(queryInfo: QueryInfo): InspectionIssue {
        val range = queryInfo.declarator.range.map { r ->
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
            message = "Query '${queryInfo.variableName}' executed without setLimit()",
            description = """
                The JCR query '${queryInfo.variableName}' is executed without calling setLimit().
                This can cause serious performance issues if the result set is large.

                **Impact**: HIGH - Can cause OutOfMemoryError and application crashes

                **Symptoms**:
                - Slow page load times
                - High memory consumption
                - Database connection exhaustion
                - Application becomes unresponsive

                **Example Problem**:
                ```java
                Query query = queryManager.createQuery(
                    "SELECT * FROM [hippostd:folder]", Query.JCR_SQL2);
                QueryResult result = query.execute(); // ⚠️ No limit!
                // Could return 10,000+ nodes → OutOfMemoryError
                ```

                **Correct Usage**:
                ```java
                Query query = queryManager.createQuery(
                    "SELECT * FROM [hippostd:folder]", Query.JCR_SQL2);
                query.setLimit(100);  // ✓ Always set a limit
                QueryResult result = query.execute();
                ```

                **Recommended Limits**:
                - List pages: 20-50 items
                - Search results: 100-500 items
                - Bulk operations: 1000 max (with pagination)
                - Admin operations: Consider if unbounded is truly needed

                **Alternative: Pagination**:
                ```java
                long offset = 0;
                long pageSize = 100;

                while (true) {
                    query.setOffset(offset);
                    query.setLimit(pageSize);
                    QueryResult result = query.execute();

                    // Process results...

                    if (result.getNodes().getSize() < pageSize) {
                        break; // Last page
                    }
                    offset += pageSize;
                }
                ```

                **Performance Tips**:
                - Use specific node types in queries (not [nt:base])
                - Add WHERE clauses to filter results
                - Ensure indexed properties are used in WHERE clauses
                - Consider using Lucene/Solr for complex searches

                **Related Community Issues**:
                - [Site brought down by indexing error](https://community.bloomreach.com/t/site-brought-down-potentially-by-indexing-error/2437)
                - OutOfMemoryError from large query results
                - Slow performance with faceted navigation

                **References**:
                - [JCR Query Best Practices](https://xmdocumentation.bloomreach.com/)
                - [Performance Optimization Guide](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "variableName" to queryInfo.variableName,
                "suggestedLimit" to 100,
                "queryType" to "variable"
            )
        )
    }

    private fun createInlineQueryIssue(call: MethodCallExpr): InspectionIssue {
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
            message = "Inline query executed without limit",
            description = """
                An inline JCR query is executed without setLimit().

                **Problem**: The query is created and executed in a single expression chain,
                making it impossible to set a limit.

                **Example**:
                ```java
                // ⚠️ PROBLEM - Inline query without limit
                QueryResult result = queryManager
                    .createQuery("SELECT * FROM [nt:base]", Query.JCR_SQL2)
                    .execute();
                ```

                **Solution**: Assign to a variable and set limit:
                ```java
                Query query = queryManager.createQuery(
                    "SELECT * FROM [nt:base]", Query.JCR_SQL2);
                query.setLimit(100);
                QueryResult result = query.execute();
                ```

                See the main unbounded query documentation for more details.
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "suggestedLimit" to 100,
                "queryType" to "inline"
            )
        )
    }
}

/**
 * Information about a query variable
 */
private data class QueryInfo(
    val variableName: String,
    val declarator: VariableDeclarator,
    val creationExpression: Expression,
    var hasLimit: Boolean,
    var isExecuted: Boolean
)

/**
 * Quick fix: Add query.setLimit() call
 */
private class AddQueryLimitQuickFix(
    private val defaultLimit: Int
) : BaseQuickFix(
    name = "Add query.setLimit($defaultLimit)",
    description = "Adds a setLimit($defaultLimit) call after query creation"
) {
    override fun apply(context: QuickFixContext) {
        val queryVariable = context.issue.metadata["queryVariable"] as? String ?: "query"

        val content = context.file.readText()
        val lines = content.split("\n").toMutableList()

        // Find the line with query creation
        val queryLine = context.range.startLine - 1
        if (queryLine < 0 || queryLine >= lines.size) return

        val line = lines[queryLine]
        val indent = line.takeWhile { it.isWhitespace() }

        // Insert setLimit call after query creation
        val setLimitCall = "$indent$queryVariable.setLimit($defaultLimit);"

        lines.add(queryLine + 1, setLimitCall)

        // Write back to file
        val newContent = lines.joinToString("\n")
        java.nio.file.Files.writeString(context.file.path, newContent)
    }
}
