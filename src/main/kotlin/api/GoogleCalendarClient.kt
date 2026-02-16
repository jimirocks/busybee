package rocks.jimi.calsync.api

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.auth.oauth2.UserCredentials
import rocks.jimi.calsync.config.CalendarConfig
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GoogleCalendarClient(
    private val config: CalendarConfig,
    private val tokenManager: TokenManager
) {
    private val httpTransport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    
    @Volatile
    private var service: Calendar = createService()
    
    private fun createService(): Calendar {
        val credentials = tokenManager.getCredentials()
        return Calendar.Builder(httpTransport, jsonFactory, credentials as com.google.api.client.http.HttpRequestInitializer)
            .setApplicationName("CalSync")
            .build()
    }
    
    fun refreshService() {
        tokenManager.refreshToken()
        service = createService()
    }
    
    fun listEvents(timeMin: ZonedDateTime, timeMax: ZonedDateTime): List<GoogleEvent> {
        val events = service.events().list(config.calendarId)
            .setTimeMin(com.google.api.client.util.DateTime(timeMin.format(formatter)))
            .setTimeMax(com.google.api.client.util.DateTime(timeMax.format(formatter)))
            .execute()
        
        return events.items?.map { e ->
            GoogleEvent(
                id = e.id,
                summary = e.summary ?: "Busy",
                description = e.description,
                start = ZonedDateTime.parse(e.start.dateTime.toString()),
                end = ZonedDateTime.parse(e.end.dateTime.toString())
            )
        } ?: emptyList()
    }
    
    fun createEvent(summary: String, start: ZonedDateTime, end: ZonedDateTime): String {
        val event = com.google.api.services.calendar.model.Event().apply {
            this.summary = summary
            this.description = "Synced by CalSync"
            this.start = com.google.api.services.calendar.model.EventDateTime()
                .setDateTime(com.google.api.client.util.DateTime(start.format(formatter)))
            this.end = com.google.api.services.calendar.model.EventDateTime()
                .setDateTime(com.google.api.client.util.DateTime(end.format(formatter)))
        }
        
        val created = service.events().insert(config.calendarId, event).execute()
        return created.id
    }
    
    fun updateEvent(eventId: String, summary: String, start: ZonedDateTime, end: ZonedDateTime) {
        val event = service.events().get(config.calendarId, eventId).execute()
        event.summary = summary
        event.start = com.google.api.services.calendar.model.EventDateTime()
            .setDateTime(com.google.api.client.util.DateTime(start.format(formatter)))
        event.end = com.google.api.services.calendar.model.EventDateTime()
            .setDateTime(com.google.api.client.util.DateTime(end.format(formatter)))
        
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
    val start: ZonedDateTime,
    val end: ZonedDateTime
)
