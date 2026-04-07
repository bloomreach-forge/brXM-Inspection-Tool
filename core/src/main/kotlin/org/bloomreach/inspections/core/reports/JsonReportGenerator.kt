package org.bloomreach.inspections.core.reports

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.bloomreach.inspections.core.engine.InspectionResults
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Generates JSON format inspection reports.
 */
class JsonReportGenerator : ReportGenerator {

    private val mapper = ObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    override fun generate(results: InspectionResults, outputPath: Path) {
        val report = createReportData(results)
        val json = mapper.writeValueAsString(report)
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, json)
    }

    private fun createReportData(results: InspectionResults): Map<String, Any> {
        return mapOf(
            "metadata" to mapOf(
                "tool" to "Bloomreach CMS Inspections",
                "version" to "1.0.0",
                "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "projectRoot" to (results.issues.firstOrNull()?.file?.path?.parent?.toString() ?: "unknown")
            ),
            "summary" to mapOf(
                "totalIssues" to results.totalIssues,
                "errors" to results.errorCount,
                "warnings" to results.warningCount,
                "info" to results.infoCount,
                "hints" to results.hintCount
            ),
            "byCategory" to results.issuesByCategory.map { (category, issues) ->
                mapOf(
                    "category" to category.name,
                    "displayName" to category.displayName,
                    "count" to issues.size
                )
            },
            "bySeverity" to results.issuesBySeverity.map { (severity, issues) ->
                mapOf(
                    "severity" to severity.name,
                    "count" to issues.size
                )
            },
            "issues" to results.issues.map { issue ->
                mapOf(
                    "id" to issue.inspection.id,
                    "name" to issue.inspection.name,
                    "severity" to issue.severity.name,
                    "category" to issue.inspection.category.name,
                    "file" to issue.file.path.toString(),
                    "line" to issue.range.startLine,
                    "column" to issue.range.startColumn,
                    "message" to issue.message,
                    "description" to issue.description,
                    "metadata" to issue.metadata
                )
            }
        )
    }
}
