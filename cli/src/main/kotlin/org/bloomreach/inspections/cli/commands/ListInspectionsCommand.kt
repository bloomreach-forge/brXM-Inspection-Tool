package org.bloomreach.inspections.cli.commands

import org.bloomreach.inspections.core.engine.InspectionRegistry
import picocli.CommandLine.Command

/**
 * List all available inspections.
 */
@Command(
    name = "list-inspections",
    aliases = ["list"],
    description = ["List all available inspections"]
)
class ListInspectionsCommand : Runnable {

    override fun run() {
        val registry = InspectionRegistry()
        registry.discoverInspections()

        val inspections = registry.getAllInspections()

        println("Bloomreach CMS Inspections")
        println("=" .repeat(80))
        println("\nTotal inspections: ${inspections.size}\n")

        // Group by category
        val byCategory = inspections.groupBy { it.category }

        byCategory.forEach { (category, categoryInspections) ->
            println("${category.displayName}:")
            categoryInspections.forEach { inspection ->
                val severityIcon = when (inspection.severity.name) {
                    "ERROR" -> "🔴"
                    "WARNING" -> "🟡"
                    "INFO" -> "🔵"
                    else -> "💡"
                }

                println("  $severityIcon [${inspection.severity}] ${inspection.id}")
                println("     ${inspection.name}")
                println("     ${inspection.description}")
                println()
            }
        }

        // Show statistics
        val stats = registry.getStatistics()
        println("Statistics:")
        println("  By Severity:")
        stats.bySeverity.forEach { (severity, count) ->
            println("    $severity: $count")
        }
        println("\n  By File Type:")
        stats.byFileType.forEach { (fileType, count) ->
            if (count > 0) {
                println("    $fileType: $count")
            }
        }
    }
}
