package rocks.jimi.calsync

import java.time.ZonedDateTime

data class CalendarEvent(
    val id: String,
    val summary: String,
    val description: String?,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val calendarId: String,
    val isOriginal: Boolean,
    val syncId: String? = null
)

data class SyncState(
    val sourceCalendarId: String,
    val sourceEventId: String,
    val targetCalendarId: String,
    val targetEventId: String,
    val createdAt: Long
)
