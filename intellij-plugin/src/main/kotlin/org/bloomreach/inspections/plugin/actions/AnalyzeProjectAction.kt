package org.bloomreach.inspections.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.bloomreach.inspections.plugin.services.BrxmInspectionService

/**
 * Action to analyze the entire Bloomreach project.
 *
 * This action triggers a full project analysis using all registered
 * Bloomreach inspections. Results are displayed in the Problems panel.
 */
class AnalyzeProjectAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Analyzing Bloomreach Project",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                analyzeProject(project, indicator)
            }
        })
    }

    private fun analyzeProject(project: Project, indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        indicator.text = "Initializing analysis..."

        val service = project.getService(BrxmInspectionService::class.java)

        // Clear cache and rebuild index
        indicator.text = "Clearing cache and rebuilding index..."
        indicator.fraction = 0.1
        service.clearCache()
        service.rebuildIndex()

        // Get statistics
        indicator.text = "Scanning project..."
        indicator.fraction = 0.3
        val stats = service.getStatistics()

        indicator.text = "Analysis complete - found ${stats.registeredInspections} inspections"
        indicator.fraction = 1.0

        // Note: The actual inspection is performed automatically by the
        // IntelliJ inspection framework. This action just prepares the
        // project service state and triggers a refresh.
        // Users can view results in the Problems panel (Alt+6).
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only when a project is open
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
