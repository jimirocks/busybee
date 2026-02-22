package rocks.jimi.busybee.sync

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import rocks.jimi.busybee.CalendarEvent
import rocks.jimi.busybee.SyncState
import rocks.jimi.busybee.api.CalDavClient
import rocks.jimi.busybee.api.GoogleCalendarClient
import rocks.jimi.busybee.api.TokenManager
import rocks.jimi.busybee.config.CalendarConfig
import rocks.jimi.busybee.config.Config
import java.io.File
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SyncEngine(private val config: Config) {
    private val logger = KotlinLogging.logger { }
    private val prefix = config.sync.prefix
    private val tokenManagers = config.calendars
        .filter { it.type == "google" }
        .associate { it.id to TokenManager(it) }
    private val clients = config.calendars.associate { it.id to createClient(it) }
    private val stateFile = "sync-state.json"
    private val json = Json { prettyPrint = true }
    private val state: MutableMap<String, SyncState> by lazy { loadState() }

    private fun effectivePrefix(calId: String): String {
        val cal = config.calendars.find { it.id == calId }
        return if (cal?.shortcut != null) {
            "$prefix-${cal.shortcut}"
        } else {
            prefix
        }
    }
    
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
        logger.info { "Starting sync" }
        val now = Clock.System.now()
        val timeMin = now.minus(7.days)
        val timeMax = now.plus(30.days)
        
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
        logger.info { "Sync completed" }
    }
    
    private suspend fun fetchEvents(cfg: CalendarConfig, timeMin: Instant, timeMax: Instant): List<CalendarEvent> {
        val client = clients[cfg.id]!!
        val effPrefix = effectivePrefix(cfg.id)
        val events: List<CalendarEvent> = when (client) {
            is GoogleCalendarClient -> safeGoogleCall { client.listEvents(timeMin, timeMax) }.map { e ->
                CalendarEvent(
                    id = e.id,
                    summary = e.summary,
                    description = e.description,
                    start = e.start,
                    end = e.end,
                    calendarId = cfg.id,
                    isOriginal = !e.summary.startsWith(effPrefix),
                    syncId = if (e.summary.startsWith(effPrefix)) e.description else null
                )
            }
            is CalDavClient -> safeCalDavCall { client.listEvents(timeMin, timeMax) }?.map { e ->
                CalendarEvent(
                    id = e.id,
                    summary = e.summary,
                    description = e.description,
                    start = e.start,
                    end = e.end,
                    calendarId = cfg.id,
                    isOriginal = !e.summary.startsWith(effPrefix),
                    syncId = if (e.summary.startsWith(effPrefix)) e.description else null
                )
            } ?: emptyList()
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
    
    private suspend fun <T> safeCalDavCall(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            logger.error(e) { "CalDAV operation failed" }
            null
        }
    }
    
    private suspend fun syncCalendar(sourceCalId: String, allEvents: MutableMap<String, MutableList<CalendarEvent>>) {
        logger.info { "Syncing calendar: $sourceCalId" }
        val sourceEvents = allEvents[sourceCalId] ?: return
        val originalEvents = sourceEvents.filter { it.isOriginal }
        val effPrefix = effectivePrefix(sourceCalId)

        for (original in originalEvents) {
            val syncId = "${sourceCalId}_${original.id}"

            for (targetCal in config.calendars) {
                if (targetCal.id == sourceCalId) continue

                val targetKey = "${targetCal.id}_$syncId"
                val targetEvents = allEvents[targetCal.id] ?: continue
                val existingTargetEvent = targetEvents.find { it.syncId == syncId }

                when {
                    existingTargetEvent != null -> {
                        if (existingTargetEvent.summary != "$effPrefix ${original.summary}" ||
                            existingTargetEvent.start != original.start ||
                            existingTargetEvent.end != original.end) {
                            updateSyncEvent(targetCal.id, existingTargetEvent.id, original, syncId, sourceCalId)
                        }
                    }
                    else -> {
                        createSyncEvent(targetCal.id, original, syncId, allEvents, sourceCalId)
                    }
                }
            }
        }
    }
    
    private suspend fun createSyncEvent(
        targetCalId: String,
        original: CalendarEvent,
        syncId: String,
        allEvents: MutableMap<String, MutableList<CalendarEvent>>,
        sourceCalId: String
    ) {
        val client = clients[targetCalId]!!
        val effPrefix = effectivePrefix(sourceCalId)

        val newEventId: String? = when (client) {
            is GoogleCalendarClient -> safeGoogleCall {
                client.createEvent("$effPrefix ${original.summary}", syncId, original.start, original.end)
            }
            is CalDavClient -> safeCalDavCall {
                client.createEvent(
                    UUID.randomUUID().toString(),
                    "$effPrefix ${original.summary}",
                    syncId,
                    original.start,
                    original.end
                )
            }
            else -> return
        }

        if (newEventId == null) {
            logger.warn { "Failed to create sync event on $targetCalId" }
            return
        }

        logger.debug { "Created sync event on $targetCalId: $newEventId" }

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
                summary = "$effPrefix ${original.summary}",
                description = syncId,
                start = original.start,
                end = original.end,
                calendarId = targetCalId,
                isOriginal = false,
                syncId = syncId
            )
        )
    }
    
    private suspend fun updateSyncEvent(targetCalId: String, eventId: String, original: CalendarEvent, syncId: String, sourceCalId: String) {
        val client = clients[targetCalId]!!
        val effPrefix = effectivePrefix(sourceCalId)
        try {
            when (client) {
                is GoogleCalendarClient -> safeGoogleCall {
                    client.updateEvent(eventId, "$effPrefix ${original.summary}", syncId, original.start, original.end)
                }
                is CalDavClient -> client.updateEvent(
                    eventId,
                    "$effPrefix ${original.summary}",
                    syncId,
                    original.start,
                    original.end
                )
            }
            logger.debug { "Updated sync event on $targetCalId: $eventId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update sync event on $targetCalId: $eventId" }
        }
    }
    
    private suspend fun cleanupDeletedEvents(allEvents: Map<String, MutableList<CalendarEvent>>) {
        val toRemove = mutableListOf<String>()
        
        for ((key, syncState) in state) {
            val sourceEvents = allEvents[syncState.sourceCalendarId] ?: continue
            val sourceExists = sourceEvents.any { it.id == syncState.sourceEventId && it.isOriginal }
            
            if (!sourceExists) {
                val client = clients[syncState.targetCalendarId]
                try {
                    when (client) {
                        is GoogleCalendarClient -> safeGoogleCall { client.deleteEvent(syncState.targetEventId) }
                        is CalDavClient -> client.deleteEvent(syncState.targetEventId)
                    }
                    logger.debug { "Deleted sync event on ${syncState.targetCalendarId}: ${syncState.targetEventId}" }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to delete sync event on ${syncState.targetCalendarId}: ${syncState.targetEventId}" }
                }
                toRemove.add(key)
            }
        }
        
        toRemove.forEach { state.remove(it) }
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
