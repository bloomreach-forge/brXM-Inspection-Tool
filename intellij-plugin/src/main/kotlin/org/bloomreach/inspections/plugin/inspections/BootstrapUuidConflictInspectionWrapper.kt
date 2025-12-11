package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.config.BootstrapUuidConflictInspection

/**
 * IntelliJ wrapper for BootstrapUuidConflictInspection.
 *
 * Detects duplicate UUIDs in hippoecm-extension.xml bootstrap files,
 * which can cause repository initialization failures.
 */
class BootstrapUuidConflictInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = BootstrapUuidConflictInspection()
}
