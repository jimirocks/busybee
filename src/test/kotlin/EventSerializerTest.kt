package rocks.jimi.calsync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class EventSerializerTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `serialize and deserialize roundtrip preserves CalendarEvent`() {
        val event = CalendarEvent(
            id = "event-123",
            summary = "Team Meeting",
            description = "Weekly sync",
            start = Instant.parse("2024-01-15T10:00:00Z"),
            end = Instant.parse("2024-01-15T11:00:00Z"),
            calendarId = "work",
            isOriginal = true,
            syncId = null
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CalendarEvent>(serialized)

        assertEquals(event.id, deserialized.id)
        assertEquals(event.summary, deserialized.summary)
        assertEquals(event.description, deserialized.description)
        assertEquals(event.start, deserialized.start)
        assertEquals(event.end, deserialized.end)
        assertEquals(event.calendarId, deserialized.calendarId)
        assertEquals(event.isOriginal, deserialized.isOriginal)
        assertEquals(event.syncId, deserialized.syncId)
    }

    @Test
    fun `serialize and deserialize roundtrip preserves CalendarEvent with syncId`() {
        val event = CalendarEvent(
            id = "sync-event-456",
            summary = "[SYNC] Team Meeting",
            description = null,
            start = Instant.parse("2024-01-15T14:30:00Z"),
            end = Instant.parse("2024-01-15T15:30:00Z"),
            calendarId = "personal",
            isOriginal = false,
            syncId = "work_event-123"
        )

        val serialized = json.encodeToString(event)
        val deserialized = json.decodeFromString<CalendarEvent>(serialized)

        assertEquals(event.id, deserialized.id)
        assertEquals(event.summary, deserialized.summary)
        assertEquals(event.start, deserialized.start)
        assertEquals(event.syncId, deserialized.syncId)
    }

    @Test
    fun `deserialize from ISO 8601 string produces correct Instant`() {
        val jsonString = """{"id":"test","summary":"Test","description":null,"start":"2024-06-20T09:15:30Z","end":"2024-06-20T10:15:30Z","calendarId":"cal","isOriginal":true}"""

        val event = json.decodeFromString<CalendarEvent>(jsonString)

        assertEquals(Instant.parse("2024-06-20T09:15:30Z"), event.start)
        assertEquals(Instant.parse("2024-06-20T10:15:30Z"), event.end)
    }

    @Test
    fun `serialize SyncState roundtrip preserves data`() {
        val state = SyncState(
            sourceCalendarId = "work",
            sourceEventId = "event-abc",
            targetCalendarId = "personal",
            targetEventId = "sync-xyz",
            createdAt = 1705312800000L
        )

        val serialized = json.encodeToString(state)
        val deserialized = json.decodeFromString<SyncState>(serialized)

        assertEquals(state.sourceCalendarId, deserialized.sourceCalendarId)
        assertEquals(state.sourceEventId, deserialized.sourceEventId)
        assertEquals(state.targetCalendarId, deserialized.targetCalendarId)
        assertEquals(state.targetEventId, deserialized.targetEventId)
        assertEquals(state.createdAt, deserialized.createdAt)
    }
}
