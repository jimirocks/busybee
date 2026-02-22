package rocks.jimi.busybee.config

import kotlinx.serialization.Serializable

@Serializable
data class CalendarConfig(
    val id: String,
    val type: String,
    val calendarId: String? = null,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val tokenFile: String = "",
    val shortcut: String? = null
)

@Serializable
data class Config(
    val calendars: List<CalendarConfig> = emptyList(),
    val sync: SyncConfig = SyncConfig(),
    val oauth: OAuthConfig? = null,
    val logging: LoggingConfig? = null,
    val alerts: AlertConfig? = null
)

@Serializable
data class OAuthConfig(
    val clientId: String,
    val clientSecret: String
)

@Serializable
data class SyncConfig(
    val intervalMinutes: Int = 15,
    val prefix: String = "[SYNC]"
)

@Serializable
data class LoggingConfig(
    val level: String = "INFO"
)

@Serializable
data class AlertConfig(
    val enabled: Boolean = false,
    val email: EmailAlertConfig? = null
)

@Serializable
data class EmailAlertConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val to: String
)
