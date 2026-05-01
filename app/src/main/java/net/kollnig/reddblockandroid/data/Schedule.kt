package net.kollnig.reddblockandroid.data

import java.time.DayOfWeek
import java.time.LocalTime

data class Schedule(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val timing: ScheduleTiming,
    val blockedApps: List<String> = emptyList(),
    val blockedWebsites: List<String> = emptyList(),
    val frictionWordCount: Int = 15,
    val autoReenableMinutes: Int = 1440, // default 24 hours; 0 = stays disabled
    val disabledUntil: Long? = null
)

data class ScheduleTiming(
    val type: ScheduleType,
    val timeHour: Int? = null,
    val timeMinute: Int? = null,
    val endTimeHour: Int? = null,
    val endTimeMinute: Int? = null,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val isRecurring: Boolean = true,
    val motionCondition: MotionCondition? = null,
    val wifiCondition: WifiCondition? = null
) {
    enum class ScheduleType {
        DAILY,
        WEEKLY,
        MANUAL
    }

    val time: LocalTime?
        get() = if (timeHour != null && timeMinute != null) {
            LocalTime.of(timeHour, timeMinute)
        } else null

    val endTime: LocalTime?
        get() = if (endTimeHour != null && endTimeMinute != null) {
            LocalTime.of(endTimeHour, endTimeMinute)
        } else null
}

data class MotionCondition(
    val activity: Activity
) {
    enum class Activity(val assistantName: String, val label: String) {
        STILL("still", "Still"),
        WALKING("walking", "Walking"),
        RUNNING("running", "Running"),
        ON_FOOT("on_foot", "On foot"),
        ON_BICYCLE("on_bicycle", "Cycling"),
        IN_VEHICLE("in_vehicle", "In vehicle");

        companion object {
            fun fromAssistantName(value: String): Activity? {
                return entries.firstOrNull { it.assistantName == value }
            }
        }
    }
}

data class WifiCondition(
    val label: String,
    val ssid: String
)
