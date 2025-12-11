package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.repository.SessionLeakInspection

/**
 * IntelliJ wrapper for SessionLeakInspection.
 *
 * Detects JCR session leaks where sessions are not properly closed
 * in finally blocks or try-with-resources.
 */
class SessionLeakInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = SessionLeakInspection()
}
