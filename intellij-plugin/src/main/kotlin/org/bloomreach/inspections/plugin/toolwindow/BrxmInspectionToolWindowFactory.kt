package org.bloomreach.inspections.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Bloomreach Inspections tool window.
 *
 * This tool window displays inspection results, statistics, and
 * provides quick access to inspection settings.
 */
class BrxmInspectionToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = BrxmInspectionToolWindowContent(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            toolWindowContent.getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
