package org.bloomreach.inspections.core.inspections.performance

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import org.bloomreach.inspections.core.engine.*

/**
 * Detects JCR queries that may be missing indexes.
 *
 * Queries on properties without indexes can cause severe performance issues,
 * especially on large repositories. This inspection detects common patterns
 * that indicate missing indexes.
 *
 * From community analysis: 35% of performance issues are caused by missing indexes.
 */
class MissingIndexInspection : Inspection() {

    override val id: String = "performance.missing-index"

    override val name: String = "Potential Missing Index"

    override val description: String = """
        Detects JCR queries that may suffer from missing indexes.

        JCR queries perform best when:
        1. Properties used in WHERE clauses are indexed
        2. Properties used in ORDER BY clauses are indexed
        3. Full-text searches use proper indexes

        This inspection detects:
        - ORDER BY on non-standard properties
        - WHERE clauses on custom properties
        - Queries on large node types without indexes

        To fix:
        1. Add index definition in CND file
        2. Use built-in indexed properties when possible
        3. Consider full-text index for text searches
    """.trimIndent()

    override val category: InspectionCategory = InspectionCategory.PERFORMANCE

    override val severity: Severity = Severity.INFO

    override val applicableFileTypes: Set<FileType> = setOf(FileType.JAVA)

    // Properties that are typically indexed by default
    private val standardIndexedProperties = setOf(
        "jcr:primaryType",
        "jcr:mixinTypes",
        "jcr:uuid",
        "jcr:created",
        "jcr:createdBy",
        "jcr:lastModified",
        "jcr:lastModifiedBy",
        "hippostd:state",
        "hippostdpubwf:publicationDate",
        "hippostdpubwf:lastModificationDate"
    )

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val cu = JavaParser().parse(context.fileContent).result.orElse(null) ?: return emptyList()

