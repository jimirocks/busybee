package rocks.jimi.busybee.api

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import rocks.jimi.busybee.config.CalendarConfig
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
        val report = """<?xml version="1.0" encoding="utf-8" ?>
            <C:calendar-query xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                <D:prop>
                    <C:calendar-data/>
                    <D:getetag/>
                </D:prop>
                <C:filter>
                    <C:comp-filter name="VCALENDAR">
                        <C:comp-filter name="VEVENT">
                            <C:time-range start="${formatICalInstant(timeMin)}" end="${formatICalInstant(timeMax)}"/>
                        </C:comp-filter>
                    </C:comp-filter>
                </C:filter>
            </C:calendar-query>""".trimIndent()
        
        logger.debug { "Fetching CalDAV events from $baseUrl for range $timeMin to $timeMax" }
        
        val response = client.request(baseUrl) {
            method = HttpMethod("REPORT")
            basicAuth(user, pass)
            header("Depth", "1")
            header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            setBody(report)
        }
        
        val events = parseCalDavResponse(response.bodyAsText(), timeMin, timeMax)
        logger.debug { "Found ${events.size} CalDAV events" }
        
        return events
    }
    
    private fun formatICalInstant(instant: Instant): String {
        return instant.toString()
            .replace("-", "")
            .replace(":", "")
            .replace(".", "")
            .take(15) + "Z"
    }

    private fun escapeICalString(value: String?): String {
        return value
            ?.replace("\\", "\\\\")
            ?.replace(",", "\\,")
            ?.replace(";", "\\;")
            ?.replace("\n", "\\n")
            ?: ""
    }
    
    suspend fun createEvent(uid: String, summary: String, description: String?, start: Instant, end: Instant, visibility: String? = null): String {
        val classLine = if (visibility == "private") "CLASS:PRIVATE" else ""
        val escapedSummary = escapeICalString(summary)
        val escapedDescription = escapeICalString(description)
        val ics = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//BusyBee//EN")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:${formatICalInstant(Clock.System.now())}")
            appendLine("SUMMARY:$escapedSummary")
            appendLine("DESCRIPTION:$escapedDescription")
            appendLine("DTSTART:${formatICalInstant(start)}")
            appendLine("DTEND:${formatICalInstant(end)}")
            if (classLine.isNotEmpty()) appendLine(classLine)
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
        
        logger.debug { "Creating CalDAV event: uid=$uid, summary=$escapedSummary" }
        
        val response = client.request("$baseUrl/$uid.ics") {
            method = HttpMethod("PUT")
            basicAuth(user, pass)
            contentType(ContentType.parse("text/calendar; charset=utf-8"))
            setBody(ics)
        }

        if (!response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            logger.error { "CalDAV create failed: status=${response.status}, body=$responseBody, ics=\n$ics" }
            throw IllegalStateException("Failed to create CalDAV event: ${response.status} - $responseBody")
        }
        
        return uid
    }
    
    suspend fun updateEvent(eventId: String, summary: String, description: String?, start: Instant, end: Instant, visibility: String? = null) {
        val classLine = if (visibility == "private") "CLASS:PRIVATE" else ""
        val escapedSummary = escapeICalString(summary)
        val escapedDescription = escapeICalString(description)
        val ics = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//BusyBee//EN")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$eventId")
            appendLine("DTSTAMP:${formatICalInstant(Clock.System.now())}")
            appendLine("SUMMARY:$escapedSummary")
            appendLine("DESCRIPTION:$escapedDescription")
            appendLine("DTSTART:${formatICalInstant(start)}")
            appendLine("DTEND:${formatICalInstant(end)}")
            if (classLine.isNotEmpty()) appendLine(classLine)
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
        
        logger.debug { "Updating CalDAV event: eventId=$eventId, summary=$escapedSummary" }

        val response = client.request("$baseUrl/$eventId.ics") {
            method = HttpMethod("PUT")
            basicAuth(user, pass)
            contentType(ContentType.parse("text/calendar; charset=utf-8"))
            setBody(ics)
        }

        if (!response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            logger.error { "CalDAV update failed: status=${response.status}, body=$responseBody, ics=\n$ics" }
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
