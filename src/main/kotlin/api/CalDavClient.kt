package rocks.jimi.calsync.api

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import rocks.jimi.calsync.config.CalendarConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Instant

class CalDavClient(private val config: CalendarConfig) {
    private val logger = KotlinLogging.logger { }
    private val client = HttpClient(CIO)
    private val baseUrl = config.url ?: throw IllegalArgumentException("CalDAV URL required")
    private val user = config.username ?: throw IllegalArgumentException("CalDAV username required")
    private val pass = config.password ?: throw IllegalArgumentException("CalDAV password required")
    
    suspend fun listEvents(timeMin: Instant, timeMax: Instant): List<CalDavEvent> {
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
    
    private fun formatICalInstant(instant: Instant): String {
        return instant.toString()
            .replace("-", "")
            .replace(":", "")
            .replace(".", "")
            .take(15) + "Z"
    }
    
    suspend fun createEvent(uid: String, summary: String, description: String?, start: Instant, end: Instant): String {
        val ics = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//CalSync//EN
BEGIN:VEVENT
UID:$uid
DTSTAMP:${formatICalInstant(Clock.System.now())}
SUMMARY:$summary
DESCRIPTION:$description
DTSTART:${formatICalInstant(start)}
DTEND:${formatICalInstant(end)}
END:VEVENT
END:VCALENDAR""".trimIndent()
        
        val response = client.request("$baseUrl/$uid.ics") {
            method = HttpMethod("PUT")
            basicAuth(user, pass)
            contentType(ContentType.Text.Any)
            setBody(ics)
        }
        
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to create CalDAV event: ${response.status}")
        }
        
        return uid
    }
    
    suspend fun updateEvent(eventId: String, summary: String, description: String?, start: Instant, end: Instant) {
        val ics = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//CalSync//EN
BEGIN:VEVENT
UID:$eventId
DTSTAMP:${formatICalInstant(Clock.System.now())}
SUMMARY:$summary
DESCRIPTION:$description
DTSTART:${formatICalInstant(start)}
DTEND:${formatICalInstant(end)}
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
    
    private fun parseCalDavResponse(xml: String, timeMin: Instant, timeMax: Instant): List<CalDavEvent> {
        val events = mutableListOf<CalDavEvent>()
        
        val unfolded = xml.replace(Regex("[\r\n][ \t]+"), "")
        
        val uidPattern = "UID:([^\\s]+)".toRegex()
        val summaryPattern = "SUMMARY:([^\\r\\n]+)".toRegex()
        val descriptionPattern = "DESCRIPTION:([^\\r\\n]+)".toRegex()
        val statusPattern = "STATUS:([^\\r\\n]+)".toRegex()
        val dtstartPattern = "DTSTART(?:;TZID=([^:]+))?:([^\\s]+)".toRegex()
        val dtendPattern = "DTEND(?:;TZID=([^:]+))?:([^\\s]+)".toRegex()
        
        val icalBlocks = unfolded.split("BEGIN:VEVENT").drop(1)
        
        for (block in icalBlocks) {
            val status = statusPattern.find(block)?.groupValues?.get(1)
            if (status != null && status != "CONFIRMED") continue

            val uid = uidPattern.find(block)?.groupValues?.get(1) ?: continue
            val summary = summaryPattern.find(block)?.groupValues?.get(1) ?: "Busy"
            val description = descriptionPattern.find(block)?.groupValues?.get(1)
            
            val dtstartMatch = dtstartPattern.find(block)
            val dtendMatch = dtendPattern.find(block)
            
            if (dtstartMatch != null && dtendMatch != null) {
                val tzid = dtstartMatch.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                val dtstartValue = dtstartMatch.groupValues.getOrNull(2)
                val dtendValue = dtendMatch.groupValues.getOrNull(2)
                
                if (dtstartValue != null && dtendValue != null) {
                    try {
                        val start = parseICalDateTime(dtstartValue, tzid)
                        val end = parseICalDateTime(dtendValue, tzid)
                        
                        if (start < timeMax && end > timeMin) {
                            events.add(CalDavEvent(uid, summary, description, start, end))
                        }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to parse CalDAV event: uid=$uid, dtstart=$dtstartValue, dtend=$dtendValue, tzid=$tzid" }
                    }
                }
            }
        }
        
        return events
    }
    
    private fun parseICalDateTime(value: String, tzid: String?): Instant {
        val localDateTime = LocalDateTime(
            value.substring(0, 4).toInt(),
            value.substring(4, 6).toInt(),
            value.substring(6, 8).toInt(),
            value.substring(9, 11).toInt(),
            value.substring(11, 13).toInt(),
            value.substring(13, 15).toInt()
        )
        
        return if (tzid != null) {
            val timeZone = TimeZone.of(tzid)
            localDateTime.toInstant(timeZone)
        } else {
            localDateTime.toInstant(TimeZone.UTC)
        }
    }
}

data class CalDavEvent(
    val id: String,
    val summary: String,
    val description: String?,
    val start: Instant,
    val end: Instant
)
