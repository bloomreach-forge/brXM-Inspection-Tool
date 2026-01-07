package org.bloomreach.inspections.cli.commands

import org.bloomreach.inspections.cli.config.CliConfig
import org.bloomreach.inspections.cli.config.ConfigException
import org.bloomreach.inspections.cli.config.ConfigLoader
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Paths

/**
 * Configuration management command.
 */
@Command(
    name = "config",
    description = ["Manage inspection configuration"]
)
class ConfigCommand : Runnable {

    @Parameters(
        index = "0",
        description = ["Action: init, validate, or show"],
        defaultValue = "show"
    )
    var action: String = "show"

    @Option(
        names = ["-f", "--file"],
        description = ["Configuration file path (default: .brxm-inspections.yaml)"]
    )
    var configFile: String? = null

    private val configLoader = ConfigLoader()

    override fun run() {
        try {
            when (action.lowercase()) {
                "init" -> initConfig()
                "validate" -> validateConfig()
                "show" -> showConfig()
                else -> {
                    System.err.println("Unknown action: $action")
                    System.err.println("Valid actions: init, validate, show")
                }
            }
        } catch (e: ConfigException) {
            System.err.println("Configuration error: ${e.message}")
            System.exit(1)
        }
    }

    private fun initConfig() {
        val filePath = configFile?.let { Paths.get(it) } ?: Paths.get(".brxm-inspections.yaml")

        println("Creating default configuration file: $filePath")

        val config = CliConfig.default()

        try {
            configLoader.saveConfig(config, filePath)
            println("\n✓ Configuration file created at: $filePath")
            println("\nYou can now customize the configuration by editing the file.")
            println("Run 'brxm-inspect config validate' to check configuration validity.")
        } catch (e: Exception) {
            System.err.println("✗ Failed to create configuration file: ${e.message}")
            System.exit(1)
        }
    }

    private fun validateConfig() {
        val config = configLoader.loadConfigFromString(configFile)

        if (config == null) {
            println("ℹ No configuration file found. Using defaults.")
            println("✓ Default configuration is valid.")
            return
        }

        try {
            configLoader.validateConfig(config)
            println("✓ Configuration is valid:")
            println("  - Enabled: ${config.enabled ?: true}")
            println("  - Min severity: ${config.minSeverity ?: "INFO"}")
            println("  - Parallel execution: ${config.parallel ?: true}")
            println("  - Max threads: ${config.maxThreads ?: Runtime.getRuntime().availableProcessors()}")
            println("  - Cache enabled: ${config.cacheEnabled ?: true}")
            println("  - Include paths: ${config.includePaths?.size ?: 5} pattern(s)")
            println("  - Exclude paths: ${config.excludePaths?.size ?: 4} pattern(s)")
            println("  - Custom inspections: ${config.inspections?.size ?: 0}")
        } catch (e: ConfigException) {
            System.err.println("✗ Configuration validation failed:")
            System.err.println(e.message)
            System.exit(1)
        }
    }

    private fun showConfig() {
        var config = configLoader.loadConfigFromString(configFile)

        println("Current Configuration:")
        println("=" .repeat(80))

        if (config == null) {
            println("ℹ No configuration file found. Using defaults.")
            config = CliConfig.default()
        } else {
            println("📄 Loaded from: ${configFile ?: ".brxm-inspections.yaml"}")
        }

        println("\nGlobal Settings:")
        println("  Enabled: ${config.enabled ?: true}")
        println("  Min Severity: ${config.minSeverity ?: "INFO"}")
        println("  Parallel Execution: ${config.parallel ?: true}")
        println("  Max Threads: ${config.maxThreads ?: Runtime.getRuntime().availableProcessors()}")
        println("  Cache Enabled: ${config.cacheEnabled ?: true}")

        println("\nFile Patterns:")
        println("  Include: ${config.includePaths?.joinToString(", ") ?: "*.java, *.xml, *.yaml, *.yml, *.json"}")
        println("  Exclude: ${config.excludePaths?.joinToString(", ") ?: "**target/**, **build/**, **node_modules/**, **.git/**"}")

        if (config.inspections?.isNotEmpty() == true) {
            println("\nPer-Inspection Configuration:")
            config.inspections.forEach { (id, cfg) ->
                println("  $id:")
                println("    Enabled: ${cfg.enabled ?: "inherit"}")
                println("    Severity: ${cfg.severity ?: "inherit"}")
            }
        }

        println("\n" + "=" .repeat(80))
        println("Run 'brxm-inspect config validate' to validate this configuration.")
    }
}
