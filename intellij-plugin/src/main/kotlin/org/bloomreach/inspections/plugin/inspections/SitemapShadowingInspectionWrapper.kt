package org.bloomreach.inspections.plugin.inspections

import org.bloomreach.inspections.core.engine.Inspection
import org.bloomreach.inspections.core.inspections.config.SitemapShadowingInspection

/**
 * IntelliJ wrapper for SitemapShadowingInspection.
 *
 * Detects HST sitemap patterns that shadow each other, where
 * more general patterns are defined before more specific ones.
 */
class SitemapShadowingInspectionWrapper : BrxmInspectionBase() {
    override val coreInspection: Inspection = SitemapShadowingInspection()
}
