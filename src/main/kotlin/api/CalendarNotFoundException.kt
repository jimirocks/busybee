package rocks.jimi.busybee.api

class CalendarNotFoundException(
    val calendarId: String,
    message: String
) : Exception(message)
