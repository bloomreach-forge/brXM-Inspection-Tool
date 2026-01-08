package org.bloomreach.inspections.core.inspections.performance

import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.ForEachStmt
import com.github.javaparser.ast.stmt.ForStmt
import com.github.javaparser.ast.stmt.WhileStmt
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects N+1 query patterns where queries are executed inside loops.
 *
 * N+1 query pattern: 1 query to get a list + N queries for each item = N+1 queries total.
 * This causes severe performance degradation as the list grows.
 *
 * From community analysis: N+1 queries are a common performance bottleneck in content-heavy applications.
 */
class N1QueryPatternInspection : Inspection() {
    override val id = "performance.n-plus-1-query"
    override val name = "N+1 Query Pattern"
    override val description = """
        Detects N+1 query patterns where queries are executed inside loops.

        N+1 query pattern occurs when:
        1. Load a list of items with one query
        2. For each item, execute another query
        3. Result: 1 + N queries instead of 1 or 2

        Example: Loading 100 documents with related content results in 101 queries!

        This inspection checks for:
        - JCR query methods inside loops (getNode, createQuery, execute)
        - Content bean access methods in loops (getLinkedBean, getChildBeans)
        - Repository access inside forEach operations
        - Query execution within while loops
    """.trimIndent()
    override val category = InspectionCategory.PERFORMANCE
    override val severity = Severity.WARNING
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
        val visitor = N1QueryVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/src/test/") || path.contains("\\src\\test\\")
    }

    fun buildDescription(queryMethod: String, loopType: String): String {
        return """
            N+1 query pattern detected: $queryMethod() called inside $loopType loop.

            **Performance Risk**: CRITICAL

            **What Can Happen**:
            - **Severe Performance Degradation**: 100 items = 100+ queries
            - **Database Overload**: Excessive connections and query load
            - **Slow Page Loads**: Users experience significant delays
            - **Resource Exhaustion**: Memory and CPU consumed by repeated queries
            - **Scalability Issues**: Performance degrades linearly with data volume

            **Example of the Problem**:
            ```java
            // BAD: N+1 query pattern
            List<Node> documents = getDocuments(); // 1 query
            for (Node doc : documents) {
                // N queries (one per document!)
                Node related = session.getNode(doc.getProperty("relatedPath"));
                processRelated(related);
            }
            // Total: 1 + N queries
            ```

            **How to Fix**:

            **1. Batch Load Related Content**:
            ```java
            // GOOD: Batch loading
            List<Node> documents = getDocuments(); // 1 query

            // Collect all related paths first
            Set<String> relatedPaths = new HashSet<>();
            for (Node doc : documents) {
                relatedPaths.add(doc.getProperty("relatedPath"));
            }

            // Load all related nodes in one query
            Map<String, Node> relatedNodes = batchLoadNodes(relatedPaths); // 1 query

            // Now iterate without additional queries
            for (Node doc : documents) {
                String path = doc.getProperty("relatedPath");
                Node related = relatedNodes.get(path);
                processRelated(related);
            }
            // Total: 2 queries (constant)
            ```

            **2. Use JCR Query Joins**:
            ```java
            // GOOD: Single query with join
            String query = "SELECT doc.*, related.* " +
                          "FROM [nt:unstructured] AS doc " +
                          "LEFT OUTER JOIN [nt:unstructured] AS related " +
                          "ON doc.[relatedPath] = related.[jcr:path] " +
                          "WHERE ISDESCENDANTNODE(doc, '/content/documents')";

            QueryResult result = qm.createQuery(query, Query.JCR_SQL2).execute();
            // Process results - all data loaded in 1 query
            ```

            **3. Use Content Bean Batch Methods**:
            ```java
            // BAD: N+1 with content beans
            for (MyDocument doc : documents) {
                RelatedDocument related = doc.getRelated(); // N queries
            }

            // GOOD: Batch bean loading
            List<String> relatedIds = documents.stream()
                .map(MyDocument::getRelatedId)
                .collect(Collectors.toList());

            Map<String, RelatedDocument> relatedDocs =
                beanContext.getBeans(relatedIds, RelatedDocument.class); // 1 query

            for (MyDocument doc : documents) {
                RelatedDocument related = relatedDocs.get(doc.getRelatedId());
            }
            ```

            **4. Fetch Associations Eagerly**:
            ```java
            // GOOD: Eager loading with custom query
            String query = "SELECT * FROM [hippostd:publishable] " +
                          "WHERE ISDESCENDANTNODE('/content/documents')";

            QueryManager qm = session.getWorkspace().getQueryManager();
            Query q = qm.createQuery(query, Query.JCR_SQL2);

            // Load related nodes upfront
            NodeIterator nodes = q.execute().getNodes();
            while (nodes.hasNext()) {
                Node node = nodes.nextNode();
                // Prefetch related nodes
                if (node.hasProperty("relatedPath")) {
                    String path = node.getProperty("relatedPath").getString();
                    // Cache or store for later use
                }
            }
            ```

            **5. Use Caching Strategically**:
            ```java
            // GOOD: Cache repeated queries
            private final Map<String, Node> nodeCache = new ConcurrentHashMap<>();

            public Node getNodeCached(String path) throws RepositoryException {
                return nodeCache.computeIfAbsent(path, p -> {
                    try {
                        return session.getNode(p);
                    } catch (RepositoryException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            // Use in loop
            for (Node doc : documents) {
                String path = doc.getProperty("relatedPath");
                Node related = getNodeCached(path); // Cached access
            }
            ```

            **6. Optimize HST Content Bean Access**:
            ```java
            // BAD: Lazy loading in loop
            for (NewsDocument news : newsList) {
                AuthorBean author = news.getAuthor(); // N queries
                model.addAuthor(author);
            }

            // GOOD: Eager loading
            HstRequestContext requestContext = getRequestContext();
            ContentBeansTool beansTool = requestContext.getContentBeansTool();

            // Get all author paths first
            List<String> authorPaths = newsList.stream()
                .map(NewsDocument::getAuthorPath)
                .collect(Collectors.toList());

            // Batch load authors
            List<AuthorBean> authors = authorPaths.stream()
                .map(path -> beansTool.getBeanByPath(path, AuthorBean.class))
                .collect(Collectors.toList());

            // Map to news items without additional queries
            ```

            **Best Practices**:
            - Profile query patterns during development
            - Use batch loading for related content
            - Leverage JCR query joins when possible
            - Implement caching for frequently accessed nodes
            - Monitor query counts in production
            - Use eager loading for known associations
            - Consider denormalization for critical paths
            - Test performance with realistic data volumes

            **Detection and Monitoring**:
            - Enable JCR query logging
            - Use database query profilers
            - Monitor repository session metrics
            - Track response times by endpoint
            - Set alerts for query count thresholds

            **References**:
            - N+1 Select Problem: https://stackoverflow.com/questions/97197/what-is-the-n1-selects-problem
            - JCR Query Documentation: https://jackrabbit.apache.org/jcr/jcr-query.html
            - Bloomreach Performance Guide: https://xmdocumentation.bloomreach.com/
        """.trimIndent()
    }
}

