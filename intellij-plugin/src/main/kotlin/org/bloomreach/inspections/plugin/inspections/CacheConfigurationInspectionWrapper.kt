package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.config.CacheConfigurationInspection

/**
 * IntelliJ wrapper for CacheConfigurationInspection
 */
class CacheConfigurationInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = CacheConfigurationInspection()
}
