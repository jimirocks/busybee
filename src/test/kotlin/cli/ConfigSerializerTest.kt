package rocks.jimi.busybee.cli

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import rocks.jimi.busybee.config.CalendarConfig
import rocks.jimi.busybee.config.Config
import rocks.jimi.busybee.config.OAuthConfig
import rocks.jimi.busybee.config.SyncConfig
import java.io.File

class ConfigSerializerTest : StringSpec({

    fun createTempConfigFile(): File {
        return File.createTempFile("calsync-test", ".yaml").also {
            it.deleteOnExit()
        }
    }

    "save and load roundtrip preserves config with all fields" {
        val tempFile = createTempConfigFile()
        val serializer = ConfigSerializer(tempFile.absolutePath)

        val original = Config(
            calendars = listOf(
                CalendarConfig(
                    id = "work_calendar",
                    type = "google",
                    calendarId = "primary",
                    tokenFile = "/tokens/work.json"
                ),
                CalendarConfig(
                    id = "caldav_personal",
                    type = "caldav",
                    url = "https://caldav.example.com",
                    username = "user",
                    password = "pass123"
                )
            ),
            sync = SyncConfig(intervalMinutes = 30, prefix = "[BB]"),
            oauth = OAuthConfig(clientId = "test-client-id", clientSecret = "test-secret")
        )

        serializer.save(original)
        val loaded = serializer.load()

        loaded.calendars.size shouldBe 2
        loaded.calendars[0].id shouldBe "work_calendar"
        loaded.calendars[0].type shouldBe "google"
        loaded.calendars[0].calendarId shouldBe "primary"
        loaded.calendars[0].tokenFile shouldBe "/tokens/work.json"

        loaded.calendars[1].id shouldBe "caldav_personal"
        loaded.calendars[1].type shouldBe "caldav"
        loaded.calendars[1].url shouldBe "https://caldav.example.com"
        loaded.calendars[1].username shouldBe "user"

        loaded.sync.intervalMinutes shouldBe 30
        loaded.sync.prefix shouldBe "[BB]"

        loaded.oauth?.clientId shouldBe "test-client-id"
        loaded.oauth?.clientSecret shouldBe "test-secret"

        tempFile.delete()
    }

    "save and load roundtrip preserves minimal config" {
        val tempFile = createTempConfigFile()
        val serializer = ConfigSerializer(tempFile.absolutePath)

        val original = Config()

        serializer.save(original)
        val loaded = serializer.load()

        loaded.calendars.isEmpty() shouldBe true
        loaded.sync.intervalMinutes shouldBe 15
        loaded.sync.prefix shouldBe "[BB]"
        loaded.oauth shouldBe null

        tempFile.delete()
    }

    "save and load roundtrip preserves config without oauth" {
        val tempFile = createTempConfigFile()
        val serializer = ConfigSerializer(tempFile.absolutePath)

        val original = Config(
            calendars = listOf(
                CalendarConfig(id = "test", type = "google", calendarId = "primary")
            ),
            sync = SyncConfig(intervalMinutes = 60, prefix = "[BUSY]")
        )

        serializer.save(original)
        val loaded = serializer.load()

        loaded.calendars.size shouldBe 1
        loaded.oauth shouldBe null
        loaded.sync.intervalMinutes shouldBe 60
        loaded.sync.prefix shouldBe "[BUSY]"

        tempFile.delete()
    }
})
