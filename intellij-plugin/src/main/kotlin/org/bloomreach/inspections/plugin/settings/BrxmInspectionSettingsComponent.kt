package org.bloomreach.inspections.plugin.settings

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
import org.bloomreach.inspections.core.config.InspectionConfig
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings component UI for Bloomreach CMS Inspections.
 *
 * Provides controls for configuring inspection behavior.
 */
class BrxmInspectionSettingsComponent(private val project: Project) {

    private val mainPanel: JPanel
    private val enabledCheckbox = JBCheckBox("Enable all inspections")
    private val cacheEnabledCheckbox = JBCheckBox("Enable parse cache (improves performance)")
    private val parallelExecutionCheckbox = JBCheckBox("Enable parallel execution")

    init {
        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Bloomreach CMS Inspections Settings</b></html>"))
            .addSeparator()
            .addComponent(JBLabel("<html>" +
                "Configure how Bloomreach CMS inspections are executed.<br/>" +
                "These settings affect real-time analysis in the editor." +
                "</html>"))
            .addVerticalGap(10)
            .addLabeledComponent("General:", enabledCheckbox)
            .addComponent(cacheEnabledCheckbox)
            .addComponent(parallelExecutionCheckbox)
            .addVerticalGap(10)
            .addComponent(JBLabel("<html><i>" +
                "Note: Individual inspections can be enabled/disabled in<br/>" +
                "Settings > Editor > Inspections > Bloomreach CMS" +
                "</i></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    fun getPanel(): JComponent = mainPanel

    fun isModified(config: InspectionConfig): Boolean {
        return enabledCheckbox.isSelected != config.enabled ||
                cacheEnabledCheckbox.isSelected != config.cacheEnabled ||
                parallelExecutionCheckbox.isSelected != config.parallel
    }

    fun apply(config: InspectionConfig) {
        config.enabled = enabledCheckbox.isSelected
        config.cacheEnabled = cacheEnabledCheckbox.isSelected
        config.parallel = parallelExecutionCheckbox.isSelected
    }

    fun reset(config: InspectionConfig) {
        enabledCheckbox.isSelected = config.enabled
        cacheEnabledCheckbox.isSelected = config.cacheEnabled
        parallelExecutionCheckbox.isSelected = config.parallel
    }
}
