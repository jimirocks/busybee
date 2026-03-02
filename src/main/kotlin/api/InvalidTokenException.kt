package rocks.jimi.busybee.api

class InvalidTokenException(
    val calendarId: String,
    message: String
) : Exception(message)
