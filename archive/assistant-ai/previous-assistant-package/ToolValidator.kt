package net.kollnig.reddblockandroid.assistant

import net.kollnig.reddblockandroid.data.MotionCondition
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.data.WifiCondition
import net.kollnig.reddblockandroid.schedule.Schedules
import org.json.JSONObject
import java.time.DayOfWeek

class ToolValidator(
    installedApps: List<InstalledAppSummary>
) {
    private val installedPackages = installedApps.map { it.packageName }.toSet()

    fun parseAndValidate(arguments: String): Result<ScheduleProposal> = runCatching {
        val json = JSONObject(arguments)
        val proposal = parseProposal(json)
        validate(proposal)
        proposal
    }

    fun parseAndValidateAmendment(arguments: String): Result<ScheduleAmendmentProposal> = runCatching {
        val json = JSONObject(arguments)
        val scheduleId = json.getString("scheduleId")
        val original = Schedules.get(scheduleId)
            ?: error("The schedule to amend no longer exists.")
        val timing = parseTiming(json.getJSONObject("timing"))
        val updated = original.copy(
            name = json.getString("name").trim(),
            isEnabled = original.isEnabled,
            timing = timing.toScheduleTiming(),
            blockedApps = json.getJSONArray("blockedApps").toStringList().distinct(),
            blockedWebsites = json.getJSONArray("blockedWebsites").toStringList().map(::normalizeDomain).distinct(),
            frictionWordCount = json.getInt("frictionWordCount"),
            autoReenableMinutes = json.getInt("autoReenableMinutes"),
            disabledUntil = original.disabledUntil
        )
        validateUpdatedSchedule(updated)
        require(updated != original) { "Amendment does not change the selected schedule." }
        ScheduleAmendmentProposal(
            scheduleId = scheduleId,
            originalName = original.name,
            updatedSchedule = updated,
            rationale = json.getString("rationale").trim()
        )
    }

    private fun parseProposal(json: JSONObject): ScheduleProposal {
        val timing = parseTiming(json.getJSONObject("timing"))
        return ScheduleProposal(
            name = json.getString("name").trim(),
            blockedApps = json.getJSONArray("blockedApps").toStringList().distinct(),
            blockedWebsites = json.getJSONArray("blockedWebsites").toStringList().map(::normalizeDomain).distinct(),
            timing = timing,
            frictionWordCount = json.getInt("frictionWordCount"),
            autoReenableMinutes = json.getInt("autoReenableMinutes"),
            rationale = json.getString("rationale").trim(),
            experimentDays = json.optNullableInt("experimentDays")
        )
    }

    private fun validate(proposal: ScheduleProposal) {
        validateScheduleParts(
            name = proposal.name,
            blockedApps = proposal.blockedApps,
            blockedWebsites = proposal.blockedWebsites,
            timing = proposal.timing,
            frictionWordCount = proposal.frictionWordCount,
            autoReenableMinutes = proposal.autoReenableMinutes
        )
        require(!duplicatesExistingSchedule(proposal)) {
            "Proposal duplicates an existing schedule."
        }
    }

    private fun validateUpdatedSchedule(schedule: Schedule) {
        validateScheduleParts(
            name = schedule.name,
            blockedApps = schedule.blockedApps,
            blockedWebsites = schedule.blockedWebsites,
            timing = schedule.timing.toDraft(),
            frictionWordCount = schedule.frictionWordCount,
            autoReenableMinutes = schedule.autoReenableMinutes
        )
    }

    private fun validateScheduleParts(
        name: String,
        blockedApps: List<String>,
        blockedWebsites: List<String>,
        timing: ScheduleTimingDraft,
        frictionWordCount: Int,
        autoReenableMinutes: Int
    ) {
        require(name.isNotBlank()) { "Schedule name is required." }
        require(blockedApps.isNotEmpty() || blockedWebsites.isNotEmpty()) {
            "Proposal must block at least one app or website."
        }
        require(blockedApps.all { installedPackages.contains(it) }) {
            "Proposal contains apps that are not installed."
        }
        require(blockedWebsites.all { DOMAIN_PATTERN.matches(it) }) {
            "Proposal contains malformed domains."
        }
        require(frictionWordCount in 1..50) {
            "Friction word count is outside the allowed range."
        }
        require(autoReenableMinutes in ALLOWED_REENABLE_MINUTES) {
            "Temporary unlock duration is not supported."
        }
        validateTiming(timing)
    }

    private fun validateTiming(timing: ScheduleTimingDraft) {
        if (timing.type == ScheduleTiming.ScheduleType.MANUAL) {
            require(timing.motionCondition == null && timing.wifiCondition == null) {
                "Manual schedules cannot use motion or Wi-Fi conditions."
            }
            return
        }
        require(timing.timeHour in 0..23 && timing.timeMinute in 0..59) {
            "Start time is invalid."
        }
        require(timing.endTimeHour in 0..23 && timing.endTimeMinute in 0..59) {
            "End time is invalid."
        }
        if (timing.type == ScheduleTiming.ScheduleType.WEEKLY) {
            require(timing.daysOfWeek.isNotEmpty()) { "Weekly schedules need at least one day." }
        }
        timing.wifiCondition?.let { condition ->
            require(condition.label.isNotBlank()) { "Wi-Fi label is required." }
            require(condition.ssid.isNotBlank()) { "Wi-Fi SSID is required." }
        }
    }

    private fun duplicatesExistingSchedule(proposal: ScheduleProposal): Boolean {
        return Schedules.getAll().any { schedule ->
            schedule.blockedApps.toSet() == proposal.blockedApps.toSet() &&
                    schedule.blockedWebsites.toSet() == proposal.blockedWebsites.toSet() &&
                    schedule.timing.type == proposal.timing.type &&
                    schedule.timing.timeHour == proposal.timing.timeHour &&
                    schedule.timing.timeMinute == proposal.timing.timeMinute &&
                    schedule.timing.endTimeHour == proposal.timing.endTimeHour &&
                    schedule.timing.endTimeMinute == proposal.timing.endTimeMinute &&
                    schedule.timing.daysOfWeek == proposal.timing.daysOfWeek &&
                    schedule.timing.motionCondition == proposal.timing.motionCondition &&
                    schedule.timing.wifiCondition == proposal.timing.wifiCondition
        }
    }

    private fun parseMotionCondition(json: JSONObject?): MotionCondition? {
        if (json == null || json.isNull("activity")) return null
        return MotionCondition(
            activity = MotionCondition.Activity.valueOf(json.getString("activity"))
        )
    }

    private fun parseWifiCondition(json: JSONObject?): WifiCondition? {
        if (json == null || json.isNull("ssid")) return null
        return WifiCondition(
            label = json.getString("label").trim(),
            ssid = json.getString("ssid").trim()
        )
    }

    private fun parseTiming(timingJson: JSONObject): ScheduleTimingDraft {
        val type = ScheduleTiming.ScheduleType.valueOf(timingJson.getString("type"))
        val days = timingJson.getJSONArray("daysOfWeek").let { arr ->
            (0 until arr.length()).map { DayOfWeek.valueOf(arr.getString(it)) }.toSet()
        }
        return ScheduleTimingDraft(
            type = type,
            timeHour = timingJson.optNullableInt("timeHour"),
            timeMinute = timingJson.optNullableInt("timeMinute"),
            endTimeHour = timingJson.optNullableInt("endTimeHour"),
            endTimeMinute = timingJson.optNullableInt("endTimeMinute"),
            daysOfWeek = days,
            motionCondition = parseMotionCondition(timingJson.optJSONObject("motionCondition")),
            wifiCondition = parseWifiCondition(timingJson.optJSONObject("wifiCondition"))
        )
    }

    private fun ScheduleTimingDraft.toScheduleTiming(): ScheduleTiming {
        return ScheduleTiming(
            type = type,
            timeHour = timeHour,
            timeMinute = timeMinute,
            endTimeHour = endTimeHour,
            endTimeMinute = endTimeMinute,
            daysOfWeek = daysOfWeek,
            motionCondition = motionCondition,
            wifiCondition = wifiCondition
        )
    }

    private fun ScheduleTiming.toDraft(): ScheduleTimingDraft {
        return ScheduleTimingDraft(
            type = type,
            timeHour = timeHour,
            timeMinute = timeMinute,
            endTimeHour = endTimeHour,
            endTimeMinute = endTimeMinute,
            daysOfWeek = daysOfWeek,
            motionCondition = motionCondition,
            wifiCondition = wifiCondition
        )
    }

    private fun normalizeDomain(input: String): String {
        return input.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
    }

    private fun org.json.JSONArray.toStringList(): List<String> {
        return (0 until length()).map { getString(it).trim() }.filter { it.isNotBlank() }
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (!has(name) || isNull(name)) null else getInt(name)
    }

    companion object {
        private val DOMAIN_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$")
        private val ALLOWED_REENABLE_MINUTES = setOf(0, 5, 10, 15, 30, 60, 120, 240, 480, 1440)
    }
}
