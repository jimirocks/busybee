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
        args.firstOrNull() == "token" -> {
            println("""
CalSync Token Setup Instructions
================================

For Google Calendar OAuth tokens:

1. Go to Google Cloud Console: https://console.cloud.google.com/apis/credentials
2. Create OAuth 2.0 Client ID credentials (Desktop app)
3. Use the OAuth 2.0 Playground: https://developers.google.com/oauthplayground/
   - Click Settings (gear icon) > Use your own OAuth credentials
   - Enter your Client ID and Client Secret
   - Click Authorize APIs > Sign in with your Google account
   - Exchange authorization code for tokens
   - Copy the refresh_token from the response

4. Create token file at tokens/<calendar-id>.json:
   {
     "refreshToken": "YOUR_REFRESH_TOKEN",
     "clientId": "YOUR_CLIENT_ID", 
     "clientSecret": "YOUR_CLIENT_SECRET"
   }

For CalDAV:
   - Use your provider's app-specific password
   - Store in config.yaml directly or reference in tokenFile

For multiple Google accounts:
   - Create separate token files for each (personal.json, work1.json, etc.)
   - Each needs its own OAuth setup through the playground
            """.trimIndent())
        }
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
