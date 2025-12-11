package org.bloomreach.inspections.plugin.inspections

import com.intellij.codeInspection.*
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.model.ProjectIndex
import org.bloomreach.inspections.plugin.bridge.IdeaVirtualFile
import org.bloomreach.inspections.plugin.services.BrxmInspectionService
import java.nio.file.Paths

/**
 * Base class for wrapping core inspections as IntelliJ inspections.
 *
 * This bridges between IntelliJ's inspection API and our core inspection engine.
 */
abstract class BrxmInspectionBase : LocalInspectionTool() {

    /**
     * The core inspection instance to delegate to
     */
    abstract val coreInspection: Inspection

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                super.visitFile(file)

                // Only process files that this inspection handles
                val fileType = FileType.fromFilename(file.name) ?: return
                if (!coreInspection.applicableFileTypes.contains(fileType)) {
                    return
                }

                // Get project service for shared resources
                val service = file.project.getService(BrxmInspectionService::class.java)

                // Create virtual file wrapper
                val virtualFile = IdeaVirtualFile(file.virtualFile)

                // Create inspection context
                val context = InspectionContext(
                    projectRoot = Paths.get(file.project.basePath ?: "."),
                    file = virtualFile,
                    fileContent = file.text,
                    language = fileType!!, // Safe because we checked for null above
                    config = service.config,
                    cache = service.cache,
                    projectIndex = service.projectIndex
                )

                // Run the inspection
                try {
                    val issues = coreInspection.inspect(context)

                    // Report each issue to IntelliJ
                    issues.forEach { issue ->
                        reportIssue(holder, file, issue, virtualFile)
                    }
                } catch (e: Exception) {
                    // Log error but don't fail the inspection
                    // IntelliJ will handle this gracefully
                }
            }
        }
    }

    /**
     * Report an issue to IntelliJ's problem holder
     */
    private fun reportIssue(holder: ProblemsHolder, file: PsiFile, issue: InspectionIssue, virtualFile: VirtualFile) {
        // Convert our TextRange to IntelliJ's range
        val startOffset = getOffset(file, issue.range.startLine, issue.range.startColumn)
        val endOffset = getOffset(file, issue.range.endLine, issue.range.endColumn)

        // Find the PSI element at this range
        val element = file.findElementAt(startOffset) ?: file

        // Convert severity
        val problemLevel = when (issue.severity) {
            Severity.ERROR -> ProblemHighlightType.ERROR
            Severity.WARNING -> ProblemHighlightType.WARNING
            Severity.INFO -> ProblemHighlightType.WEAK_WARNING
            Severity.HINT -> ProblemHighlightType.INFORMATION
        }

        // Get quick fixes if available
        val quickFixes = coreInspection.getQuickFixes(issue)
            .map { BrxmQuickFixWrapper(it, issue, virtualFile) }
            .toTypedArray()

        // Register the problem
        holder.registerProblem(
            element,
            issue.message,
            problemLevel,
            *quickFixes
        )
    }

    /**
     * Convert line/column to file offset
     */
    private fun getOffset(file: PsiFile, line: Int, column: Int): Int {
        val document = com.intellij.psi.PsiDocumentManager.getInstance(file.project)
            .getDocument(file) ?: return 0

        // Line numbers are 1-based in our system, 0-based in IntelliJ
        val lineNumber = (line - 1).coerceAtLeast(0).coerceAtMost(document.lineCount - 1)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)

        // Column is 1-based in our system, 0-based in IntelliJ
        val columnOffset = (column - 1).coerceAtLeast(0)

        return (lineStartOffset + columnOffset).coerceIn(lineStartOffset, lineEndOffset)
    }

    override fun getDisplayName(): String = coreInspection.name

    override fun getStaticDescription(): String? = coreInspection.description

    override fun getShortName(): String = coreInspection.id

    override fun getGroupDisplayName(): String = coreInspection.category.displayName

    override fun getDefaultLevel(): HighlightDisplayLevel {
        return when (coreInspection.severity) {
            Severity.ERROR -> HighlightDisplayLevel.ERROR
            Severity.WARNING -> HighlightDisplayLevel.WARNING
            Severity.INFO, Severity.HINT -> HighlightDisplayLevel.WEAK_WARNING
        }
    }

    override fun isEnabledByDefault(): Boolean = true
}

/**
 * Wraps a core QuickFix as an IntelliJ LocalQuickFix
 */
private class BrxmQuickFixWrapper(
    private val coreQuickFix: org.bloomreach.inspections.core.engine.QuickFix,
    private val issue: InspectionIssue,
    private val virtualFile: VirtualFile
) : LocalQuickFix {

    override fun getFamilyName(): String = coreQuickFix.name

    override fun getName(): String = coreQuickFix.name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // Create quick fix context
        val context = org.bloomreach.inspections.core.engine.QuickFixContext(
            file = virtualFile,
            range = issue.range,
            issue = issue
        )

        // Apply the fix
        coreQuickFix.apply(context)
    }
}
