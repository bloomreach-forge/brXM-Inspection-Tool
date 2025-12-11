package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.performance.MissingIndexInspection

/**
 * IntelliJ wrapper for MissingIndexInspection.
 */
class MissingIndexInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = MissingIndexInspection()
}
