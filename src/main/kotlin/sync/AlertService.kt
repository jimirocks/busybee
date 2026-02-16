package rocks.jimi.calsync.sync

import rocks.jimi.calsync.config.AlertConfig

class AlertService(private val config: AlertConfig) {
    
    fun sendAlert(subject: String, body: String) {
        if (!config.enabled || config.email == null) return
        
        try {
            val props = java.util.Properties()
            props.setProperty("mail.smtp.host", config.email.host)
            props.setProperty("mail.smtp.port", config.email.port.toString())
            props.setProperty("mail.smtp.auth", "true")
            props.setProperty("mail.smtp.starttls.enable", "true")
            
            val session = javax.mail.Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): javax.mail.PasswordAuthentication {
                    return javax.mail.PasswordAuthentication(config.email.username, config.email.password)
                }
            })
            
            val message = javax.mail.internet.MimeMessage(session)
            message.setFrom(javax.mail.internet.InternetAddress(config.email.username))
            message.addRecipient(javax.mail.Message.RecipientType.TO, javax.mail.internet.InternetAddress(config.email.to))
            message.subject = subject
            message.setText(body)
            
            javax.mail.Transport.send(message)
        } catch (e: Exception) {
            System.err.println("Failed to send alert email: ${e.message}")
        }
    }
    
    fun sendSyncFailureAlert(error: String) {
        sendAlert("CalSync Alert: Sync Failed", "Synchronization failed with error: $error")
    }
    
    fun sendSyncSuccessAlert(eventsProcessed: Int) {
        sendAlert("CalSync: Sync Completed", "Processed $eventsProcessed events successfully.")
    }
}
