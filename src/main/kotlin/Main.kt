package rocks.jimi.busybee

import rocks.jimi.busybee.config.ConfigLoader
import rocks.jimi.busybee.sync.AlertService
import rocks.jimi.busybee.sync.SyncEngine
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import rocks.jimi.busybee.config.AlertConfig
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BusyBee : CliktCommand() {
    private val configPath: String by option("-c", "--config", help = "Path to config file")
        .default(ConfigLoader.getDefaultConfigPath())

    override fun run() {
        currentContext.findOrSetObject { configPath }
    }
}

class Sync : CliktCommand(name = "sync") {
    private val configPath: String by requireObject()
    override fun run() {
        echo("Loading config from $configPath...")
        val config = ConfigLoader.load(configPath)
        
        echo("Running sync...")
        val engine = SyncEngine(config, configPath)
        val alertService = AlertService(config.alerts ?: AlertConfig())
        
        try {
            engine.runSync()
            echo("Sync completed successfully.")
        } catch (e: Exception) {
            echo("Sync failed: ${e.message}", err = true)
            alertService.sendSyncFailureAlert(e.message ?: "Unknown error")
        }
    }
}

class RunDaemon : CliktCommand(name = "run") {
    private val configPath: String by requireObject()
    override fun run() {
        echo("Loading config from $configPath...")
        val config = ConfigLoader.load(configPath)
        
        val interval = config.sync.intervalMinutes.toLong()
        echo("Starting BusyBee daemon (sync every $interval minutes)...")
        
        val engine = SyncEngine(config, configPath)
        val alertService = AlertService(config.alerts ?: AlertConfig())
        
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler.scheduleAtFixedRate({
            try {
                echo("Running sync...")
                engine.runSync()
                echo("Sync completed.")
            } catch (e: Exception) {
                echo("Sync failed: ${e.message}", err = true)
                alertService.sendSyncFailureAlert(e.message ?: "Unknown error")
            }
        }, 0, interval, TimeUnit.MINUTES)
        
        Runtime.getRuntime().addShutdownHook(Thread {
            scheduler.shutdown()
        })
        
        try {
            Thread.currentThread().join()
        } catch (e: InterruptedException) {
            echo("Daemon stopped.")
        }
    }
}

class TokenHelp : CliktCommand(name = "token") {
    override fun run() {
        echo("""
BusyBee OAuth Setup (Automated)
==============================

1. Create OAuth credentials (one-time):
   - Go to: https://console.cloud.google.com/apis/credentials
   - Create OAuth 2.0 Client ID (Desktop app)
   - Note your Client ID and Client Secret
   - In OAuth consent screen → Test users → Add your email

2. Run configure:
   java -jar calsync.jar configure

   The wizard will guide you through:
   - Enter Client ID/Secret
   - Opens browser for authorization
   - Automatically exchanges code for tokens
   - Saves everything to config.yaml
        """.trimIndent())
    }
}

class CleanSync : CliktCommand(name = "clean") {
    private val configPath: String by requireObject()
    private val force: Boolean by option("-f", "--force", help = "Skip confirmation prompt").flag(default = false)
    private val dryRun: Boolean by option("-n", "--dry-run", help = "Show what would be deleted without deleting").flag(default = false)

    override fun run() {
        echo("Loading config from $configPath...")
        val config = ConfigLoader.load(configPath)

        val engine = SyncEngine(config, configPath)

        try {
            val events = engine.removeAllSyncedEvents(dryRun = true)

            if (dryRun) {
                echo("Dry-run: Would delete ${events.size} synced event(s):")
                echo("")
                for (event in events) {
                    echo("  - [${event.calendarId}] ${event.summary}")
                    echo("    ${event.start} - ${event.end}")
                }
                return
            }

            if (!force) {
                echo("This will delete ALL synced events (events with prefix '${config.sync.prefix}') from all calendars.")
                echo("Found ${events.size} event(s) to delete.")
                echo("The state.json file will also be cleared.")
                echo("")
                echo("Are you sure? (yes/no): ")
                val confirm = readLine()
                if (confirm?.lowercase() != "yes") {
                    echo("Cancelled.")
                    return
                }
            }

            echo("Removing all synced events...")
            engine.removeAllSyncedEvents(dryRun = false)
            echo("Successfully removed ${events.size} synced event(s) and cleared state.")
        } catch (e: Exception) {
            echo("Clean failed: ${e.message}", err = true)
        }
    }
}

fun main(args: Array<String>) {
    BusyBee().subcommands(
        Sync(),
        RunDaemon(),
        TokenHelp(),
        Configure(),
        CleanSync()
    ).main(args)
}