            // Find all createQuery calls
            cu.findAll(MethodCallExpr::class.java).forEach { call ->
                if (call.nameAsString == "createQuery" && call.arguments.isNotEmpty()) {
                    val firstArg = call.arguments[0]
                    if (firstArg is StringLiteralExpr) {
                        val query = firstArg.value
                        analyzeQuery(query, call, context, issues)
                    }
                }
            }
        } catch (e: Exception) {
            // Parsing failed, skip this file
        }

        return issues
    }

    private fun analyzeQuery(
        query: String,
        call: MethodCallExpr,
        context: InspectionContext,
        issues: MutableList<InspectionIssue>
    ) {
        val line = call.begin.map { it.line }.orElse(0)

        // Check for ORDER BY on custom properties
        val orderByPattern = Regex("""ORDER\s+BY\s+(\w+:\w+|\w+)""", RegexOption.IGNORE_CASE)
        orderByPattern.findAll(query).forEach { match ->
            val property = match.groupValues[1]
            if (property !in standardIndexedProperties && !property.contains('(')) {
                issues.add(
                    InspectionIssue(
                        inspection = this@MissingIndexInspection,
                        file = context.file,
                        severity = severity,
                        message = "ORDER BY on potentially unindexed property: $property",
                        description = buildOrderByDescription(property, query),
                        range = TextRange(line, 0, line, 0)
                    )
                )
            }
        }

        // Check for WHERE clauses on custom properties
        val wherePattern = Regex("""WHERE\s+(\w+:\w+|\w+)\s*[=<>!]""", RegexOption.IGNORE_CASE)
        wherePattern.findAll(query).forEach { match ->
            val property = match.groupValues[1]
            if (property !in standardIndexedProperties &&
                !property.startsWith("@") &&  // Exclude XPath attributes
                property != "element" &&  // Exclude XPath functions
                property.count { it == ':' } <= 1) {  // Basic property check

                issues.add(
                    InspectionIssue(
                        inspection = this@MissingIndexInspection,
                        file = context.file,
                        severity = severity,
                        message = "WHERE clause on potentially unindexed property: $property",
                        description = buildWhereDescription(property, query),
                        range = TextRange(line, 0, line, 0)
                    )
                )
            }
        }

        // Check for complex queries without apparent optimization
        if (query.contains("ORDER BY", ignoreCase = true) &&
            query.contains("WHERE", ignoreCase = true) &&
            query.length > 100) {

            val hasLimit = context.file.readText()
                .let { content ->
                    val callLine = content.split("\n")[line - 1]
                    // Look ahead a few lines for setLimit call
                    val startIndex = maxOf(0, line - 5)
                    val endIndex = minOf(content.split("\n").size, line + 5)
                    content.split("\n")
                        .subList(startIndex, endIndex)
                        .any { it.contains("setLimit") }
                }

            if (!hasLimit) {
                issues.add(
                    InspectionIssue(
                        inspection = this@MissingIndexInspection,
                        file = context.file,
                        severity = Severity.WARNING,
                        message = "Complex query without limit may have performance issues",
                        description = buildComplexQueryDescription(query),
                        range = TextRange(line, 0, line, 0)
                    )
                )
            }
        }
    }

    private fun buildOrderByDescription(property: String, query: String): String {
        return """
            Query uses ORDER BY on property '$property' which may not be indexed.

            ORDER BY on unindexed properties can cause severe performance degradation,
            especially on large result sets. The repository must sort all matching
            documents in memory, which can:
            - Consume excessive memory
            - Cause slow query execution
            - Impact overall system performance

            Solutions:

            Option 1: Add index in CND file (Recommended)
            ```cnd
            [myproject:mydoctype] > hippo:document
              - $property (string) indexed
            ```

            Option 2: Use a pre-indexed property
            Consider using standard properties like:
            - hippostdpubwf:publicationDate (for date sorting)
            - hippostd:state (for state filtering)
            - jcr:created / jcr:lastModified (for chronological sorting)

            Option 3: Add compound index for multiple properties
            ```cnd
            [myproject:mydoctype] > hippo:document
              - $property (string) indexed
              + hippostd:indexed (hipposys:indexed)
                - hipposys:indexedFields multiple (string) = '$property'
            ```

            Query: $query
        """.trimIndent()
    }

    private fun buildWhereDescription(property: String, query: String): String {
        return """
            Query filters on property '$property' which may not be indexed.

            WHERE clauses on unindexed properties force the repository to scan
            all nodes of the specified type, checking each one individually.
            This is extremely slow on repositories with many documents.

            Solutions:

            Option 1: Add index in CND file
            ```cnd
            [myproject:mydoctype] > hippo:document
              - $property (string) indexed
            ```

            Option 2: Use full-text search for text properties
            ```java
            // Instead of: WHERE $property = 'value'
            // Use: WHERE CONTAINS($property, 'value')
            ```

            Option 3: Reconsider query design
            - Can you use a different, indexed property?
            - Can you filter in application code instead?
            - Can you use hierarchical structure instead of queries?

            Query: $query
        """.trimIndent()
    }

    private fun buildComplexQueryDescription(query: String): String {
        return """
            Complex query with both WHERE and ORDER BY clauses detected.

            Complex queries are most sensitive to missing indexes. This query
            combines filtering and sorting, which multiplies the performance impact
            of missing indexes.

            Recommendations:

            1. Ensure all properties in WHERE and ORDER BY clauses are indexed
            2. Add setLimit() to prevent returning too many results
            3. Consider pagination for large result sets
            4. Test query performance on production-sized data

            Example with limit:
            ```java
            Query query = queryManager.createQuery(statement, Query.JCR_SQL2);
            query.setLimit(100);  // Add this!
            QueryResult result = query.execute();
            ```

            Query: $query
        """.trimIndent()
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(AddIndexCommentQuickFix())
    }
}

/**
 * Quick fix that adds a TODO comment about adding an index
 */
class AddIndexCommentQuickFix : BaseQuickFix(
    name = "Add TODO for index",
    description = "Adds a TODO comment to create an index for the property"
) {
    override fun apply(context: QuickFixContext) {
        val content = context.file.readText()
        val lines = content.split("\n").toMutableList()

        val lineIndex = context.range.startLine - 1
        if (lineIndex >= 0 && lineIndex < lines.size) {
            val line = lines[lineIndex]
            val indent = line.takeWhile { it.isWhitespace() }

            // Extract property name from message
            val propertyPattern = Regex("""property: (\w+:\w+|\w+)""")
            val match = propertyPattern.find(context.issue.message)
            val property = match?.groupValues?.get(1) ?: "property"

            val comment = "${indent}// TODO: Add index for property '$property' in CND file"
            lines.add(lineIndex, comment)

            val newContent = lines.joinToString("\n")
            java.nio.file.Files.writeString(context.file.path, newContent)
        }
    }
}
