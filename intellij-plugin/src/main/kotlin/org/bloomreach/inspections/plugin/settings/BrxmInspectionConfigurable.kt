package org.bloomreach.inspections.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.bloomreach.inspections.plugin.services.BrxmInspectionService
import javax.swing.JComponent

/**
 * Configurable for Bloomreach CMS Inspections settings.
 *
 * Provides UI for configuring:
 * - Enabling/disabling inspections
 * - Severity levels
 * - Cache settings
 * - Project index options
 */
class BrxmInspectionConfigurable(private val project: Project) : Configurable {

    private var settingsComponent: BrxmInspectionSettingsComponent? = null

    override fun getDisplayName(): String = "Bloomreach CMS Inspections"

    override fun createComponent(): JComponent {
        val component = BrxmInspectionSettingsComponent(project)
        settingsComponent = component
        return component.getPanel()
    }

    override fun isModified(): Boolean {
        val component = settingsComponent ?: return false
        val service = project.getService(BrxmInspectionService::class.java)
        return component.isModified(service.config)
    }

    override fun apply() {
        val component = settingsComponent ?: return
        val service = project.getService(BrxmInspectionService::class.java)
        component.apply(service.config)
    }

    override fun reset() {
        val component = settingsComponent ?: return
        val service = project.getService(BrxmInspectionService::class.java)
        component.reset(service.config)
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}
