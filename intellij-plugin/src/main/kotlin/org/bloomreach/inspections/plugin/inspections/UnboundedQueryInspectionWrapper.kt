package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.performance.UnboundedQueryInspection

/**
 * IntelliJ wrapper for UnboundedQueryInspection.
 *
 * Detects JCR queries that don't set a result limit, which can
 * cause performance issues with large result sets.
 */
class UnboundedQueryInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = UnboundedQueryInspection()
}
