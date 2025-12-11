package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.config.ComponentParameterNullInspection

/**
 * IntelliJ wrapper for ComponentParameterNullInspection.
 *
 * Detects HST component parameters accessed without null checks,
 * which can cause NullPointerExceptions at runtime.
 */
class ComponentParameterNullInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = ComponentParameterNullInspection()
}
