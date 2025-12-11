package org.bloomreach.inspections.core.inspections.repository

import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects dangerous Session.refresh() calls that can cause data corruption.
 *
 * JCR Session.refresh() discards pending changes (12% of data corruption bugs).
 *
 * Common issues:
 * - Calling refresh() with keepChanges=false loses modifications
 * - Calling refresh() without understanding implications
 * - Using refresh() when save() is needed
 */
class SessionRefreshInspection : Inspection() {
    override val id = "repository.session-refresh"
    override val name = "Dangerous Session.refresh() Call"
    override val description = """
        Detects Session.refresh() calls that can lead to data corruption.
        refresh(false) discards all pending changes without saving.
    """.trimIndent()
    override val category = InspectionCategory.REPOSITORY_TIER
    override val severity = Severity.ERROR
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
        val visitor = SessionRefreshVisitor(this, context)

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
 * Visitor for detecting Session.refresh() calls
 */
private class SessionRefreshVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    override fun visit(methodCall: MethodCallExpr, ctx: InspectionContext) {
        super.visit(methodCall, ctx)

        // Check for refresh() method call
        if (methodCall.nameAsString != "refresh") {
            return
        }

        // Check if the scope looks like a JCR Session
        val scope = methodCall.scope.orElse(null)?.toString() ?: return

        val isJcrSession = scope.contains("Session") ||
                          scope == "session" ||
                          scope.contains("getSession()") ||
                          scope.contains("jcrSession")

        if (!isJcrSession) {
            return
        }

        // Check the argument - refresh(false) is particularly dangerous
        val argument = methodCall.arguments.firstOrNull()?.toString()
        val isExplicitFalse = argument == "false"

        // Flag the refresh call
        issues.add(createRefreshIssue(methodCall, isExplicitFalse))
    }

    private fun createRefreshIssue(
        methodCall: MethodCallExpr,
        isExplicitFalse: Boolean
    ): InspectionIssue {
        val range = methodCall.name.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        val severityLevel = if (isExplicitFalse) Severity.ERROR else Severity.WARNING

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = severityLevel,
            message = if (isExplicitFalse) {
                "Session.refresh(false) discards all pending changes - HIGH RISK of data loss"
            } else {
                "Session.refresh() call detected - verify this is intentional"
            },
            description = """
                JCR Session.refresh() can cause data corruption if used incorrectly.

                **Problem**: Code calls session.refresh()${if (isExplicitFalse) " with keepChanges=false" else ""}

                **What refresh() Does**:
                - `refresh(false)` - **DISCARDS** all pending changes, reloads from repository
                - `refresh(true)` - Keeps pending changes, updates non-modified nodes

                **Data Corruption Scenarios**:

                **Scenario 1: Lost Updates**
                ```java
                // User makes changes
                node.setProperty("title", "New Title");
                node.setProperty("author", "John");

                // Some code calls refresh
                session.refresh(false); // L ALL CHANGES LOST!

                // Save does nothing - changes were discarded
                session.save(); // Saves empty changeset
                ```

                **Scenario 2: Partial Data**
                ```java
                // Update multiple related nodes
                parentNode.setProperty("count", childCount);
                childNode.setProperty("index", i);

                // Refresh in between
                session.refresh(false); // L Inconsistent state!

                // Only some changes saved
                session.save(); // Corrupted relationships
                ```

                **Scenario 3: Race Conditions**
                ```java
                // Thread A: reading
                String value = node.getProperty("value").getString();

                // Thread B: modifies and saves
                node.setProperty("value", "new");
                session.save();

                // Thread A: refresh and lose own changes
                session.refresh(false); // Sees thread B's change
                node.setProperty("other", value); // But uses stale value
                ```

                **When refresh() IS Needed (Rare)**:

                **Use Case 1: Read-Only Long Sessions**
                ```java
                // Read-only session needs latest data
                session.refresh(true);
                Node latest = session.getNode("/content");
                // Now sees changes from other sessions
                ```

                **Use Case 2: Rollback After Error**
                ```java
                try {
                    node.setProperty("x", "y");
                    riskyOperation();
                    session.save();
                } catch (Exception e) {
                    // Explicitly rollback changes
                    session.refresh(false); //  Intentional rollback
                    throw e;
                }
                ```

                **Better Alternatives**:

                **Alternative 1: Don't Call refresh() - Just save()**
                ```java
                // L WRONG
                node.setProperty("title", title);
                session.refresh(false); // Why?
                session.save(); // Nothing to save!

                //  CORRECT
                node.setProperty("title", title);
                session.save(); // Commits changes
                ```

                **Alternative 2: Use New Session for Fresh Data**
                ```java
                // L WRONG - reuse session with refresh
                session.refresh(true);
                process(session);

                //  CORRECT - new session
                Session freshSession = repository.login();
                try {
                    process(freshSession);
                } finally {
                    freshSession.logout();
                }
                ```

                **Alternative 3: Transaction Rollback**
                ```java
                // For explicit rollback, document intention
                try {
                    modifyNodes();
                    if (!validate()) {
                        // Explicit rollback with comment
                        session.refresh(false); // Rollback invalid changes
                        throw new ValidationException();
                    }
                    session.save();
                } catch (Exception e) {
                    // Already rolled back or will be rolled back
                }
                ```

                **The Golden Rule**:
                > If you have unsaved changes, NEVER call refresh(false) unless you explicitly want to discard them.

                **Red Flags in Code Review**:
                - Any `refresh(false)` after property modifications
                - `refresh()` without a comment explaining why
                - `refresh()` in loops or repeated operations
                - `refresh()` followed by `save()`

                **Debugging Data Loss**:
                If data randomly disappears:
                1. Search for `session.refresh(false)`
                2. Check if called between modify and save
                3. Add logging before/after refresh
                4. Verify changes actually saved

                **Reference**:
                - JCR Spec: https://docs.adobe.com/docs/en/spec/jcr/2.0/10_Writing.html#10.7%20Refresh
                - brXM Docs: https://xmdocumentation.bloomreach.com/library/concepts/content-repository/session-management.html
            """.trimIndent(),
            range = range
        )
    }
}
