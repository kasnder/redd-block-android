package net.kollnig.reddblockandroid.assistant

import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.data.MotionCondition
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

    private fun parseProposal(json: JSONObject): ScheduleProposal {
        val timingJson = json.getJSONObject("timing")
        val type = ScheduleTiming.ScheduleType.valueOf(timingJson.getString("type"))
        val days = timingJson.getJSONArray("daysOfWeek").let { arr ->
            (0 until arr.length()).map { DayOfWeek.valueOf(arr.getString(it)) }.toSet()
        }
        val timing = ScheduleTimingDraft(
            type = type,
            timeHour = timingJson.optNullableInt("timeHour"),
            timeMinute = timingJson.optNullableInt("timeMinute"),
            endTimeHour = timingJson.optNullableInt("endTimeHour"),
            endTimeMinute = timingJson.optNullableInt("endTimeMinute"),
            daysOfWeek = days,
            motionCondition = parseMotionCondition(timingJson.optJSONObject("motionCondition")),
            wifiCondition = parseWifiCondition(timingJson.optJSONObject("wifiCondition"))
        )
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
        require(proposal.name.isNotBlank()) { "Schedule name is required." }
        require(proposal.blockedApps.isNotEmpty() || proposal.blockedWebsites.isNotEmpty()) {
            "Proposal must block at least one app or website."
        }
        require(proposal.blockedApps.all { installedPackages.contains(it) }) {
            "Proposal contains apps that are not installed."
        }
        require(proposal.blockedWebsites.all { DOMAIN_PATTERN.matches(it) }) {
            "Proposal contains malformed domains."
        }
        require(proposal.frictionWordCount in 1..50) {
            "Friction word count is outside the allowed range."
        }
        require(proposal.autoReenableMinutes in ALLOWED_REENABLE_MINUTES) {
            "Temporary unlock duration is not supported."
        }
        validateTiming(proposal.timing)
        require(!duplicatesExistingSchedule(proposal)) {
            "Proposal duplicates an existing schedule."
        }
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
            ssid = json.getString("ssid").trim(),
            bssid = json.optString("bssid").takeIf { it.isNotBlank() }
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
