package org.bloomreach.inspections.core.inspections.repository

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects issues in document workflow implementations.
 *
 * Workflow classes handle document lifecycle (publish, unpublish, etc).
 * Common issues (8% of repository problems):
 * - Missing transaction management
 * - Not checking permissions
 * - Missing null checks on workflow context
 * - Not handling workflow exceptions properly
 */
class DocumentWorkflowInspection : Inspection() {
    override val id = "repository.document-workflow"
    override val name = "Document Workflow Implementation Issues"
    override val description = """
        Detects improper workflow implementations in Bloomreach CMS.
        Workflow actions must handle transactions, permissions, and exceptions correctly.
    """.trimIndent()
    override val category = InspectionCategory.REPOSITORY_TIER
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
        val visitor = WorkflowVisitor(this, context)

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
 * Visitor for detecting workflow implementation issues
 */
private class WorkflowVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(classDecl: ClassOrInterfaceDeclaration, ctx: InspectionContext) {
        super.visit(classDecl, ctx)

        // Check if this is a workflow class
        val isWorkflowClass = classDecl.implementedTypes.any {
            it.nameAsString.contains("Workflow") ||
            it.nameAsString == "DocumentWorkflow" ||
            it.nameAsString == "Action"
        } || classDecl.extendedTypes.any {
            it.nameAsString.contains("WorkflowImpl") ||
            it.nameAsString.contains("AbstractWorkflow")
        }

        if (!isWorkflowClass) {
            return
        }

        // Check workflow action methods
        classDecl.methods.forEach { method ->
            checkWorkflowMethod(method, classDecl)
        }
    }

    private fun checkWorkflowMethod(method: MethodDeclaration, classDecl: ClassOrInterfaceDeclaration) {
        // Common workflow method names
        val workflowMethods = setOf(
            "publish", "unpublish", "depublish",
            "requestPublication", "acceptPublication", "rejectPublication",
            "requestDepublication", "acceptDepublication",
            "commitEditableInstance", "disposeEditableInstance",
            "obtainEditableInstance", "archive", "delete"
        )

        if (method.nameAsString !in workflowMethods) {
            return
        }

        if (!method.body.isPresent) {
            return
        }

        val body = method.body.get()

        // Check for permission checks
        val checksPermissions = body.findAll(MethodCallExpr::class.java).any { call ->
            call.nameAsString.contains("checkPermission") ||
            call.nameAsString.contains("isGranted") ||
            call.nameAsString.contains("hasPermission")
        }

        if (!checksPermissions) {
            issues.add(createMissingPermissionCheckIssue(method, classDecl))
        }

        // Check for null checks on document/variant
        val methodCalls = body.findAll(MethodCallExpr::class.java)
        val accessesDocument = methodCalls.any { call ->
            call.nameAsString.contains("getDocument") ||
            call.nameAsString.contains("getVariant") ||
            call.nameAsString.contains("getNode")
        }

        if (accessesDocument) {
            val hasNullCheck = body.toString().contains("== null") ||
                              body.toString().contains("!= null") ||
                              body.toString().contains("Objects.requireNonNull") ||
                              body.toString().contains("if (")

            if (!hasNullCheck) {
                issues.add(createMissingNullCheckIssue(method, classDecl))
            }
        }

        // Check for proper exception handling
        val declaresWorkflowException = method.thrownExceptions.any {
            it.asString().contains("WorkflowException") ||
            it.asString().contains("RepositoryException")
        }

        if (!declaresWorkflowException) {
            issues.add(createMissingExceptionDeclarationIssue(method, classDecl))
        }
    }

