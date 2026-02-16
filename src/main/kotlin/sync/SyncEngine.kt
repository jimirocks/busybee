package rocks.jimi.calsync.sync

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import rocks.jimi.calsync.api.GoogleCalendarClient
import rocks.jimi.calsync.api.CalDavClient
import rocks.jimi.calsync.api.TokenManager
import rocks.jimi.calsync.config.CalendarConfig
import rocks.jimi.calsync.CalendarEvent
import rocks.jimi.calsync.config.Config
import rocks.jimi.calsync.SyncState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZonedDateTime
import java.util.UUID

class SyncEngine(private val config: Config) {
    private val prefix = config.sync.prefix
    private val clients = config.calendars.associate { it.id to createClient(it) }
    private val tokenManagers = config.calendars
        .filter { it.type == "google" }
        .associate { it.id to TokenManager(it) }
    private val stateFile = "sync-state.json"
    private val state: MutableMap<String, SyncState> = loadState()
    private val json = Json { prettyPrint = true }
    
    private fun createClient(cfg: CalendarConfig): Any {
        return when (cfg.type) {
            "google" -> {
                val tokenManager = tokenManagers[cfg.id] 
                    ?: throw IllegalStateException("No token manager for ${cfg.id}")
                GoogleCalendarClient(cfg, tokenManager)
            }
            "caldav" -> CalDavClient(cfg)
            else -> throw IllegalArgumentException("Unknown calendar type: ${cfg.type}")
        }
    }
    
    fun runSync() {
        runBlocking {
            doSync()
        }
    }
    
    private suspend fun doSync() {
        val now = ZonedDateTime.now()
        val timeMin = now.minusDays(1)
        val timeMax = now.plusDays(config.sync.intervalMinutes.toLong() + 30)
        
        val allEvents = mutableMapOf<String, MutableList<CalendarEvent>>()
        
        for (cal in config.calendars) {
            val events = fetchEvents(cal, timeMin, timeMax)
            allEvents[cal.id] = events.toMutableList()
        }
        
        for (cal in config.calendars) {
            syncCalendar(cal.id, allEvents)
        }
        
        cleanupDeletedEvents(allEvents)
        saveState()
    }
    
    private suspend fun fetchEvents(cfg: CalendarConfig, timeMin: ZonedDateTime, timeMax: ZonedDateTime): List<CalendarEvent> {
        val client = clients[cfg.id]!!
        val events = when (client) {
            is GoogleCalendarClient -> safeGoogleCall { client.listEvents(timeMin, timeMax) }.map { e ->
                CalendarEvent(
                    id = e.id,
                    summary = e.summary,
                    description = e.description,
                    start = e.start,
                    end = e.end,
                    calendarId = cfg.id,
                    isOriginal = !e.summary.startsWith(prefix),
                    syncId = if (e.summary.startsWith(prefix)) extractSyncId(e.summary) else null
                )
            }
            is CalDavClient -> client.listEvents(timeMin, timeMax).map { e ->
                CalendarEvent(
                    id = e.id,
                    summary = e.summary,
                    description = e.description,
                    start = e.start,
                    end = e.end,
                    calendarId = cfg.id,
                    isOriginal = !e.summary.startsWith(prefix),
                    syncId = if (e.summary.startsWith(prefix)) extractSyncId(e.summary) else null
                )
            }
            else -> emptyList()
        }
        return events
    }
    
