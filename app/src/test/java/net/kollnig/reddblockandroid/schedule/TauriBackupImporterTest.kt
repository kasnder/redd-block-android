package net.kollnig.reddblockandroid.schedule

import net.kollnig.reddblockandroid.data.ScheduleTiming
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TauriBackupImporterTest {
    @Test
    fun importsV2WrapperAndCreatesDisabledDraft() {
        val schedules = importer().importSchedules(
            """
            {
              "format": "redd-block-rules",
              "formatVersion": 2,
              "blocklists": [{
                "name": "Work",
                "mode": "blocklist",
                "apps": ["com.example.app"],
                "websites": ["example.com"],
                "overrideDifficulty": {"count": 25},
                "schedule": {"segments": [{"startHour": 9, "startMinute": 0, "endHour": 17, "endMinute": 30, "days": [0, 2]}]}
              }]
            }
            """.trimIndent()
        )!!

        assertEquals(1, schedules.size)
        assertEquals("id-1", schedules.single().id)
        assertEquals("Work", schedules.single().name)
        assertEquals(listOf("com.example.app"), schedules.single().blockedApps)
        assertEquals(listOf("example.com"), schedules.single().blockedWebsites)
        assertEquals(25, schedules.single().frictionWordCount)
        assertFalse(schedules.single().isEnabled)
        assertEquals(ScheduleTiming.ScheduleType.WEEKLY, schedules.single().timing.type)
    }

    @Test
    fun mapsDaysMondayFirstAndDeduplicates() {
        val schedule = importer().importSchedules(wrapper(
            """{"name":"Days","apps":["a"],"schedule":{"segments":[{"startHour":1,"startMinute":2,"endHour":3,"endMinute":4,"days":[0,0,6,7,-1]}]}}"""
        ))!!.single()

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), schedule.timing.daysOfWeek)
        assertEquals(ScheduleTiming.ScheduleType.WEEKLY, schedule.timing.type)
    }

    @Test
    fun allSevenAndEmptyDaysAreDaily() {
        val schedules = importer().importSchedules(wrapper(
            """{"name":"Days","apps":["a"],"schedule":{"segments":[
                {"startHour":1,"startMinute":2,"endHour":3,"endMinute":4,"days":[0,1,2,3,4,5,6]},
                {"startHour":5,"startMinute":6,"endHour":7,"endMinute":8,"days":[]}
            ]}}"""
        ))!!

        assertEquals(2, schedules.size)
        schedules.forEach {
            assertEquals(ScheduleTiming.ScheduleType.DAILY, it.timing.type)
            assertTrue(it.timing.daysOfWeek.isEmpty())
        }
    }

    @Test
    fun namesMultipleValidSegments() {
        val schedules = importer().importSchedules(wrapper(
            """{"name":"Focus","websites":["example.com"],"schedule":{"segments":[
                {"startHour":1,"startMinute":0,"endHour":2,"endMinute":0,"days":[0]},
                {"startHour":3,"startMinute":0,"endHour":4,"endMinute":0,"days":[1]},
                {"startHour":5,"startMinute":0,"endHour":6,"endMinute":0,"days":[2]}
            ]}}"""
        ))!!

        assertEquals(listOf("Focus", "Focus (2)", "Focus (3)"), schedules.map { it.name })
        assertEquals(listOf("id-1", "id-2", "id-3"), schedules.map { it.id })
    }

    @Test
    fun absentScheduleCreatesManualAndBlankNameDefaultsWhenListsExist() {
        val schedules = importer().importSchedules(wrapper(
            """{"name":"  ","apps":["  com.example.app  "]}"""
        ))!!

        assertEquals(1, schedules.size)
        assertEquals("Imported blocklist", schedules.single().name)
        assertEquals(ScheduleTiming.ScheduleType.MANUAL, schedules.single().timing.type)
        assertEquals(listOf("com.example.app"), schedules.single().blockedApps)
    }

    @Test
    fun invalidSegmentsAreSkippedAndDoNotMakeManualSchedule() {
        val schedules = importer().importSchedules(wrapper(
            """{"name":"Bad","apps":["a"],"schedule":{"segments":[
                {"startHour":24,"startMinute":0,"endHour":1,"endMinute":0,"days":[0]},
                {"startHour":1.5,"startMinute":0,"endHour":1,"endMinute":0,"days":[0]}
            ]}}"""
        ))!!

        assertTrue(schedules.isEmpty())
    }

    @Test
    fun skipsAllowlistAndFiltersLists() {
        val schedules = importer().importSchedules(wrapper(
            """[
                {"name":"Allow","mode":"allowlist","apps":["danger"]},
                {"name":"  Block  ","apps":[" a ","a","", "  ", 3],"websites":[" example.com ","example.com",false]}
            ]"""
        ))!!

        assertEquals(1, schedules.size)
        assertEquals("Block", schedules.single().name)
        assertEquals(listOf("a"), schedules.single().blockedApps)
        assertEquals(listOf("example.com"), schedules.single().blockedWebsites)
    }

    @Test
    fun frictionCountUsesPositiveIntegerAndDefaultsOtherwise() {
        val schedules = importer().importSchedules(wrapper(
            """[
                {"name":"valid","apps":["a"],"overrideDifficulty":{"count":7}},
                {"name":"zero","apps":["a"],"overrideDifficulty":{"count":0}},
                {"name":"fraction","apps":["a"],"overrideDifficulty":{"count":7.5}},
                {"name":"text","apps":["a"],"overrideDifficulty":{"count":"9"}}
            ]"""
        ))!!

        assertEquals(listOf(7, 15, 15, 15), schedules.map { it.frictionWordCount })
    }

    @Test
    fun unsupportedVersionAndMalformedRecognisedPayloadFail() {
        try {
            importer().importSchedules(wrapper("""{"name":"Future","apps":["a"]}""", version = "3"))
            throw AssertionError("future version was accepted")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        try {
            importer().importSchedules(wrapper("""{"name":"Invalid","apps":["a"]}""", version = "\"two\""))
            throw AssertionError("non-numeric version was accepted")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        try {
            importer().importSchedules("""{"format":"redd-block-rules","blocklists":["not an object"]}""")
            throw AssertionError("malformed blocklist was accepted")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun nonTauriPayloadReturnsNull() {
        assertNull(importer().importSchedules("[]"))
        assertNull(importer().importSchedules("""{"blocklists":[]}"""))
    }

    private fun importer(): TauriBackupImporter {
        var nextId = 1
        return TauriBackupImporter { "id-${nextId++}" }
    }

    private fun wrapper(blocklists: String, version: String? = "2"): String {
        val versionField = version?.let { "\"formatVersion\":$it," } ?: ""
        val array = blocklists.trim().let { if (it.startsWith("[")) it else "[$it]" }
        return "{\"format\":\"redd-block-rules\",$versionField\"blocklists\":$array}"
    }
}