    private fun createMissingPermissionCheckIssue(
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
            message = "Workflow method '${method.nameAsString}' missing permission check",
            description = """
                Workflow actions must verify user permissions before executing.

                **Problem**: Method doesn't check if user has permission to perform this action.

                **Security Risk**: Users might be able to publish/unpublish without proper authorization.

                **Fix**: Add permission check:
                ```java
                public void ${method.nameAsString}() throws WorkflowException {
                    // Check permissions first
                    if (!isActionGranted()) {
                        throw new WorkflowException("User not authorized to ${method.nameAsString}");
                    }

                    // Or use workflow context
                    WorkflowContext context = getWorkflowContext();
                    if (!context.getWorkflow().hints().containsKey("${method.nameAsString}")) {
                        throw new WorkflowException("Action not available");
                    }

                    // Then proceed with action
                    doPublish();
                }
                ```

                **Common Permission Patterns**:
                ```java
                // Check via hints
                if (!hints.containsKey("publish")) {
                    throw new WorkflowException("Publish not allowed");
                }

                // Check via workflow context
                if (!workflow.isActionAllowed("publish")) {
                    return;
                }

                // Check JCR permissions
                if (!session.hasPermission(path, "jcr:write")) {
                    throw new AccessDeniedException();
                }
                ```

                **Why This Matters**:
                - Prevents unauthorized document modifications
                - Enforces editorial workflow rules
                - Required for multi-user environments

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/document-workflow/
            """.trimIndent(),
            range = range
        )
    }

    private fun createMissingNullCheckIssue(
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
            message = "Workflow method '${method.nameAsString}' may have null pointer issues",
            description = """
                Workflow methods should check for null documents/variants before processing.

                **Problem**: Method accesses document without null check.

                **Risk**: NullPointerException if document is deleted or unavailable.

                **Fix**: Add null checks:
                ```java
                public void ${method.nameAsString}() throws WorkflowException {
                    // Get document
                    Node documentNode = getDocumentNode();

                    // Check for null
                    if (documentNode == null) {
                        throw new WorkflowException("Document not found");
                    }

                    // Or use Optional
                    Optional<Node> variant = getVariant();
                    if (!variant.isPresent()) {
                        throw new WorkflowException("Variant not available");
                    }

                    // Safe to proceed
                    processDocument(variant.get());
                }
                ```

                **Common Scenarios**:
                - Document was deleted by another user
                - Variant doesn't exist (unpublished document)
                - Permission denied returns null
                - Workflow in inconsistent state

                **Best Practices**:
                ```java
                // 1. Check early
                Node doc = getDocument();
                Objects.requireNonNull(doc, "Document is required");

                // 2. Use Optional
                Optional<Node> optDoc = Optional.ofNullable(getDocument());
                optDoc.ifPresent(this::processDocument);

                // 3. Defensive coding
                if (doc != null && doc.hasProperty("prop")) {
                    // Safe access
                }
                ```

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/document-workflow/workflow-development.html
            """.trimIndent(),
            range = range
        )
    }

    private fun createMissingExceptionDeclarationIssue(
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
            message = "Workflow method '${method.nameAsString}' should declare WorkflowException",
            description = """
                Workflow methods should declare appropriate exceptions for error handling.

                **Problem**: Method doesn't declare WorkflowException or RepositoryException.

                **Fix**: Add throws declaration:
                ```java
                public void ${method.nameAsString}()
                        throws WorkflowException, RepositoryException {
                    try {
                        // Workflow logic
                        Node doc = getDocument();
                        doc.setProperty("status", "published");
                        session.save();
                    } catch (RepositoryException e) {
                        throw new WorkflowException("Failed to ${method.nameAsString}", e);
                    }
                }
                ```

                **Exception Strategy**:
                - `WorkflowException` - For workflow-specific errors
                - `RepositoryException` - For JCR operations
                - Wrap checked exceptions in WorkflowException
                - Log before rethrowing

                **Common Patterns**:
                ```java
                // 1. Wrap and rethrow
                try {
                    node.setProperty("x", "y");
                    session.save();
                } catch (RepositoryException e) {
                    log.error("Save failed", e);
                    throw new WorkflowException("Update failed", e);
                }

                // 2. Rollback on error
                try {
                    // modifications
                    session.save();
                } catch (Exception e) {
                    session.refresh(false); // rollback
                    throw new WorkflowException("Rolled back", e);
                }

                // 3. Validation errors
                if (!isValid(doc)) {
                    throw new WorkflowException("Document validation failed");
                }
                ```

                **Reference**: https://xmdocumentation.bloomreach.com/library/concepts/document-workflow/error-handling.html
            """.trimIndent(),
            range = range
        )
    }
}
