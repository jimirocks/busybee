package rocks.jimi.calsync.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import rocks.jimi.calsync.config.Config
import rocks.jimi.calsync.config.CalendarConfig
import rocks.jimi.calsync.config.OAuthConfig
import rocks.jimi.calsync.config.SyncConfig

class ConfigSerializerTest {

    @Test
    fun `save and load roundtrip preserves config with all fields`() {
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
            sync = SyncConfig(intervalMinutes = 30, prefix = "[SYNC]"),
            oauth = OAuthConfig(clientId = "test-client-id", clientSecret = "test-secret")
        )

        serializer.save(original)
        val loaded = serializer.load()

        assertEquals(2, loaded.calendars.size)
        assertEquals("work_calendar", loaded.calendars[0].id)
        assertEquals("google", loaded.calendars[0].type)
        assertEquals("primary", loaded.calendars[0].calendarId)
        assertEquals("/tokens/work.json", loaded.calendars[0].tokenFile)

        assertEquals("caldav_personal", loaded.calendars[1].id)
        assertEquals("caldav", loaded.calendars[1].type)
        assertEquals("https://caldav.example.com", loaded.calendars[1].url)
        assertEquals("user", loaded.calendars[1].username)

        assertEquals(30, loaded.sync.intervalMinutes)
        assertEquals("[SYNC]", loaded.sync.prefix)

        assertEquals("test-client-id", loaded.oauth?.clientId)
        assertEquals("test-secret", loaded.oauth?.clientSecret)

        tempFile.delete()
    }

    @Test
    fun `save and load roundtrip preserves minimal config`() {
        val tempFile = createTempConfigFile()
        val serializer = ConfigSerializer(tempFile.absolutePath)

        val original = Config()

        serializer.save(original)
        val loaded = serializer.load()

        assertTrue(loaded.calendars.isEmpty())
        assertEquals(15, loaded.sync.intervalMinutes)
        assertEquals("[SYNC]", loaded.sync.prefix)
        assertNull(loaded.oauth)

        tempFile.delete()
    }

    @Test
    fun `save and load roundtrip preserves config without oauth`() {
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

        assertEquals(1, loaded.calendars.size)
        assertNull(loaded.oauth)
        assertEquals(60, loaded.sync.intervalMinutes)
        assertEquals("[BUSY]", loaded.sync.prefix)

        tempFile.delete()
    }

    private fun createTempConfigFile(): java.io.File {
        return java.io.File.createTempFile("calsync-test", ".yaml").also {
            it.deleteOnExit()
        }
    }
}
