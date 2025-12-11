package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.deployment.DockerConfigInspection

/**
 * IntelliJ wrapper for DockerConfigInspection
 */
class DockerConfigInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = DockerConfigInspection()
}
