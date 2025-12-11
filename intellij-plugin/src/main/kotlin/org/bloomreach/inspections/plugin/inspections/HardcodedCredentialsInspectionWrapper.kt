package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.security.HardcodedCredentialsInspection

/**
 * IntelliJ wrapper for HardcodedCredentialsInspection.
 *
 * Detects hardcoded credentials, API keys, and other sensitive
 * information in source code.
 */
class HardcodedCredentialsInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = HardcodedCredentialsInspection()
}
