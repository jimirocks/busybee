package rocks.jimi.busybee.cli

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import rocks.jimi.busybee.config.CalendarConfig
import rocks.jimi.busybee.config.Config
import rocks.jimi.busybee.config.OAuthConfig
import rocks.jimi.busybee.config.SyncConfig
import java.io.File

class ConfigSerializer(private val path: String = rocks.jimi.busybee.config.ConfigLoader.getDefaultConfigPath()) {

    companion object {
        fun getDefaultTokenDir(): String {
            val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?: File(System.getProperty("user.home"), ".config").absolutePath
            return File(configHome, "busybee/tokens").absolutePath
        }
    }

    fun save(config: Config) {
        val yaml = Yaml()
        val map = buildMap {
            config.oauth?.let { oauth ->
                put("oauth", mapOf(
                    "clientId" to oauth.clientId,
                    "clientSecret" to oauth.clientSecret
                ))
            }
            
            put("calendars", config.calendars.map { cal ->
                buildMap {
                    put("id", cal.id)
                    put("type", cal.type)
                    cal.calendarId?.let { put("calendarId", it) }
                    cal.url?.let { put("url", it) }
                    cal.username?.let { put("username", it) }
                    cal.password?.let { put("password", it) }
                    if (cal.tokenFile.isNotEmpty()) {
                        put("tokenFile", cal.tokenFile)
                    }
                    cal.shortcut?.let { put("shortcut", it) }
                }
            })
            
            put("sync", mapOf(
                "intervalMinutes" to config.sync.intervalMinutes,
                "prefix" to config.sync.prefix
            ))
        }

        val yamlStr = yaml.dump(map)
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(yamlStr)
    }

    fun load(): Config {
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
                prefix = (syncMap["prefix"] as? String) ?: "[BB]"
            )
        } ?: SyncConfig()
        
        val oauth = (map["oauth"] as? Map<String, Any>)?.let { oauthMap ->
            OAuthConfig(
                clientId = oauthMap["clientId"] as String,
                clientSecret = oauthMap["clientSecret"] as String
            )
        }
        
        return Config(calendars, sync, oauth, null, null)
    }
}
