package org.bloomreach.inspections.core.inspections.repository

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.BlockStmt
import org.bloomreach.inspections.core.engine.*

/**
 * Detects workflow actions invoked without checking availability.
 *
 * In Bloomreach CMS, workflow actions should only be invoked after checking
 * if they are available using workflowManager.getWorkflow().getActions().
 * Invoking actions without availability checks can cause runtime errors.
 *
 * From community analysis: 12% of workflow-related issues stem from
 * invoking unavailable actions.
 */
class WorkflowActionInspection : Inspection() {

    override val id: String = "repository.workflow-action"

    override val name: String = "Workflow Action Without Availability Check"

    override val description: String = """
        Detects workflow actions that are invoked without first checking availability.

        Workflow actions may not be available depending on the document state.
        Always check action availability using workflow.getActions() before invoking.

        Example of the issue:
        ```java
        Workflow workflow = workflowManager.getWorkflow("default", document);
        workflow.doAction("publish"); // No availability check!
        ```

        Correct usage:
        ```java
        Workflow workflow = workflowManager.getWorkflow("default", document);
        Set<String> actions = workflow.getActions();
        if (actions.contains("publish")) {
            workflow.doAction("publish");
        }
        ```
    """.trimIndent()

    override val category: InspectionCategory = InspectionCategory.REPOSITORY_TIER

    override val severity: Severity = Severity.WARNING

    override val applicableFileTypes: Set<FileType> = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val issues = mutableListOf<InspectionIssue>()

        try {
            val cu = JavaParser().parse(context.fileContent).result.orElse(null) ?: return emptyList()

            // Find all methods
            cu.findAll(MethodDeclaration::class.java).forEach { method ->
                val methodBody = method.body.orElse(null) ?: return@forEach

                // Track workflow variables and their availability checks
                val workflowVars = mutableMapOf<String, Boolean>() // varName -> hasAvailabilityCheck

                analyzeMethodBody(methodBody, workflowVars, issues, context)
            }
        } catch (e: Exception) {
            // Parsing failed, skip this file
        }

        return issues
    }

    private fun analyzeMethodBody(
        block: BlockStmt,
        workflowVars: MutableMap<String, Boolean>,
        issues: MutableList<InspectionIssue>,
        context: InspectionContext
    ) {
        block.statements.forEach { stmt ->
            stmt.findAll(MethodCallExpr::class.java).forEach { call ->
                val methodName = call.nameAsString
                val scopeName = call.scope.map { it.toString() }.orElse(null)

                when {
                    // Track workflow variable creation
                    methodName == "getWorkflow" -> {
                        // This is assigning to a variable, track it
                        val parent = call.parentNode.orElse(null)
                        if (parent != null) {
                            val varName = extractVariableName(parent.toString())
                            if (varName != null) {
                                workflowVars[varName] = false // Not checked yet
                            }
                        }
                    }

                    // Track getActions() calls - marks workflow as checked
                    methodName == "getActions" && scopeName != null -> {
                        workflowVars[scopeName] = true
                    }

                    // Detect doAction() without availability check
                    methodName == "doAction" && scopeName != null -> {
                        val hasCheck = workflowVars[scopeName] ?: false

                        if (!hasCheck) {
                            val line = call.begin.map { it.line }.orElse(0)
                            val actionName = if (call.arguments.isNotEmpty()) {
                                call.arguments[0].toString()
                            } else {
                                "unknown"
                            }

                            issues.add(
                                InspectionIssue(
                                    inspection = this@WorkflowActionInspection,
                                    file = context.file,
                                    severity = severity,
                                    message = "Workflow action $actionName invoked without availability check",
                                    description = buildDescription(scopeName, actionName),
                                    range = TextRange(line, 0, line, 0)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun extractVariableName(statement: String): String? {
        // Try to extract variable name from assignment like "Workflow workflow = ..."
        val assignmentPattern = Regex("""(\w+)\s*=\s*""")
        val match = assignmentPattern.find(statement)
        return match?.groupValues?.get(1)
    }

    private fun buildDescription(workflowVar: String, actionName: String): String {
        return """
            Workflow action $actionName is invoked on variable '$workflowVar' without checking availability.

            Workflow actions may not always be available depending on the document state and permissions.
            Invoking unavailable actions will throw WorkflowException at runtime.

            Fix:
            1. Call getActions() to get available actions
            2. Check if your action is in the set
            3. Only invoke if available

            Example:
            ```java
            Set<String> actions = $workflowVar.getActions();
            if (actions.contains($actionName)) {
                $workflowVar.doAction($actionName);
            } else {
                log.warn("Action {} not available", $actionName);
            }
            ```

            Alternative using contains():
            ```java
            if ($workflowVar.getActions().contains($actionName)) {
                $workflowVar.doAction($actionName);
            }
            ```
        """.trimIndent()
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(AddAvailabilityCheckQuickFix())
    }
}

/**
 * Quick fix that adds an availability check before the workflow action
 */
class AddAvailabilityCheckQuickFix : BaseQuickFix(
    name = "Add availability check",
    description = "Wraps the workflow action in an availability check"
) {
    override fun apply(context: QuickFixContext) {
        // This is a simplified version - in production, would need more sophisticated AST manipulation
        val content = context.file.readText()
        val lines = content.split("\n").toMutableList()

        val lineIndex = context.range.startLine - 1
        if (lineIndex < 0 || lineIndex >= lines.size) return

        val line = lines[lineIndex]
        val indent = line.takeWhile { it.isWhitespace() }

        // Extract workflow variable and action name from doAction call
        val doActionPattern = Regex("""(\w+)\.doAction\((.+?)\)""")
        val match = doActionPattern.find(line) ?: return
        val (workflowVar, actionExpr) = match.destructured

        // Build the availability check
        val checkCode = buildString {
            appendLine("${indent}if ($workflowVar.getActions().contains($actionExpr)) {")
            appendLine("$indent    $line")
            appendLine("${indent}} else {")
            appendLine("${indent}    log.warn(\"Action {} not available\", $actionExpr);")
            appendLine("${indent}}")
        }

        // Replace the line with the wrapped version
        lines[lineIndex] = checkCode

        val newContent = lines.joinToString("\n")
        java.nio.file.Files.writeString(context.file.path, newContent)
    }
}
