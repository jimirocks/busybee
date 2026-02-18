package rocks.jimi.calsync.cli

import rocks.jimi.calsync.config.Config

class ConsoleUI {

    fun displayMainMenu(config: Config?) {
        println("\n=== Current Configuration ===")
        println("")

        if (config?.oauth != null) {
            val maskedId = maskClientId(config.oauth.clientId)
            println("OAuth: Configured (Client ID: $maskedId)")
        } else {
            println("OAuth: Not configured")
        }

        println("Calendars:")
        if (config?.calendars?.isNotEmpty() == true) {
            config.calendars.forEachIndexed { index, cal ->
                val typeStr = if (cal.type == "google") "Google" else "CalDAV"
                val detail = when {
                    cal.type == "google" -> cal.calendarId ?: "primary"
                    cal.url != null -> cal.url
                    else -> ""
                }
                println("  ${index + 1}. ${cal.id} ($typeStr) - $detail")
            }
        } else {
            println("  (none)")
        }

        val sync = config?.sync
        println("Sync: every ${sync?.intervalMinutes ?: 15} minutes, prefix: '${sync?.prefix ?: "[SYNC]"}'")

        println("")
        println("Options:")
        println("  1. Configure OAuth credentials")
        println("  2. Add Google Calendar account")
        println("  3. Add CalDAV calendar")
        println("  4. Remove a calendar")
        println("  5. Configure sync settings")
        println("  6. Save & Exit")
        println("  7. Start fresh (clear all)")
    }

    fun displayOAuthSetupInstructions() {
        println("\n=== Configure OAuth Credentials ===")
        println("")

        println("IMPORTANT: Your OAuth app is in TESTING mode.")
        println("- All Google accounts must be added as Test Users")
        println("")
        println("Steps:")
        println("1. Go to: https://console.cloud.google.com/apis/credentials")
        println("2. Create OAuth 2.0 Client ID (Desktop app) if you haven't")
        println("3. Enable Google Calendar API:")
        println("   - Go to: https://console.cloud.google.com/apis/library")
        println("   - Search for 'Google Calendar API' and enable it")
        println("4. Go to: https://console.cloud.google.com/apis/authconsent")
        println("5. In 'Test users', add ALL Google accounts you want to sync")
        println("6. Note your Client ID and Client Secret")
        println("")
    }

    fun displayGoogleCalendarSetup() {
        println("\n=== Add Google Calendar Account ===")
        println("")
        println("This account needs authorization.")
        println("Make sure your Google account is added as Test User in Google Cloud Console.")
        println("")
    }

    fun displayCalDavSetup() {
        println("\n=== Add CalDAV Calendar ===")
        println("")
    }

    fun displayCalendarList(calendars: List<GoogleCalendarInfo>) {
        calendars.forEachIndexed { index, cal ->
            val displayName = if (cal.summary.isEmpty() || cal.id == "primary") {
                "${cal.id} (primary)"
            } else {
                cal.summary
            }
            println("  ${index + 1}. $displayName")
        }
    }

    fun displaySyncSettings(current: rocks.jimi.calsync.config.SyncConfig) {
        println("\n=== Configure Sync Settings ===")
        println("")
        println("Current: every ${current.intervalMinutes} minutes")
        println("Current prefix: '${current.prefix}'")
    }

    fun displayRemoveCalendar(config: Config) {
        println("\n=== Remove Calendar ===")
        println("")
        config.calendars.forEachIndexed { index, cal ->
            println("  ${index + 1}. ${cal.id} (${cal.type})")
        }
    }

    fun displayCurrentOAuth(clientId: String): String {
        return maskClientId(clientId)
    }

    private fun maskClientId(clientId: String): String {
        return if (clientId.length > 20) {
            clientId.take(10) + "..." + clientId.takeLast(10)
        } else {
            clientId
        }
    }
}

data class GoogleCalendarInfo(val id: String, val summary: String)
