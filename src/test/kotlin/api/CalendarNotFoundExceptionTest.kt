package rocks.jimi.busybee.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class CalendarNotFoundExceptionTest : StringSpec({

    "CalendarNotFoundException stores calendarId" {
        val exception = CalendarNotFoundException(
            calendarId = "test@example.com",
            message = "Calendar test@example.com not found"
        )

        exception.calendarId shouldBe "test@example.com"
        exception.message shouldContain "test@example.com"
        exception.message shouldContain "not found"
    }

    "CalendarNotFoundException is an Exception" {
        val exception = CalendarNotFoundException("calId", "msg")
        exception shouldNotBe null
    }
})