    private fun <T> safeGoogleCall(block: () -> T): T {
        return try {
            block()
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == 401) {
                tokenManagers.entries.find { (clients[it.key] as? GoogleCalendarClient) != null }?.let { (_, tm) ->
                    tm.refreshToken()
                }
                block()
            } else {
                throw e
            }
        }
    }
    
    private suspend fun syncCalendar(sourceCalId: String, allEvents: MutableMap<String, MutableList<CalendarEvent>>) {
        val sourceEvents = allEvents[sourceCalId] ?: return
        val originalEvents = sourceEvents.filter { it.isOriginal }
        
        for (original in originalEvents) {
            val syncId = "${sourceCalId}_${original.id}"
            
            for (targetCal in config.calendars) {
                if (targetCal.id == sourceCalId) continue
                
                val targetKey = "${targetCal.id}_$syncId"
                val targetEvents = allEvents[targetCal.id] ?: continue
                val existingTargetEvent = targetEvents.find { it.syncId == syncId }
                
                when {
                    existingTargetEvent != null -> {
                        if (existingTargetEvent.summary != "$prefix ${original.summary}" ||
                            existingTargetEvent.start != original.start ||
                            existingTargetEvent.end != original.end) {
                            updateSyncEvent(targetCal.id, existingTargetEvent.id, original)
                        }
                    }
                    else -> {
                        createSyncEvent(targetCal.id, original, syncId, allEvents)
                    }
                }
            }
        }
    }
    
    private suspend fun createSyncEvent(
        targetCalId: String,
        original: CalendarEvent,
        syncId: String,
        allEvents: MutableMap<String, MutableList<CalendarEvent>>
    ) {
        val client = clients[targetCalId]!!
        val newEventId = when (client) {
            is GoogleCalendarClient -> safeGoogleCall {
                client.createEvent("$prefix ${original.summary}", original.start, original.end)
            }
            is CalDavClient -> client.createEvent(
                UUID.randomUUID().toString(),
                "$prefix ${original.summary}",
                original.start,
                original.end
            )
            else -> return
        }
        
        val targetKey = "${targetCalId}_$syncId"
        state[targetKey] = SyncState(
            sourceCalendarId = original.calendarId,
            sourceEventId = original.id,
            targetCalendarId = targetCalId,
            targetEventId = newEventId,
            createdAt = System.currentTimeMillis()
        )
        
        allEvents.getOrPut(targetCalId) { mutableListOf() }.add(
            CalendarEvent(
                id = newEventId,
                summary = "$prefix ${original.summary}",
                description = null,
                start = original.start,
                end = original.end,
                calendarId = targetCalId,
                isOriginal = false,
                syncId = syncId
            )
        )
    }
    
    private suspend fun updateSyncEvent(targetCalId: String, eventId: String, original: CalendarEvent) {
        val client = clients[targetCalId]!!
        when (client) {
            is GoogleCalendarClient -> safeGoogleCall {
                client.updateEvent(eventId, "$prefix ${original.summary}", original.start, original.end)
            }
            is CalDavClient -> client.updateEvent(
                eventId,
                "$prefix ${original.summary}",
                original.start,
                original.end
            )
        }
    }
    
    private suspend fun cleanupDeletedEvents(allEvents: Map<String, MutableList<CalendarEvent>>) {
        val toRemove = mutableListOf<String>()
        
        for ((key, syncState) in state) {
            val sourceEvents = allEvents[syncState.sourceCalendarId] ?: continue
            val sourceExists = sourceEvents.any { it.id == syncState.sourceEventId && it.isOriginal }
            
            if (!sourceExists) {
                val client = clients[syncState.targetCalendarId]
                when (client) {
                    is GoogleCalendarClient -> safeGoogleCall { client.deleteEvent(syncState.targetEventId) }
                    is CalDavClient -> client.deleteEvent(syncState.targetEventId)
                }
                toRemove.add(key)
            }
        }
        
        toRemove.forEach { state.remove(it) }
    }
    
    private fun extractSyncId(summary: String): String? {
        return summary.removePrefix(prefix).trim().let {
            if (it.isNotEmpty()) it else null
        }
    }
    
    private fun loadState(): MutableMap<String, SyncState> {
        return try {
            val content = File(stateFile).readText()
            json.decodeFromString<Map<String, SyncState>>(content).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }
    
    private fun saveState() {
        val jsonStr = json.encodeToString(state.toMap())
        File(stateFile).writeText(jsonStr)
    }
}
