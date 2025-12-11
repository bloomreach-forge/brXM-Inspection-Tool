package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.security.HardcodedPathsInspection

/**
 * IntelliJ wrapper for HardcodedPathsInspection.
 */
class HardcodedPathsInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = HardcodedPathsInspection()
}
