package rocks.jimi.busybee.api

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import rocks.jimi.busybee.cli.GoogleCalendarInfo

class GoogleCalendarService {

    fun listCalendars(
        clientId: String,
        clientSecret: String,
        refreshToken: String
    ): List<GoogleCalendarInfo> {
        return try {
            val credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build()

            val httpTransport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val requestInitializer = HttpCredentialsAdapter(credentials)

            val service = Calendar.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName("BusyBee")
                .build()

            val list = service.calendarList().list().execute()

            list.items?.map { entry ->
                GoogleCalendarInfo(
                    id = entry.id,
                    summary = entry.summary ?: ""
                )
            } ?: emptyList()
        } catch (e: Exception) {
            println("Error fetching calendars: ${e.message}")
            emptyList()
        }
    }
}
