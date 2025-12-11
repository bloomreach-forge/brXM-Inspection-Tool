package org.bloomreach.inspections.cli

import org.bloomreach.inspections.cli.commands.*
import picocli.CommandLine
import picocli.CommandLine.Command
import kotlin.system.exitProcess

/**
 * Main CLI entry point for Bloomreach CMS Inspections Tool.
 *
 * Provides commands for:
 * - Analyzing projects
 * - Listing available inspections
 * - Managing configuration
 * - Generating reports
 */
@Command(
    name = "brxm-inspect",
    description = ["Bloomreach CMS Static Analysis Tool"],
    mixinStandardHelpOptions = true,
    version = ["1.0.0"],
    subcommands = [
        AnalyzeCommand::class,
        ListInspectionsCommand::class,
        ConfigCommand::class
    ]
)
class BrxmInspect : Runnable {
    override fun run() {
        // When no command is specified, show help
        CommandLine(this).usage(System.out)
    }
}

/**
 * Main function - entry point for the CLI
 */
fun main(args: Array<String>) {
    val exitCode = CommandLine(BrxmInspect()).execute(*args)
    exitProcess(exitCode)
}
