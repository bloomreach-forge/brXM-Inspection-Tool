package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.security.RestAuthenticationInspection

/**
 * IntelliJ wrapper for RestAuthenticationInspection
 */
class RestAuthenticationInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = RestAuthenticationInspection()
}
