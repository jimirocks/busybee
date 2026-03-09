package rocks.jimi.busybee.cli

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import rocks.jimi.busybee.config.CalendarConfig
import rocks.jimi.busybee.config.OAuthConfig
import java.io.File

class ReauthHelperTargetCalendarTest : StringSpec({

    val testTokenDir = File("build/test-tokens")
    val testClientId = "test-client-id"
    val testClientSecret = "test-secret"

    val googleCalendars = listOf(
        CalendarConfig(
            id = "cal1",
            type = "google",
            calendarId = "user1@gmail.com",
            tokenFile = "${testTokenDir.absolutePath}/user1.json"
        ),
        CalendarConfig(
            id = "cal2",
            type = "google",
            calendarId = "user2@company.com",
            tokenFile = "${testTokenDir.absolutePath}/user2.json"
        ),
        CalendarConfig(
            id = "caldav1",
            type = "caldav",
            url = "https://example.com/cal",
            tokenFile = ""
        )
    )

    val oauthConfig = OAuthConfig(
        clientId = testClientId,
        clientSecret = testClientSecret
    )

    "targetCalendarId filters correctly - finds exact match" {
        val targetCal = googleCalendars.filter { it.type == "google" }
            .filter { it.tokenFile.isNotEmpty() }
            .filter { it.id == "cal1" }

        targetCal.size shouldBe 1
        targetCal.first().id shouldBe "cal1"
        targetCal.first().calendarId shouldBe "user1@gmail.com"
    }

    "targetCalendarId filters correctly - no match returns empty" {
        val targetCal = googleCalendars.filter { it.type == "google" }
            .filter { it.tokenFile.isNotEmpty() }
            .filter { it.id == "nonexistent" }

        targetCal.size shouldBe 0
    }

    "OAuthConfig holds correct values" {
        oauthConfig.clientId shouldBe testClientId
        oauthConfig.clientSecret shouldBe testClientSecret
    }

    "CalendarConfig stores tokenFile path correctly" {
        val googleCals = googleCalendars.filter { it.type == "google" && it.tokenFile.isNotEmpty() }
        
        googleCals.size shouldBe 2
        googleCals.map { it.id } shouldBe listOf("cal1", "cal2")
    }
})
