package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects improper HST component lifecycle implementations in Bloomreach CMS.
 *
 * HST components must follow specific lifecycle patterns (10% of config issues):
 * - Override doBeforeRender() correctly
 * - Call super.doBeforeRender()
 * - Handle exceptions properly
 * - Set request attributes for templates
 * - Use proper method signatures
 */
class HstComponentLifecycleInspection : Inspection() {
    override val id = "config.hst-component-lifecycle"
    override val name = "HST Component Lifecycle Issues"
    override val description = """
        Detects improper HST component lifecycle implementations.
        Components must override doBeforeRender correctly and call super methods.
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
        val visitor = HstComponentVisitor(this, context)

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
 * Visitor for detecting HST component lifecycle issues
 */
private class HstComponentVisitor(
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
            it.nameAsString.contains("Component") ||
            it.nameAsString == "BaseHstComponent" ||
            it.nameAsString == "SimpleHstComponent" ||
            it.nameAsString == "CommonComponent"
        }

        if (!isHstComponent) {
            return
        }

        // Check for doBeforeRender method
        val doBeforeRenderMethods = classDecl.methods.filter {
            it.nameAsString == "doBeforeRender"
        }

        doBeforeRenderMethods.forEach { method ->
            checkDoBeforeRenderMethod(method, classDecl)
        }

        // If no doBeforeRender, check if this component actually does anything
        if (doBeforeRenderMethods.isEmpty()) {
            val hasMeaningfulMethods = classDecl.methods.any {
                !it.nameAsString.startsWith("get") &&
                !it.nameAsString.startsWith("set") &&
                !it.nameAsString.startsWith("is")
            }

            if (!hasMeaningfulMethods) {
                issues.add(createEmptyComponentIssue(classDecl))
            }
        }
    }

    private fun checkDoBeforeRenderMethod(method: MethodDeclaration, classDecl: ClassOrInterfaceDeclaration) {
        // Check method signature
        val hasCorrectSignature = method.parameters.size == 2 &&
                                  method.parameters[0].typeAsString.contains("HstRequest") &&
                                  method.parameters[1].typeAsString.contains("HstResponse")

        if (!hasCorrectSignature) {
            issues.add(createInvalidSignatureIssue(method, classDecl))
            return
        }

        // Check if method body exists
        if (!method.body.isPresent) {
            return
        }

        val body = method.body.get()

        // Check for super.doBeforeRender() call
        val callsSuperDoBeforeRender = body.findAll(MethodCallExpr::class.java).any { call ->
            call.nameAsString == "doBeforeRender" &&
            call.scope.map { it.toString() == "super" }.orElse(false)
        }

        if (!callsSuperDoBeforeRender) {
            issues.add(createMissingSuperCallIssue(method, classDecl))
        }

        // Check if exceptions are properly handled
        val throwsExceptions = method.thrownExceptions.isNotEmpty()
        if (!throwsExceptions) {
            issues.add(createMissingExceptionHandlingIssue(method, classDecl))
        }

        // Check if request attributes are being set
        val setsRequestAttributes = body.findAll(MethodCallExpr::class.java).any { call ->
            call.nameAsString == "setAttribute" &&
            call.scope.map { scope ->
                val scopeStr = scope.toString()
                scopeStr == "request" || scopeStr.contains("HstRequest")
            }.orElse(false)
        }

        if (!setsRequestAttributes) {
            issues.add(createNoRequestAttributesIssue(method, classDecl))
        }
    }

    private fun createInvalidSignatureIssue(
        method: MethodDeclaration,
        classDecl: ClassOrInterfaceDeclaration
    ): InspectionIssue {
        val range = method.name.range.map { r ->
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
            severity = Severity.ERROR,
            message = "doBeforeRender has incorrect signature in ${classDecl.nameAsString}",
            description = """
                HST Component doBeforeRender must have specific parameters.

                **Problem**: Method signature doesn't match HST requirements.

                **Fix**: Use correct signature:
                ```java
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response)
                        throws HstComponentException {
                    super.doBeforeRender(request, response);

                    // Your component logic here
                }
                ```

                **Required**:
                - Return type: `void`
                - Parameter 1: `HstRequest request`
                - Parameter 2: `HstResponse response`
                - Throws: `HstComponentException` (or compatible)

                **Why This Matters**:
                - HST framework calls this method during request processing
                - Wrong signature means method won't be called
                - Your component logic will never execute

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/
            """.trimIndent(),
            range = range
        )
    }

    private fun createMissingSuperCallIssue(
        method: MethodDeclaration,
        classDecl: ClassOrInterfaceDeclaration
    ): InspectionIssue {
        val range = method.name.range.map { r ->
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
            severity = Severity.WARNING,
            message = "Missing super.doBeforeRender() call in ${classDecl.nameAsString}",
            description = """
                HST components should call super.doBeforeRender() to ensure proper initialization.

