package rocks.jimi.busybee.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SyncEngineTest : StringSpec({

    val testCalendarIds = setOf(
        "user.name@companyA.com",
        "user.name@companyB.com",
        "caldav_calendar",
        "user.name@companyC.com"
    )

    "returns false for null description" {
        SyncIdPattern.isMatch(null, testCalendarIds) shouldBe false
    }

    "returns false for empty description" {
        SyncIdPattern.isMatch("", testCalendarIds) shouldBe false
    }

    "returns false for single part (no underscore)" {
        SyncIdPattern.isMatch("justOnePart", testCalendarIds) shouldBe false
    }

    "returns false for unknown calendar ID" {
        SyncIdPattern.isMatch("unknown_12345", testCalendarIds) shouldBe false
    }

    "returns false for unknown calendar ID with multiple parts" {
        SyncIdPattern.isMatch("unknown_12345_extra", testCalendarIds) shouldBe false
    }

    "returns true for synced event from Google to CalDAV" {
        val calendarIds = setOf("user.name@companyA.com", "caldav_calendar")
        val result = SyncIdPattern.isMatch("user.name@companyA.com_64qud5jqocfdp2se449ema05pc", calendarIds)
        result shouldBe true
    }

    "returns true for synced event from CalDAV to Google" {
        SyncIdPattern.isMatch("caldav_calendar_c763298e-6cda-40c5-86bb-f5eea21235a3", testCalendarIds) shouldBe true
    }

    "returns true for synced event with timestamp suffix" {
        val calendarIds = setOf("user.name@companyA.com")
        SyncIdPattern.isMatch("user.name@companyA.com_64qud5jqocfdp2se449ema05pc_20260217T193000Z", calendarIds) shouldBe true
    }

    "returns true for all configured Google calendars" {
        val calendarIds = setOf("user.name@companyB.com", "user.name@companyC.com")
        SyncIdPattern.isMatch("user.name@companyB.com_event123", calendarIds) shouldBe true
        SyncIdPattern.isMatch("user.name@companyC.com_event456", calendarIds) shouldBe true
    }

    "returns true for known CalDAV calendar ID" {
        SyncIdPattern.isMatch("caldav_calendar_3uio02a6g7hq2e7vdorthbtmlv", testCalendarIds) shouldBe true
    }

    "returns false for external attendee event from unknown calendar" {
        SyncIdPattern.isMatch("external_calendar_12345", testCalendarIds) shouldBe false
    }

    "returns false for empty calendar ID part" {
        SyncIdPattern.isMatch("_event123", testCalendarIds) shouldBe false
    }

    "returns false for empty event ID part" {
        SyncIdPattern.isMatch("user.name@companyA.com_", testCalendarIds) shouldBe false
    }

    "returns false for description with only underscores" {
        SyncIdPattern.isMatch("___", testCalendarIds) shouldBe false
    }

    "returns true when calendar ID is at start of description with multiple underscores" {
        val calendarIds = setOf("user.name@companyA.com")
        SyncIdPattern.isMatch("user.name@companyA.com_eventId_20260217", calendarIds) shouldBe true
    }
})
