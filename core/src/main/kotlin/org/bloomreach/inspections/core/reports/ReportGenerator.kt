package org.bloomreach.inspections.core.reports

import org.bloomreach.inspections.core.engine.InspectionResults
import java.nio.file.Path

/**
 * Interface for generating inspection reports in various formats.
 */
interface ReportGenerator {
    /**
     * Generate a report from inspection results and write it to the specified path.
     *
     * @param results The inspection results to report
     * @param outputPath The file path where the report should be written
     */
    fun generate(results: InspectionResults, outputPath: Path)
}
