package net.kollnig.reddblockandroid.assistant

import net.kollnig.reddblockandroid.data.MotionCondition
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantImportParserTest {
    private val installedApps = listOf(
        InstalledAppSummary("com.example.social", "Example Social"),
        InstalledAppSummary("com.example.video", "Example Video")
    )

    @Test
    fun extractsReddBlockJsonFence() {
        val result = AssistantImportParser.extractJson(
            """
            Here is the plan.
            ```redd-block-json
            {"version":1,"actions":[]}
            ```
            """.trimIndent()
        )

        assertEquals("""{"version":1,"actions":[]}""", result.getOrThrow())
    }

    @Test
    fun extractsJsonFence() {
        val result = AssistantImportParser.extractJson(
            """
            Advice first.
            ```json
            {"version":1,"actions":[]}
            ```
            """.trimIndent()
        )

        assertEquals("""{"version":1,"actions":[]}""", result.getOrThrow())
    }

    @Test
    fun acceptsRawJson() {
        val result = AssistantImportParser.extractJson("""{"version":1,"actions":[]}""")

        assertEquals("""{"version":1,"actions":[]}""", result.getOrThrow())
    }

    @Test
    fun rejectsTextWithoutJson() {
        val result = AssistantImportParser.extractJson("No machine-readable block here.")

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsMalformedJson() {
        val result = AssistantImportParser.parseActions(
            replyText = "```redd-block-json\n{\"version\":1,\n```",
            installedApps = installedApps,
            existingSchedules = { emptyList() },
            scheduleById = { null }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun parsesValidProposalAction() {
        val actions = AssistantImportParser.parseActions(
            replyText = fenced(validProposalAction()),
            installedApps = installedApps,
            existingSchedules = { emptyList() },
            scheduleById = { null }
        ).getOrThrow()

        assertEquals(1, actions.size)
        val action = actions.single() as ImportedAssistantAction.Proposal
        assertEquals("Evening social", action.proposal.name)
        assertEquals(listOf("com.example.social"), action.proposal.blockedApps)
    }

    @Test
    fun parsesProposalWithMotionAndWifiConditions() {
        val actions = AssistantImportParser.parseActions(
            replyText = fenced(
                validProposalAction(
                    motionCondition = """{"activity":"IN_VEHICLE"}""",
                    wifiCondition = """{"label":"Work","ssid":"OfficeNet"}"""
                )
            ),
            installedApps = installedApps,
            existingSchedules = { emptyList() },
            scheduleById = { null }
        ).getOrThrow()

        val action = actions.single() as ImportedAssistantAction.Proposal
        assertEquals(MotionCondition.Activity.IN_VEHICLE, action.proposal.timing.motionCondition?.activity)
        assertEquals("Work", action.proposal.timing.wifiCondition?.label)
        assertEquals("OfficeNet", action.proposal.timing.wifiCondition?.ssid)
    }

    @Test
    fun rejectsUnknownPackageName() {
        val result = AssistantImportParser.parseActions(
            replyText = fenced(validProposalAction(packageName = "com.unknown.app")),
            installedApps = installedApps,
            existingSchedules = { emptyList() },
            scheduleById = { null }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsMalformedDomain() {
        val result = AssistantImportParser.parseActions(
            replyText = fenced(validProposalAction(blockedWebsites = """"not a domain"""")),
            installedApps = installedApps,
            existingSchedules = { emptyList() },
            scheduleById = { null }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsInvalidTiming() {
        val result = AssistantImportParser.parseActions(
            replyText = fenced(validProposalAction(timeHour = 27)),
            installedApps = installedApps,
            existingSchedules = { emptyList() },
            scheduleById = { null }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsUnsupportedReenableDuration() {
        val result = AssistantImportParser.parseActions(
            replyText = fenced(validProposalAction(autoReenableMinutes = 17)),
            installedApps = installedApps,
            existingSchedules = { emptyList() },
            scheduleById = { null }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsManualScheduleWithContextConditions() {
        val result = AssistantImportParser.parseActions(
            replyText = fenced(
                validProposalAction(
                    scheduleType = "MANUAL",
                    timeHourLiteral = "null",
                    timeMinuteLiteral = "null",
                    endTimeHourLiteral = "null",
                    endTimeMinuteLiteral = "null",
                    motionCondition = """{"activity":"WALKING"}"""
                )
            ),
            installedApps = installedApps,
            existingSchedules = { emptyList() },
            scheduleById = { null }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun promptOptionsKeepUsageStatsOptInByDefault() {
        val options = PromptOptions()

        assertTrue(options.includeTopUsedApps)
        assertTrue(!options.includeUsageStats)
    }

    @Test
    fun parsesValidAmendmentAction() {
        val original = Schedule(
            id = "schedule-1",
            name = "Old social",
            isEnabled = true,
            timing = ScheduleTiming(
                type = ScheduleTiming.ScheduleType.DAILY,
                timeHour = 20,
                timeMinute = 0,
                endTimeHour = 22,
                endTimeMinute = 0
            ),
            blockedApps = listOf("com.example.social"),
            blockedWebsites = emptyList(),
            frictionWordCount = 10,
            autoReenableMinutes = 10
        )

        val actions = AssistantImportParser.parseActions(
            replyText = fenced(validAmendmentAction()),
            installedApps = installedApps,
            existingSchedules = { listOf(original) },
            scheduleById = { id -> if (id == original.id) original else null }
        ).getOrThrow()

        val action = actions.single() as ImportedAssistantAction.Amendment
        assertEquals("Old social", action.amendment.originalName)
        assertEquals("Stronger evening social", action.amendment.updatedSchedule.name)
    }

    @Test
    fun rejectsDuplicateNewSchedule() {
        val existing = Schedule(
            id = "existing",
            name = "Existing",
            isEnabled = true,
            timing = ScheduleTiming(
                type = ScheduleTiming.ScheduleType.DAILY,
                timeHour = 21,
                timeMinute = 0,
                endTimeHour = 23,
                endTimeMinute = 0
            ),
            blockedApps = listOf("com.example.social"),
            blockedWebsites = listOf("reddit.com"),
            frictionWordCount = 15,
            autoReenableMinutes = 10
        )

        val result = AssistantImportParser.parseActions(
            replyText = fenced(validProposalAction()),
            installedApps = installedApps,
            existingSchedules = { listOf(existing) },
            scheduleById = { null }
        )

        assertTrue(result.isFailure)
    }

    private fun fenced(json: String): String {
        return """
            This seems proportionate.
            ```redd-block-json
            $json
            ```
        """.trimIndent()
    }

    private fun validProposalAction(
        packageName: String = "com.example.social",
        blockedWebsites: String = """"reddit.com"""",
        timeHour: Int = 21,
        autoReenableMinutes: Int = 10,
        scheduleType: String = "DAILY",
        timeHourLiteral: String = "$timeHour",
        timeMinuteLiteral: String = "0",
        endTimeHourLiteral: String = "23",
        endTimeMinuteLiteral: String = "0",
        motionCondition: String = "null",
        wifiCondition: String = "null"
    ): String {
        return """
            {
              "version": 1,
              "actions": [
                {
                  "type": "propose_schedule",
                  "arguments": {
                    "name": "Evening social",
                    "blockedApps": ["$packageName"],
                    "blockedWebsites": [$blockedWebsites],
                    "timing": {
                      "type": "$scheduleType",
                      "timeHour": $timeHourLiteral,
                      "timeMinute": $timeMinuteLiteral,
                      "endTimeHour": $endTimeHourLiteral,
                      "endTimeMinute": $endTimeMinuteLiteral,
                      "daysOfWeek": [],
                      "motionCondition": $motionCondition,
                      "wifiCondition": $wifiCondition
                    },
                    "frictionWordCount": 15,
                    "autoReenableMinutes": $autoReenableMinutes,
                    "rationale": "Evening scrolling is cue-driven.",
                    "experimentDays": 7
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun validAmendmentAction(): String {
        return """
            {
              "version": 1,
              "actions": [
                {
                  "type": "propose_schedule_amendment",
                  "arguments": {
                    "scheduleId": "schedule-1",
                    "name": "Stronger evening social",
                    "blockedApps": ["com.example.social", "com.example.video"],
                    "blockedWebsites": ["reddit.com"],
                    "timing": {
                      "type": "DAILY",
                      "timeHour": 20,
                      "timeMinute": 30,
                      "endTimeHour": 23,
                      "endTimeMinute": 0,
                      "daysOfWeek": [],
                      "motionCondition": null,
                      "wifiCondition": null
                    },
                    "frictionWordCount": 20,
                    "autoReenableMinutes": 10,
                    "rationale": "The existing schedule needs a slightly wider evening window."
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
