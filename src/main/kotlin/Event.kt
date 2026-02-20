package rocks.jimi.calsync

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CalendarEvent(
    val id: String,
    val summary: String,
    val description: String?,
    val start: Instant,
    val end: Instant,
    val calendarId: String,
    val isOriginal: Boolean,
    val syncId: String? = null
)

@Serializable
data class SyncState(
    val sourceCalendarId: String,
    val sourceEventId: String,
    val targetCalendarId: String,
    val targetEventId: String,
    val createdAt: Long
)
