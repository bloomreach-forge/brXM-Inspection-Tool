package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects thread safety issues in HST components.
 *
 * HST components are singletons shared across multiple requests.
 * Non-static instance fields can cause race conditions (15% of concurrency bugs).
 *
 * Common issues:
 * - Instance fields storing request-specific data
 * - Mutable state shared across requests
 * - Collections without synchronization
 */
class HstComponentThreadSafetyInspection : Inspection() {
    override val id = "config.hst-component-thread-safety"
    override val name = "HST Component Thread Safety Issues"
    override val description = """
        Detects non-static fields in HST components that can cause thread safety issues.
        HST components are singletons - instance fields are shared across all requests.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
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
        val visitor = ThreadSafetyVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") ||
               path.contains("\\test\\") ||
               path.endsWith("test.java") ||
               path.endsWith("test.kt")
    }
}

/**
 * Visitor for detecting thread safety issues in HST components
 */
private class ThreadSafetyVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(classDecl: ClassOrInterfaceDeclaration, ctx: InspectionContext) {
        super.visit(classDecl, ctx)

        // Skip interfaces and abstract classes
        if (classDecl.isInterface || classDecl.isAbstract) {
            return
        }

        // Check if this is an HST component
        val isHstComponent = classDecl.extendedTypes.any {
            val typeName = it.nameAsString
            typeName.contains("Component") ||
            typeName == "BaseHstComponent" ||
            typeName == "SimpleHstComponent" ||
            typeName == "CommonComponent"
        }

        if (!isHstComponent) {
            return
        }

        // Check all fields for thread safety issues
        classDecl.fields.forEach { field ->
            checkFieldThreadSafety(field, classDecl)
        }
    }

    private fun checkFieldThreadSafety(field: FieldDeclaration, classDecl: ClassOrInterfaceDeclaration) {
        // Skip static fields (they're class-level, not per-instance)
        if (field.isStatic) {
            return
        }

        // Skip final fields with immutable types (these are safe)
        if (field.isFinal && isImmutableType(field.commonType.asString())) {
            return
        }

        // Skip logger fields (these are typically thread-safe)
        val isLogger = field.variables.any { variable ->
            val varName = variable.nameAsString.lowercase()
            varName == "log" || varName == "logger" ||
            variable.typeAsString.contains("Logger")
        }

        if (isLogger) {
            return
        }

        // Flag the field as a thread safety issue
        issues.add(createThreadSafetyIssue(field, classDecl))
    }

    private fun isImmutableType(typeName: String): Boolean {
        // Common immutable types
        return typeName in setOf(
            "String", "Integer", "Long", "Boolean", "Double", "Float",
            "Byte", "Short", "Character", "BigDecimal", "BigInteger",
            "LocalDate", "LocalDateTime", "Instant", "ZonedDateTime"
        )
    }

    private fun createThreadSafetyIssue(
        field: FieldDeclaration,
        classDecl: ClassOrInterfaceDeclaration
    ): InspectionIssue {
        val fieldName = field.variables.firstOrNull()?.nameAsString ?: "field"
        val fieldType = field.commonType.asString()

        val range = field.variables.firstOrNull()?.name?.range?.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }?.orElse(TextRange.wholeLine(1)) ?: TextRange.wholeLine(1)

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.WARNING,
            message = "Non-static field '$fieldName' in HST component '${classDecl.nameAsString}' - potential thread safety issue",
            description = """
                HST components are singletons shared across all HTTP requests.
                Instance fields are NOT thread-safe and can cause race conditions.

                **Problem**: Field '$fieldName' ($fieldType) is shared across all requests.

                **Risk**:
                - Data from one user's request can leak into another user's request
                - Race conditions cause intermittent bugs
                - Production-only issues that don't appear in testing

                **Fix Options**:

                **Option 1: Use Method Parameters (RECOMMENDED)**
                ```java
                // L WRONG - Instance field
                private String title;

                @Override
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    this.title = getParameter("title"); // RACE CONDITION!
                    request.setAttribute("title", this.title);
                }

                //  CORRECT - Local variable
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    String title = getParameter("title"); // Safe!
                    request.setAttribute("title", title);
                }
                ```

                **Option 2: Make Static Final (Constants Only)**
                ```java
                //  Safe for constants
                private static final String DEFAULT_TITLE = "Welcome";
                private static final int MAX_RESULTS = 10;
                ```

                **Option 3: Use Request Attributes**
                ```java
                // Store in request scope instead
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    request.setAttribute("title", getParameter("title"));
                    // Each request has its own attributes
                }
                ```

                **Why This Happens**:
                - HST creates ONE instance of each component
                - That instance handles ALL requests from ALL users
                - Instance fields = shared memory = race conditions

                **Real-World Example of Bug**:
                ```java
                // Component handles 1000 req/sec
                private List<Product> products;

                public void doBeforeRender(...) {
                    products = fetchProducts(userId); // User A's products
                    // Context switch to another thread...
                    // products = fetchProducts(userId); // User B overwrites!
                    request.setAttribute("products", products); // User A sees User B's data!
                }
                ```

                **When Fields ARE Allowed**:
                - `private static final` constants
                - `private final Logger` logger instances
                - `private final` Spring beans (if thread-safe themselves)

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/thread-safety.html
            """.trimIndent(),
            range = range
        )
    }
}
