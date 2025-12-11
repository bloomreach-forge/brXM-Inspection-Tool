package org.bloomreach.inspections.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.bloomreach.inspections.plugin.services.BrxmInspectionService
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Content for the Bloomreach Inspections tool window.
 *
 * Displays:
 * - Current inspection statistics
 * - Cache and index status
 * - Quick actions
 */
class BrxmInspectionToolWindowContent(private val project: Project) {

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val statsPanel = JBPanel<JBPanel<*>>(GridLayout(0, 2, 10, 5))

    private val indexedFilesLabel = JBLabel("Indexed files: 0")
    private val cachedFilesLabel = JBLabel("Cached files: 0")
    private val registeredInspectionsLabel = JBLabel("Registered inspections: 0")

    init {
        setupUI()
        refreshStatistics()
    }

    private fun setupUI() {
        // Header
        val headerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JBLabel("Bloomreach CMS Inspections"), BorderLayout.WEST)
        }

        // Statistics panel
        statsPanel.apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Statistics"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            )
            add(JBLabel("Indexed Files:"))
            add(indexedFilesLabel)
            add(JBLabel("Cached Files:"))
            add(cachedFilesLabel)
            add(JBLabel("Registered Inspections:"))
            add(registeredInspectionsLabel)
        }

        // Info panel
        val infoPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Information"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            )
            add(JBLabel("<html>" +
                "<b>Real-time Analysis:</b> Issues are highlighted as you type<br/>" +
                "<b>Quick Fixes:</b> Press Alt+Enter on highlighted issues<br/>" +
                "<b>Settings:</b> Configure inspections in Settings > Tools > Bloomreach CMS Inspections<br/>" +
                "<b>Inspection Results:</b> View all issues in the Problems panel (Alt+6)" +
                "</html>"), BorderLayout.CENTER)
        }

        // Content panel with statistics and info
        val contentPanel = JPanel(BorderLayout(0, 10)).apply {
            border = JBUI.Borders.empty(10)
            add(statsPanel, BorderLayout.NORTH)
            add(infoPanel, BorderLayout.CENTER)
        }

        // Main panel assembly
        mainPanel.apply {
            add(headerPanel, BorderLayout.NORTH)
            add(JBScrollPane(contentPanel), BorderLayout.CENTER)
        }
    }

    private fun refreshStatistics() {
        val service = project.getService(BrxmInspectionService::class.java)
        val stats = service.getStatistics()

        indexedFilesLabel.text = stats.indexedFiles.toString()
        cachedFilesLabel.text = stats.cachedFiles.toString()
        registeredInspectionsLabel.text = stats.registeredInspections.toString()
    }

    fun getContent(): JComponent = mainPanel
}
