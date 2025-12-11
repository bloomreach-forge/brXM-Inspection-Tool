package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.repository.WorkflowActionInspection

/**
 * IntelliJ wrapper for WorkflowActionInspection.
 */
class WorkflowActionInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = WorkflowActionInspection()
}
