package rocks.jimi.busybee

import com.github.ajalt.clikt.core.CliktCommand
import kotlinx.coroutines.runBlocking
import rocks.jimi.busybee.api.GoogleCalendarService
import rocks.jimi.busybee.cli.ConfigSerializer
import rocks.jimi.busybee.cli.ConsoleUI
import rocks.jimi.busybee.cli.InputReader
import rocks.jimi.busybee.config.CalendarConfig
import rocks.jimi.busybee.config.Config
import rocks.jimi.busybee.config.OAuthConfig
import rocks.jimi.busybee.config.SyncConfig
import rocks.jimi.busybee.oauth.OAuthHandler
import java.awt.Desktop
import java.io.File
import java.net.URI

class Configure : CliktCommand(name = "configure") {

    private val inputReader = InputReader()
    private val consoleUI = ConsoleUI()
    private val configSerializer = ConfigSerializer()
    private val oauthHandler = OAuthHandler()
    private val googleCalendarService = GoogleCalendarService()

    override fun run() {
        println("=== BusyBee Configuration ===\n")

        val configFile = File("config.yaml")

        var config = if (configFile.exists()) {
            try {
                configSerializer.load()
            } catch (e: Exception) {
                println("Warning: Could not load existing config: ${e.message}")
                null
            }
        } else {
            null
        }

        var shouldExit = false
        while (!shouldExit) {
            consoleUI.displayMainMenu(config)

            println("Enter option (1-7):")
            val choice = inputReader.readLine(listOf("1", "2", "3", "4", "5", "6", "7"))

            when (choice) {
                "1" -> config = configureOAuth(config)
                "2" -> config = addGoogleCalendar(config)
                "3" -> config = addCalDav(config)
                "4" -> config = removeCalendar(config)
                "5" -> config = configureSync(config)
                "6" -> shouldExit = handleSave(config, configFile)
                "7" -> config = handleClear(config, configFile)
            }
        }
    }

    private fun handleSave(config: Config?, configFile: File): Boolean {
        return when {
            config != null && config.oauth != null -> {
                configSerializer.save(config)
                println("\nConfiguration saved to config.yaml")
                println("Run 'busybee sync' to test or 'busybee run' to start daemon")
                true
            }
            config != null && config.calendars.isNotEmpty() -> {
                println("\nWarning: OAuth credentials not configured.")
                println("Your Google calendars will not work without OAuth.")
                println("Save anyway? (y/n)")
                val confirm = inputReader.readLine(listOf("y", "n", "Y", "N"))
                if (confirm.lowercase() == "y") {
                    configSerializer.save(config)
                    println("\nConfiguration saved (without OAuth).")
                    true
                } else {
                    false
                }
            }
            else -> {
                configSerializer.save(config ?: Config())
                println("\nConfiguration saved.")
                true
            }
        }
    }

    private fun handleClear(config: Config?, configFile: File): Config? {
        println("This will clear all configuration. Are you sure? (y/n)")
        val confirm = inputReader.readLine(listOf("y", "n", "Y", "N"))
        return if (confirm.lowercase() == "y") {
            configFile.delete()
            File("tokens").listFiles()?.forEach { it.delete() }
            println("Configuration cleared. Starting fresh...")
            null
        } else {
            config
        }
    }

    private fun configureOAuth(config: Config?): Config {
        consoleUI.displayOAuthSetupInstructions()

        if (config?.oauth != null) {
            val maskedId = consoleUI.displayCurrentOAuth(config.oauth.clientId)
            println("Current Client ID: $maskedId")
            println("Change? (y/n)")
            val change = inputReader.readLine(listOf("y", "n", "Y", "N"))
            if (change.lowercase() == "n") {
                return config
            }
        }

        val clientId = inputReader.readNonEmpty("Enter your OAuth Client ID:")
        val clientSecret = inputReader.readNonEmpty("Enter your OAuth Client Secret:")

        println("\nVerifying credentials...")
        val verified = runBlocking { oauthHandler.verifyCredentials(clientId, clientSecret) }

        if (!verified) {
            println("Failed to verify credentials. Please check and try again.")
            return config ?: Config()
        }

        println("Credentials verified successfully!")

        return Config(
            calendars = config?.calendars ?: emptyList(),
            sync = config?.sync ?: SyncConfig(),
            oauth = OAuthConfig(clientId = clientId, clientSecret = clientSecret),
            logging = config?.logging,
            alerts = config?.alerts
        )
    }

