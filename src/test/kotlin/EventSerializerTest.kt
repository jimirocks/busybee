package rocks.jimi.busybee

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class EventSerializerTest : StringSpec({

    val json = Json { prettyPrint = true }

    "serialize and deserialize roundtrip preserves CalendarEvent" {
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

        deserialized.id shouldBe event.id
        deserialized.summary shouldBe event.summary
        deserialized.description shouldBe event.description
        deserialized.start shouldBe event.start
        deserialized.end shouldBe event.end
        deserialized.calendarId shouldBe event.calendarId
        deserialized.isOriginal shouldBe event.isOriginal
        deserialized.syncId shouldBe event.syncId
    }

    "serialize and deserialize roundtrip preserves CalendarEvent with syncId" {
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

        deserialized.id shouldBe event.id
        deserialized.summary shouldBe event.summary
        deserialized.start shouldBe event.start
        deserialized.syncId shouldBe event.syncId
    }

    "deserialize from ISO 8601 string produces correct Instant" {
        val jsonString = """{"id":"test","summary":"Test","description":null,"start":"2024-06-20T09:15:30Z","end":"2024-06-20T10:15:30Z","calendarId":"cal","isOriginal":true}"""

        val event = json.decodeFromString<CalendarEvent>(jsonString)

        event.start shouldBe Instant.parse("2024-06-20T09:15:30Z")
        event.end shouldBe Instant.parse("2024-06-20T10:15:30Z")
    }

    "serialize SyncState roundtrip preserves data" {
        val state = SyncState(
            sourceCalendarId = "work",
            sourceEventId = "event-abc",
            targetCalendarId = "personal",
            targetEventId = "sync-xyz",
            createdAt = 1705312800000L
        )

        val serialized = json.encodeToString(state)
        val deserialized = json.decodeFromString<SyncState>(serialized)

        deserialized.sourceCalendarId shouldBe state.sourceCalendarId
        deserialized.sourceEventId shouldBe state.sourceEventId
        deserialized.targetCalendarId shouldBe state.targetCalendarId
        deserialized.targetEventId shouldBe state.targetEventId
        deserialized.createdAt shouldBe state.createdAt
    }
})