                **Problem**: doBeforeRender doesn't call parent implementation.

                **Fix**: Add super call as first statement:
                ```java
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response)
                        throws HstComponentException {
                    //  Call super first
                    super.doBeforeRender(request, response);

                    // Then your logic
                    String title = getComponentParameter("title");
                    request.setAttribute("title", title);
                }
                ```

                **Why This Matters**:
                - Parent class may perform essential initialization
                - BaseHstComponent sets up common attributes
                - Skipping super call can cause subtle bugs

                **When It's OK to Skip**:
                - If you extend BaseHstComponent and override everything
                - If explicitly documented by your base class

                **Best Practice**: Always call super unless you have a good reason not to.

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/component-lifecycle.html
            """.trimIndent(),
            range = range
        )
    }

    private fun createMissingExceptionHandlingIssue(
        method: MethodDeclaration,
        classDecl: ClassOrInterfaceDeclaration
    ): InspectionIssue {
        val range = method.name.range.map { r ->
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
            severity = Severity.INFO,
            message = "doBeforeRender should declare throws HstComponentException",
            description = """
                HST component methods should declare HstComponentException for proper error handling.

                **Problem**: doBeforeRender doesn't declare throws clause.

                **Fix**: Add throws declaration:
                ```java
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response)
                        throws HstComponentException {
                    super.doBeforeRender(request, response);

                    try {
                        // Your logic that might fail
                        String data = fetchData();
                        request.setAttribute("data", data);
                    } catch (Exception e) {
                        throw new HstComponentException("Failed to fetch data", e);
                    }
                }
                ```

                **Why This Matters**:
                - HST can handle HstComponentException gracefully
                - Proper exception propagation for error pages
                - Better logging and debugging

                **Best Practice**:
                - Wrap checked exceptions in HstComponentException
                - Log errors before throwing
                - Provide meaningful error messages

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/error-handling.html
            """.trimIndent(),
            range = range
        )
    }

    private fun createNoRequestAttributesIssue(
        method: MethodDeclaration,
        classDecl: ClassOrInterfaceDeclaration
    ): InspectionIssue {
        val range = method.name.range.map { r ->
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
            severity = Severity.HINT,
            message = "Component doesn't set any request attributes",
            description = """
                HST components typically set request attributes for FreeMarker templates.

                **Observation**: doBeforeRender doesn't call request.setAttribute().

                **Is This OK?**:
                -  If component only does redirects or forwards
                -  If component uses response.setRenderPath()
                -  If component is abstract/base class
                - L If template expects data from this component

                **Typical Pattern**:
                ```java
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response)
                        throws HstComponentException {
                    super.doBeforeRender(request, response);

                    // Fetch content
                    HippoBean document = getContentBean(request);

                    // Set attributes for template
                    request.setAttribute("document", document);
                    request.setAttribute("title", document.getTitle());

                    // Template can access: ${'$'}{document.title}
                }
                ```

                **Common Attributes**:
                - `document` - The content bean
                - `query` - Search results
                - `menu` - Navigation menu
                - `pageable` - Pagination info

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/hst/components/request-attributes.html
            """.trimIndent(),
            range = range
        )
    }

    private fun createEmptyComponentIssue(classDecl: ClassOrInterfaceDeclaration): InspectionIssue {
        val range = classDecl.name.range.map { r ->
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
            severity = Severity.HINT,
            message = "HST component ${classDecl.nameAsString} appears to be empty",
            description = """
                This HST component extends a component class but has no logic.

                **Observation**: Component has no doBeforeRender or other meaningful methods.

                **Is This Intentional?**:
                -  Placeholder for future implementation
                -  Uses configuration only (catalog/parameters)
                -  Relies entirely on parent class behavior
                - L Left over from refactoring
                - L Forgot to implement functionality

                **If Empty Is OK**:
                - Add a comment explaining why
                - Consider if this class is needed at all

                **If Logic Is Needed**:
                ```java
                public class ${classDecl.nameAsString} extends BaseHstComponent {

                    @Override
                    public void doBeforeRender(HstRequest request, HstResponse response)
                            throws HstComponentException {
                        super.doBeforeRender(request, response);

                        // Add your component logic here
                    }
                }
                ```

                **Alternative**: If truly empty, use parent component directly in HST configuration.
            """.trimIndent(),
            range = range
        )
    }
}