private class N1QueryVisitor(
    private val inspection: N1QueryPatternInspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    // JCR query methods
    private val queryMethods = setOf(
        "getNode",
        "getNodes",
        "createQuery",
        "execute",
        "getNodeByIdentifier",
        "getNodeByUUID"
    )

    // Content bean query methods
    private val beanQueryMethods = setOf(
        "getLinkedBean",
        "getLinkedBeans",
        "getChildBeans",
        "getBean",
        "getBeans",
        "getObjectBeanManager"
    )

    // Track if we're inside a loop
    private var loopDepth = 0
    private var currentLoopType: String? = null

    override fun visit(forStmt: ForStmt, ctx: InspectionContext) {
        loopDepth++
        currentLoopType = "for"
        super.visit(forStmt, ctx)
        loopDepth--
        if (loopDepth == 0) {
            currentLoopType = null
        }
    }

    override fun visit(forEachStmt: ForEachStmt, ctx: InspectionContext) {
        loopDepth++
        currentLoopType = "forEach"
        super.visit(forEachStmt, ctx)
        loopDepth--
        if (loopDepth == 0) {
            currentLoopType = null
        }
    }

    override fun visit(whileStmt: WhileStmt, ctx: InspectionContext) {
        loopDepth++
        currentLoopType = "while"
        super.visit(whileStmt, ctx)
        loopDepth--
        if (loopDepth == 0) {
            currentLoopType = null
        }
    }

    override fun visit(call: MethodCallExpr, ctx: InspectionContext) {
        super.visit(call, ctx)

        val methodName = call.nameAsString

        // Check if we're in a loop and calling a query method
        if (loopDepth > 0 && (queryMethods.contains(methodName) || beanQueryMethods.contains(methodName))) {
            val line = call.begin.map { it.line }.orElse(0)
            val loopType = currentLoopType ?: "loop"

            issues.add(
                InspectionIssue(
                    inspection = inspection,
                    file = context.file,
                    severity = inspection.severity,
                    message = "N+1 query pattern: $methodName() called inside $loopType loop",
                    description = inspection.buildDescription(methodName, loopType),
                    range = TextRange.wholeLine(line),
                    metadata = mapOf(
                        "queryMethod" to methodName,
                        "loopType" to loopType,
                        "loopDepth" to loopDepth,
                        "line" to line
                    )
                )
            )
        }
    }
}
