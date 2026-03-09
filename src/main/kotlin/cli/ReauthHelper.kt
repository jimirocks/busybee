package rocks.jimi.busybee.cli

import com.google.auth.oauth2.UserCredentials
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rocks.jimi.busybee.config.CalendarConfig
import rocks.jimi.busybee.config.OAuthConfig
import rocks.jimi.busybee.oauth.OAuthHandler
import rocks.jimi.busybee.oauth.OAuthTokenResult
import java.awt.Desktop
import java.io.File
import java.net.URI

object ReauthHelper {
    private val json = Json { prettyPrint = true }
    private val oauthHandler = OAuthHandler()

    fun reauthorize(oauthConfig: OAuthConfig, calendars: List<CalendarConfig>, targetCalendarId: String? = null): Boolean {
        val googleCalendars = calendars.filter { it.type == "google" && it.tokenFile.isNotEmpty() }
        if (googleCalendars.isEmpty()) {
            println("No Google calendars configured. Cannot re-authenticate.")
            return false
        }

        val targetCalendars = if (targetCalendarId != null) {
            googleCalendars.filter { it.id == targetCalendarId }.ifEmpty {
                println("Calendar with ID '$targetCalendarId' not found. Re-authenticating all Google calendars.")
                googleCalendars
            }
        } else {
            googleCalendars
        }

        val tokenFiles = targetCalendars.map { File(it.tokenFile) }.toSet()

        if (targetCalendars.size == 1) {
            println("Re-authenticating for calendar: ${targetCalendars.first().calendarId ?: targetCalendars.first().id}")
        } else {
            println("Re-authenticating ${targetCalendars.size} Google calendars...")
        }

        println("Starting re-authentication with Google...")
        println()

        val authUrl = oauthHandler.buildAuthorizationUrl(oauthConfig.clientId)

        try {
            Desktop.getDesktop().browse(URI(authUrl))
        } catch (e: Exception) {
            println("Could not open browser. Please open this URL manually:")
            println(authUrl)
        }

        println("After authorizing, you'll see a code on the screen.")
        println("Copy and paste it here:")
        val authCode = readLine() ?: return false

        println("\nExchanging code for refresh token...")

        val tokenResult = runBlocking {
            oauthHandler.exchangeForRefreshToken(
                oauthConfig.clientId,
                oauthConfig.clientSecret,
                authCode
            )
        }

        val refreshToken = when (tokenResult) {
            is OAuthTokenResult.Success -> tokenResult.refreshToken
            is OAuthTokenResult.InvalidGrant -> {
                println("Error: Invalid authorization code. Please try again.")
                return false
            }
            is OAuthTokenResult.Error -> {
                println("Error: ${tokenResult.message}")
                return false
            }
        }

        val tokenJson = json.encodeToString(
            TokenData(refreshToken, oauthConfig.clientId, oauthConfig.clientSecret)
        )

        var success = true
        for (tokenFile in tokenFiles) {
            try {
                tokenFile.parentFile?.mkdirs()
                tokenFile.writeText(tokenJson)
                println("Token saved to: ${tokenFile.absolutePath}")
            } catch (e: Exception) {
                println("Failed to save token to ${tokenFile.absolutePath}: ${e.message}")
                success = false
            }
        }

        if (success) {
            println("\nRe-authentication completed!")
        }

        return success
    }

    @Serializable
    private data class TokenData(
        val refreshToken: String,
        val clientId: String,
        val clientSecret: String
    )
}