    private fun addGoogleCalendar(config: Config?): Config {
        if (config?.oauth == null) {
            println("\nError: Please configure OAuth credentials first (option 1)")
            return config ?: Config()
        }

        consoleUI.displayGoogleCalendarSetup()

        val accountName = inputReader.readOptional(
            "Enter a name for this account (e.g., personal, work):",
            "google"
        )

        val tokenFile = File("tokens", "$accountName.json")
        if (tokenFile.exists()) {
            println("Token already exists for '$accountName'.")
            println("Re-authorize? (y/n)")
            val reauth = inputReader.readLine(listOf("y", "n", "Y", "N"))
            if (reauth.lowercase() != "y") {
                return config
            }
        }

        println("\nPress Enter to open authorization page...")
        inputReader.readLine()

        val authUrl = oauthHandler.buildAuthorizationUrl(config.oauth.clientId)

        try {
            Desktop.getDesktop().browse(URI(authUrl))
        } catch (e: Exception) {
            println("Could not open browser. Please open this URL manually:")
            println(authUrl)
        }

        println("\nAfter authorizing, you'll see a code on the screen.")
        println("Copy and paste it here:")
        val authCode = inputReader.readLine()

        println("\nExchanging code for refresh token...")

        val tokenResult = runBlocking {
            oauthHandler.exchangeForRefreshToken(
                config.oauth.clientId,
                config.oauth.clientSecret,
                authCode
            )
        }

        val refreshToken = when (tokenResult) {
            is rocks.jimi.busybee.oauth.OAuthTokenResult.Success -> tokenResult.refreshToken
            is rocks.jimi.busybee.oauth.OAuthTokenResult.InvalidGrant -> {
                println("Error: Invalid authorization code. Please try again.")
                return config
            }
            is rocks.jimi.busybee.oauth.OAuthTokenResult.Error -> {
                println("Error: ${tokenResult.message}")
                return config
            }
        }

        tokenFile.parentFile?.mkdirs()
        val tokenJson = """
{
  "refreshToken": "$refreshToken",
  "clientId": "${config.oauth.clientId}",
  "clientSecret": "${config.oauth.clientSecret}"
}
        """.trimIndent()
        tokenFile.writeText(tokenJson)

        println("Token saved to: ${tokenFile.absolutePath}")

        println("\nFetching your calendars...")
        val calendars = googleCalendarService.listCalendars(
            config.oauth.clientId,
            config.oauth.clientSecret,
            refreshToken
        )

        if (calendars.isEmpty()) {
            println("No calendars found for this account.")
            return config
        }

        consoleUI.displayCalendarList(calendars)

        println("\nEnter calendar numbers to sync (comma-separated, e.g., 1,3):")
        val selection = inputReader.readLine()

        val selectedIndices = selection.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..calendars.size }
            .map { it - 1 }
            .toSet()

        if (selectedIndices.isEmpty()) {
            println("No calendars selected.")
            return config
        }

        println("\nEnter a shortcut identifier for these calendars (optional, e.g., 'work', 'home'):")
        val shortcut = inputReader.readLine().ifEmpty { null }

        val newCalendars = selectedIndices.map { index ->
            val cal = calendars[index]
            CalendarConfig(
                id = "${accountName}_${cal.summary.ifEmpty { cal.id }}".replace(" ", "_"),
                type = "google",
                calendarId = cal.id,
                url = null,
                username = null,
                password = null,
                tokenFile = tokenFile.absolutePath,
                shortcut = shortcut
            )
        }

        val existingCalIds = config.calendars.map { it.id }.toSet()
        val calendarsToAdd = newCalendars.filter { it.id !in existingCalIds }

        if (calendarsToAdd.isEmpty()) {
            println("These calendars are already configured.")
            return config
        }

        return config.copy(calendars = config.calendars + calendarsToAdd)
    }

    private fun addCalDav(config: Config?): Config {
        consoleUI.displayCalDavSetup()

        val url = inputReader.readNonEmpty("Enter CalDAV URL:")
        val username = inputReader.readNonEmpty("Enter username:")
        val password = inputReader.readNonEmpty("Enter password:")
        val id = inputReader.readOptional("Enter calendar ID (for reference):", "caldav")
        val shortcutInput = inputReader.readOptional("Enter shortcut identifier (optional):", "")

        val newCal = CalendarConfig(
            id = id,
            type = "caldav",
            calendarId = null,
            url = url,
            username = username,
            password = password,
            tokenFile = "",
            shortcut = shortcutInput.ifEmpty { null }
        )

        val existingCalIds = config?.calendars?.map { it.id }?.toSet() ?: emptySet()
        if (id in existingCalIds) {
            println("Calendar '$id' already exists. Use option 4 to remove it first.")
            return config ?: Config()
        }

        return Config(
            calendars = (config?.calendars ?: emptyList()) + newCal,
            sync = config?.sync ?: SyncConfig(),
            oauth = config?.oauth,
            logging = config?.logging,
            alerts = config?.alerts
        )
    }

    private fun removeCalendar(config: Config?): Config {
        if (config?.calendars.isNullOrEmpty()) {
            println("\nNo calendars to remove.")
            return config ?: Config()
        }

        consoleUI.displayRemoveCalendar(config)

        println("\nEnter number to remove:")
        val selection = inputReader.readLine().toIntOrNull()

        if (selection == null || selection < 1 || selection > config.calendars.size) {
            println("Invalid selection.")
            return config
        }

        val toRemove = config.calendars[selection - 1]

        if (toRemove.type == "google" && toRemove.tokenFile.isNotEmpty()) {
            val tokenFile = File(toRemove.tokenFile)
            if (tokenFile.exists()) {
                println("Also delete token file? (y/n)")
                val deleteToken = inputReader.readLine(listOf("y", "n", "Y", "N"))
                if (deleteToken.lowercase() == "y") {
                    tokenFile.delete()
                    println("Token file deleted.")
                }
            }
        }

        val newCalendars = config.calendars.toMutableList()
        newCalendars.removeAt(selection - 1)

        return config.copy(calendars = newCalendars)
    }

    private fun configureSync(config: Config?): Config {
        val current = config?.sync ?: SyncConfig()

        consoleUI.displaySyncSettings(current)

        println("Enter new interval (minutes):")
        val intervalMinutes = inputReader.readLine().toIntOrNull() ?: current.intervalMinutes

        println("Enter new prefix (or press Enter to keep current):")
        val prefix = inputReader.readLine().ifEmpty { current.prefix }

        return Config(
            calendars = config?.calendars ?: emptyList(),
            sync = SyncConfig(intervalMinutes = intervalMinutes, prefix = prefix),
            oauth = config?.oauth,
            logging = config?.logging,
            alerts = config?.alerts
        )
    }
}
