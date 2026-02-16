package rocks.jimi.calsync.api

import rocks.jimi.calsync.config.CalendarConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CalDavClient(private val config: CalendarConfig) {
    private val client = HttpClient(OkHttp)
    private val baseUrl = config.url ?: throw IllegalArgumentException("CalDAV URL required")
    private val user = config.username ?: throw IllegalArgumentException("CalDAV username required")
    private val pass = config.password ?: throw IllegalArgumentException("CalDAV password required")
    
    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    
    suspend fun listEvents(timeMin: ZonedDateTime, timeMax: ZonedDateTime): List<CalDavEvent> {
        val propfind = """<?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                <D:prop>
                    <C:calendar-data/>
                    <D:getetag/>
                </D:prop>
            </D:propfind>""".trimIndent()
        
        val response = client.request(baseUrl) {
            method = HttpMethod("PROPFIND")
            basicAuth(user, pass)
            header("Depth", "1")
            header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            setBody(propfind)
        }
        
        return parseCalDavResponse(response.bodyAsText(), timeMin, timeMax)
    }
    
    suspend fun createEvent(uid: String, summary: String, start: ZonedDateTime, end: ZonedDateTime): String {
        val ics = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//CalSync//EN
BEGIN:VEVENT
UID:$uid
DTSTAMP:${ZonedDateTime.now().format(isoFormatter)}
SUMMARY:$summary
DESCRIPTION:Synced by CalSync
DTSTART:${start.format(isoFormatter)}
DTEND:${end.format(isoFormatter)}
END:VEVENT
END:VCALENDAR""".trimIndent()
        
        client.request("$baseUrl/$uid.ics") {
            method = HttpMethod("PUT")
            basicAuth(user, pass)
            contentType(ContentType.Text.Any)
            setBody(ics)
        }
        
        return uid
    }
    
    suspend fun updateEvent(eventId: String, summary: String, start: ZonedDateTime, end: ZonedDateTime) {
        val ics = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//CalSync//EN
BEGIN:VEVENT
UID:$eventId
DTSTAMP:${ZonedDateTime.now().format(isoFormatter)}
SUMMARY:$summary
DESCRIPTION:Synced by CalSync
DTSTART:${start.format(isoFormatter)}
DTEND:${end.format(isoFormatter)}
END:VEVENT
END:VCALENDAR""".trimIndent()
        
        client.request("$baseUrl/$eventId.ics") {
            method = HttpMethod("PUT")
            basicAuth(user, pass)
            contentType(ContentType.Text.Any)
            setBody(ics)
        }
    }
    
    suspend fun deleteEvent(eventId: String) {
        client.request("$baseUrl/$eventId.ics") {
            method = HttpMethod("DELETE")
            basicAuth(user, pass)
        }
    }
    
    private fun parseCalDavResponse(xml: String, timeMin: ZonedDateTime, timeMax: ZonedDateTime): List<CalDavEvent> {
        val events = mutableListOf<CalDavEvent>()
        val uidPattern = "UID:([^\\s]+)".toRegex()
        val summaryPattern = "SUMMARY:([^\\r\\n]+)".toRegex()
        val dtstartPattern = "DTSTART[^:]*:([^\\s]+)".toRegex()
        val dtendPattern = "DTEND[^:]*:([^\\s]+)".toRegex()
        
        val icalBlocks = xml.split("BEGIN:VEVENT").drop(1)
        
        for (block in icalBlocks) {
            val uid = uidPattern.find(block)?.groupValues?.get(1) ?: continue
            val summary = summaryPattern.find(block)?.groupValues?.get(1) ?: "Busy"
            val dtstart = dtstartPattern.find(block)?.groupValues?.get(1)
            val dtend = dtendPattern.find(block)?.groupValues?.get(1)
            
            if (dtstart != null && dtend != null) {
                try {
                    val start = ZonedDateTime.parse(dtstart)
                    val end = ZonedDateTime.parse(dtend)
                    
                    if (start.isBefore(timeMax) && end.isAfter(timeMin)) {
                        events.add(CalDavEvent(uid, summary, null, start, end))
                    }
                } catch (e: Exception) {
                }
            }
        }
        
        return events
    }
}

data class CalDavEvent(
    val id: String,
    val summary: String,
    val description: String?,
    val start: ZonedDateTime,
    val end: ZonedDateTime
)
