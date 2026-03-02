package rocks.jimi.busybee.api

import com.google.auth.oauth2.UserCredentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rocks.jimi.busybee.config.CalendarConfig
import java.io.File
import java.util.Date

class TokenManager(private val config: CalendarConfig) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val tokenFile = File(config.tokenFile)
    
    @Volatile
    private var credentials: UserCredentials? = null
    
    fun getCredentials(): UserCredentials {
        credentials?.let { return it }
        
        synchronized(this) {
            credentials?.let { return it }
            
            val tokenData = loadTokenData()
            credentials = createCredentials(tokenData)
            
            return credentials!!
        }
    }
    
    fun refreshToken() {
        synchronized(this) {
            try {
                credentials?.refresh()
            } catch (e: Exception) {
                credentials = null
                throw InvalidTokenException(config.id, "Refresh token expired or revoked. Please re-authorize with Google.")
            }
        }
    }

    fun clearCredentials() {
        synchronized(this) {
            credentials = null
        }
    }
    
    private fun createCredentials(tokenData: TokenData): UserCredentials {
        return UserCredentials.newBuilder()
            .setClientId(tokenData.clientId)
            .setClientSecret(tokenData.clientSecret)
            .setRefreshToken(tokenData.refreshToken)
            .build()
    }
    
    private fun loadTokenData(): TokenData {
        if (!tokenFile.exists()) {
            throw IllegalStateException("Token file not found: ${tokenFile.absolutePath}")
        }
        
        val content = tokenFile.readText()
        return try {
            json.decodeFromString<TokenData>(content)
        } catch (e: Exception) {
            throw IllegalStateException("Invalid token file format: ${e.message}")
        }
    }
}

@Serializable
data class TokenData(
    val refreshToken: String,
    val clientId: String,
    val clientSecret: String
)
