package rocks.jimi.busybee.oauth

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URLEncoder

class OAuthHandler(private val httpClient: HttpClient = HttpClient(CIO)) {

    fun buildAuthorizationUrl(clientId: String): String {
        val encodedClientId = URLEncoder.encode(clientId, "UTF-8")
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$encodedClientId&" +
                "redirect_uri=urn:ietf:wg:oauth:2.0:oob&" +
                "response_type=code&" +
                "scope=https://www.googleapis.com/auth/calendar.readonly+https://www.googleapis.com/auth/calendar.events&" +
                "access_type=offline"
    }

    suspend fun verifyCredentials(clientId: String, clientSecret: String): Boolean {
        return try {
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                return false
            }

            val response = httpClient.request("https://oauth2.googleapis.com/token") {
                method = HttpMethod("POST")
                setBody(
                    "client_id=$clientId&" +
                            "client_secret=$clientSecret&" +
                            "code=test&" +
                            "grant_type=authorization_code&" +
                            "redirect_uri=urn:ietf:wg:oauth:2.0:oob"
                )
                header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            }

            val body = response.bodyAsText()
            !body.contains("\"error\": \"invalid_client\"")
        } catch (e: Exception) {
            true
        }
    }

    suspend fun exchangeForRefreshToken(
        clientId: String,
        clientSecret: String,
        authCode: String
    ): OAuthTokenResult {
        return try {
            val response = httpClient.request("https://oauth2.googleapis.com/token") {
                method = HttpMethod("POST")
                setBody(
                    "client_id=$clientId&" +
                            "client_secret=$clientSecret&" +
                            "code=$authCode&" +
                            "grant_type=authorization_code&" +
                            "redirect_uri=urn:ietf:wg:oauth:2.0:oob"
                )
                header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            }

            val body = response.bodyAsText()

            val refreshToken = parseRefreshToken(body)
            when {
                refreshToken != null -> OAuthTokenResult.Success(refreshToken)
                body.contains("invalid_grant") -> OAuthTokenResult.InvalidGrant
                else -> OAuthTokenResult.Error(body)
            }
        } catch (e: Exception) {
            OAuthTokenResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun parseRefreshToken(body: String): String? {
        val regex = Regex("\"refresh_token\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(body)?.groupValues?.get(1)
    }
}

sealed class OAuthTokenResult {
    data class Success(val refreshToken: String) : OAuthTokenResult()
    data object InvalidGrant : OAuthTokenResult()
    data class Error(val message: String) : OAuthTokenResult()
}
