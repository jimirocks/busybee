package rocks.jimi.busybee

import rocks.jimi.busybee.config.ConfigLoader
import rocks.jimi.busybee.sync.AlertService
import rocks.jimi.busybee.sync.SyncEngine
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import rocks.jimi.busybee.config.AlertConfig
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BusyBee : CliktCommand() {
    override fun run() {}
}

class Sync : CliktCommand(name = "sync") {
    override fun run() {
        echo("Loading config...")
        val config = ConfigLoader.load()
        
        echo("Running sync...")
        val engine = SyncEngine(config)
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
    override fun run() {
        echo("Loading config...")
        val config = ConfigLoader.load()
        
        val interval = config.sync.intervalMinutes.toLong()
        echo("Starting BusyBee daemon (sync every $interval minutes)...")
        
        val engine = SyncEngine(config)
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

fun main(args: Array<String>) {
    BusyBee().subcommands(
        Sync(),
        RunDaemon(),
        TokenHelp(),
        Configure()
    ).main(args)
}
