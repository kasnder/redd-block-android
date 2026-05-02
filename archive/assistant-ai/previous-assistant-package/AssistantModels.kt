package net.kollnig.reddblockandroid.assistant

import androidx.compose.runtime.Immutable
import net.kollnig.reddblockandroid.data.MotionCondition
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.data.WifiCondition
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

@Immutable
data class ScheduleTimingDraft(
    val type: ScheduleTiming.ScheduleType,
    val timeHour: Int? = null,
    val timeMinute: Int? = null,
    val endTimeHour: Int? = null,
    val endTimeMinute: Int? = null,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val motionCondition: MotionCondition? = null,
    val wifiCondition: WifiCondition? = null
)

@Immutable
data class ScheduleProposal(
    val name: String,
    val blockedApps: List<String>,
    val blockedWebsites: List<String>,
    val timing: ScheduleTimingDraft,
    val frictionWordCount: Int,
    val autoReenableMinutes: Int,
    val rationale: String,
    val experimentDays: Int?
)

@Immutable
data class ScheduleAmendmentProposal(
    val scheduleId: String,
    val originalName: String,
    val updatedSchedule: Schedule,
    val rationale: String
)

@Immutable
data class AssistantMessage(
    val role: Role,
    val text: String,
    val proposal: ScheduleProposal? = null,
    val amendment: ScheduleAmendmentProposal? = null,
    val id: String = UUID.randomUUID().toString()
) {
    enum class Role {
        USER,
        ASSISTANT
    }
}

@Immutable
data class InstalledAppSummary(
    val packageName: String,
    val label: String
)

@Immutable
data class UsageSummary(
    val packageName: String,
    val label: String,
    val minutesUsed: Long,
    val bucket: String
)

data class AssistantContext(
    val existingSchedules: String,
    val installedApps: List<InstalledAppSummary>,
    val usageSummaries: List<UsageSummary>,
    val usageSharingEnabled: Boolean,
    val motionSharingEnabled: Boolean,
    val motionStateJson: JSONObject?,
    val wifiSharingEnabled: Boolean,
    val wifiJson: JSONObject?,
    val goals: String
)

sealed class AssistantResult {
    data class Message(val text: String) : AssistantResult()
    data class Proposal(val text: String, val proposal: ScheduleProposal) : AssistantResult()
    data class Amendment(val text: String, val amendment: ScheduleAmendmentProposal) : AssistantResult()
}
