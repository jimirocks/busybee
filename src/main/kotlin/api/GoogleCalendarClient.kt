package rocks.jimi.busybee.api

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Event.Reminders
import com.google.auth.http.HttpCredentialsAdapter
import rocks.jimi.busybee.config.CalendarConfig
import kotlin.time.Instant

class GoogleCalendarClient(
    private val config: CalendarConfig,
    private val tokenManager: TokenManager
) {
    private val httpTransport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    
    @Volatile
    private var service: Calendar = createService()
    
    private fun createService(): Calendar {
        val credentials = tokenManager.getCredentials()
        return Calendar.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName("BusyBee")
            .build()
    }
    
    fun refreshService() {
        tokenManager.refreshToken()
        service = createService()
    }
    
    fun listEvents(timeMin: Instant, timeMax: Instant): List<GoogleEvent> {
        val events = service.events().list(config.calendarId)
            .setTimeMin(DateTime(timeMin.toString()))
            .setTimeMax(DateTime(timeMax.toString()))
            .setSingleEvents(true)
            .setMaxAttendees(2500)
            .execute()
        
        return events.items?.mapNotNull { e ->
            if (e.status != "confirmed") return@mapNotNull null
            if (e.attendeesOmitted == true) return@mapNotNull null
            
            val isOrganizer = e.organizer?.self == true
            if (!isOrganizer) {
                val myResponse = e.attendees?.find { it.self == true }?.responseStatus
                if (myResponse != "accepted") return@mapNotNull null
            }
            
            val startTime = e.start.dateTime?.toString() ?: return@mapNotNull null
            val endTime = e.end.dateTime?.toString() ?: return@mapNotNull null
            GoogleEvent(
                id = e.id,
                summary = e.summary ?: "Busy",
                description = e.description,
                start = Instant.parse(startTime),
                end = Instant.parse(endTime)
            )
        } ?: emptyList()
    }
    
    fun createEvent(summary: String, description: String?, start: Instant, end: Instant, visibility: String? = null): String {
        val event = Event().apply {
            this.summary = summary
            this.description = description
            this.visibility = visibility
            this.start = EventDateTime()
                .setDateTime(DateTime(start.toString()))
            this.end = EventDateTime()
                .setDateTime(DateTime(end.toString()))
            this.reminders = Reminders()
                .setUseDefault(false)
        }
        
        val created = service.events().insert(config.calendarId, event).execute()
        return created.id
    }
    
    fun updateEvent(eventId: String, summary: String, description: String?, start: Instant, end: Instant, visibility: String? = null) {
        val event = service.events().get(config.calendarId, eventId).execute()
        event.summary = summary
        event.description = description
        event.visibility = visibility
        event.start = EventDateTime()
            .setDateTime(DateTime(start.toString()))
        event.end = EventDateTime()
            .setDateTime(DateTime(end.toString()))
        event.reminders = Reminders()
            .setUseDefault(false)
        
        service.events().update(config.calendarId, eventId, event).execute()
    }
    
    fun deleteEvent(eventId: String) {
        service.events().delete(config.calendarId, eventId).execute()
    }
}

data class GoogleEvent(
    val id: String,
    val summary: String,
    val description: String?,
    val start: Instant,
    val end: Instant
)
