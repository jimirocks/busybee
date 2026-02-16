package rocks.jimi.calsync

import rocks.jimi.calsync.config.ConfigLoader
import rocks.jimi.calsync.config.AlertConfig
import rocks.jimi.calsync.sync.AlertService
import rocks.jimi.calsync.sync.SyncEngine
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    when {
        args.firstOrNull() == "run" -> runDaemon()
        args.firstOrNull() == "sync" -> runOnce()
        args.firstOrNull() == "token" -> fetchToken(args.getOrNull(1) ?: "google")
        else -> printUsage()
    }
}

private fun runOnce() {
    println("Loading config...")
    val config = ConfigLoader.load()
    
    println("Running sync...")
    val engine = SyncEngine(config)
    val alertService = AlertService(config.alerts ?: AlertConfig())
    
    try {
        engine.runSync()
        println("Sync completed successfully.")
    } catch (e: Exception) {
        println("Sync failed: ${e.message}")
        alertService.sendSyncFailureAlert(e.message ?: "Unknown error")
    }
}

private fun runDaemon() {
    println("Loading config...")
    val config = ConfigLoader.load()
    
    val interval = config.sync.intervalMinutes.toLong()
    println("Starting CalSync daemon (sync every $interval minutes)...")
    
    val engine = SyncEngine(config)
    val alertService = AlertService(config.alerts ?: AlertConfig())
    
    val scheduler = Executors.newSingleThreadScheduledExecutor()
    scheduler.scheduleAtFixedRate({
        try {
            println("Running sync...")
            engine.runSync()
            println("Sync completed.")
        } catch (e: Exception) {
            println("Sync failed: ${e.message}")
            alertService.sendSyncFailureAlert(e.message ?: "Unknown error")
        }
    }, 0, interval, TimeUnit.MINUTES)
    
    Runtime.getRuntime().addShutdownHook(Thread {
        scheduler.shutdown()
    })
    
    try {
        Thread.currentThread().join()
    } catch (e: InterruptedException) {
        println("Daemon stopped.")
    }
}

private fun fetchToken(type: String) {
    println("Token fetching for $type:")
    println("1. Go to Google Cloud Console > APIs > Credentials")
    println("2. Create OAuth 2.0 Client ID")
    println("3. Download the JSON file")
    println("4. Place it at the path specified in config.yaml (e.g., tokens/personal.json)")
    println("\nFor CalDAV, use your app-specific password from your provider settings.")
}

private fun printUsage() {
    println("""
CalSync - Universal Calendar Sync Engine

Usage:
  calsync sync     Run synchronization once
  calsync run      Run as daemon (continuous sync)
  calsync token    Show token setup instructions

Configuration:
  Edit config.yaml to configure calendars and sync settings
    """.trimIndent())
}
