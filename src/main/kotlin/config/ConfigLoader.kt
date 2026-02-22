package rocks.jimi.busybee.config

import org.yaml.snakeyaml.Yaml
import java.io.File

object ConfigLoader {
    private const val APP_NAME = "busybee"
    private const val CONFIG_FILE = "config.yaml"

    fun getDefaultConfigPath(): String {
        val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("user.home"), ".config").absolutePath
        return File(configHome, "$APP_NAME/$CONFIG_FILE").absolutePath
    }

    fun load(path: String = getDefaultConfigPath()): Config {
        val yaml = Yaml()
        val input = File(path).readText()
        val map = yaml.load<Map<String, Any>>(input)
        
        val calendars = (map["calendars"] as? List<Map<String, Any>>)?.map { c ->
            CalendarConfig(
                id = c["id"] as String,
                type = c["type"] as String,
                calendarId = c["calendarId"] as? String,
                url = c["url"] as? String,
                username = c["username"] as? String,
                password = c["password"] as? String,
                tokenFile = c["tokenFile"] as? String ?: "",
                shortcut = c["shortcut"] as? String
            )
        } ?: emptyList()
        
        val sync = (map["sync"] as? Map<String, Any>)?.let { syncMap ->
            SyncConfig(
                intervalMinutes = (syncMap["intervalMinutes"] as? Int) ?: 15,
                prefix = (syncMap["prefix"] as? String) ?: "[SYNC]"
            )
        } ?: SyncConfig()
        
        val oauth = (map["oauth"] as? Map<String, Any>)?.let { oauthMap ->
            OAuthConfig(
                clientId = oauthMap["clientId"] as String,
                clientSecret = oauthMap["clientSecret"] as String
            )
        }
        
        var alerts: AlertConfig? = null
        map["alerts"]?.let { a ->
            val alertMap = a as Map<String, Any>
            val emailMap = alertMap["email"] as? Map<String, Any>
            alerts = AlertConfig(
                enabled = (alertMap["enabled"] as? Boolean) ?: false,
                email = emailMap?.let {
                    EmailAlertConfig(
                        host = it["host"] as String,
                        port = (it["port"] as Number).toInt(),
                        username = it["username"] as String,
                        password = it["password"] as String,
                        to = it["to"] as String
                    )
                }
            )
        }
        
        var logging: LoggingConfig? = null
        map["logging"]?.let { l ->
            val logMap = l as Map<String, Any>
            logging = LoggingConfig(
                level = (logMap["level"] as? String) ?: "INFO"
            )
        }
        
        return Config(calendars, sync, oauth, logging, alerts)
    }
}
