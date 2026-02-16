package rocks.jimi.calsync

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.CalendarScopes
import java.io.File
import java.io.InputStreamReader

fun main(args: Array<String>) {
    if (args.firstOrNull() != "auth") {
        println("Usage: calsync auth <calendar-id>")
        println("  calendar-id: personal, work1, work2 (from config.yaml)")
        return
    }
    
    val calId = args.getOrNull(1) ?: "personal"
    println("Setting up OAuth for calendar: $calId")
    println("\nThis tool requires client_secrets.json from Google Cloud Console")
    println("Get it at: https://console.cloud.google.com/apis/credentials\n")
    
    val secretsFile = File("client_secrets.json")
    if (!secretsFile.exists()) {
        println("ERROR: client_secrets.json not found")
        println("Download it from Google Cloud Console and place in project root")
        return
    }
    
    val secrets = GoogleClientSecrets.load(
        GsonFactory.getDefaultInstance(),
        InputStreamReader(secretsFile.inputStream())
    )
    
    val flow = GoogleAuthorizationCodeFlow.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        secrets,
        listOf(CalendarScopes.CALENDAR)
    ).build()
    
    println("1. Open this URL in your browser:")
    println(flow.newAuthorizationUrl().setRedirectUri("http://localhost:8080/callback").build())
    println("\n2. After authorizing, you'll be redirected to a localhost URL")
    println("3. Copy the 'code' parameter from the URL")
    println("4. Run: java -cp build/libs/calsync.jar rocks.jimi.calsync.AuthTool <code> $calId")
    
    println("\nAlternatively, manually create tokens/$calId.json with your refresh token.")
}
