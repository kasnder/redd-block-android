package net.kollnig.reddblockandroid.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.util.NotificationHelper
import net.kollnig.reddblockandroid.util.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

/**
 * Single, simple schedule system. Supports MULTIPLE active schedules simultaneously.
 * Simplified from Reef: no usage-time limits, apps/websites are simply blocked or not.
 */
object Schedules {
    private const val TAG = "Schedules"
    private const val SCHEDULES_KEY = "routines" // keep legacy key for data compat
    private const val ACTIVE_SESSIONS_KEY = "active_routine_sessions" // keep legacy key

    const val ACTION_CHANGED = "net.kollnig.reddblockandroid.SCHEDULE_CHANGED"

    data class ActiveSession(
        val scheduleId: String,
        val startTime: Long,
        val blockedApps: Set<String>,
        val blockedWebsites: Set<String>
    )

    fun getAll(): List<Schedule> {
        val json = prefs.getString(SCHEDULES_KEY, "[]") ?: "[]"
        return try {
            JSONArray(json).let { arr ->
                (0 until arr.length()).mapNotNull { parseSchedule(arr.getJSONObject(it)) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun exportSchedules(): String {
        val schedules = getAll()
        val json = JSONArray().apply {
            schedules.forEach { put(scheduleToJson(it)) }
        }
        return json.toString(2)
    }

    fun importSchedules(jsonString: String, context: Context): Int {
        return try {
            val arr = JSONArray(jsonString)
            var count = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val schedule = parseSchedule(obj)
                if (schedule != null) {
                    val newSchedule = schedule.copy(id = UUID.randomUUID().toString())
                    save(newSchedule, context)
                    count++
                    
                    if (newSchedule.isEnabled) {
                        when (newSchedule.timing.type) {
                            ScheduleTiming.ScheduleType.MANUAL -> {
                                startSession(context, newSchedule)
                            }
                            ScheduleTiming.ScheduleType.DAILY,
                            ScheduleTiming.ScheduleType.WEEKLY -> {
                                if (ScheduleManager.isScheduleActiveNow(newSchedule)) {
                                    startSession(context, newSchedule)
                                }
                                ScheduleManager.scheduleTimedSchedule(context, newSchedule)
                            }
                        }
                    }
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import schedules", e)
            -1
        }
    }

    fun get(id: String): Schedule? = getAll().find { it.id == id }

    fun save(schedule: Schedule, context: Context) {
        val schedules = getAll().toMutableList()
        val index = schedules.indexOfFirst { it.id == schedule.id }

        if (index >= 0) schedules[index] = schedule
        else schedules.add(schedule)

        saveAll(schedules)

        // If this schedule has an active session, update its blocked lists
        val sessions = getActiveSessions().toMutableList()
        val sessionIndex = sessions.indexOfFirst { it.scheduleId == schedule.id }
        if (sessionIndex >= 0) {
            sessions[sessionIndex] = sessions[sessionIndex].copy(
                blockedApps = schedule.blockedApps.toSet(),
                blockedWebsites = schedule.blockedWebsites.toSet()
            )
            saveActiveSessions(sessions)
            broadcast(context)
        }
    }

    fun delete(id: String, context: Context) {
        stopSession(context, id)
        val schedules = getAll().filterNot { it.id == id }
        saveAll(schedules)
    }

    fun toggle(id: String, context: Context) {
        Log.d(TAG, "Toggle called for schedule ID: $id")

        val schedule = get(id) ?: run {
            Log.e(TAG, "Schedule not found: $id")
            return
        }

        val hasActiveSession = getActiveSessions().any { it.scheduleId == id }

        if (schedule.isEnabled && hasActiveSession) {
            // Turning OFF
            val updated = if (schedule.autoReenableMinutes > 0) {
                val disabledUntil = System.currentTimeMillis() + schedule.autoReenableMinutes * 60_000L
                schedule.copy(isEnabled = false, disabledUntil = disabledUntil)
            } else {
                schedule.copy(isEnabled = false, disabledUntil = null)
            }

            val schedules = getAll().toMutableList()
            val index = schedules.indexOfFirst { it.id == id }
            if (index >= 0) schedules[index] = updated
            saveAll(schedules)

            stopSession(context, id)
            if (updated.timing.type != ScheduleTiming.ScheduleType.MANUAL) {
                ScheduleManager.cancelSchedule(context, id)
            }

            // Schedule auto-re-enable if configured
            if (schedule.autoReenableMinutes > 0) {
                ScheduleManager.scheduleReEnable(context, id, schedule.autoReenableMinutes * 60_000L)
            }
        } else if (!schedule.isEnabled) {
            // Turning ON (re-enabling)
            val updated = schedule.copy(isEnabled = true, disabledUntil = null)

            val schedules = getAll().toMutableList()
            val index = schedules.indexOfFirst { it.id == id }
            if (index >= 0) schedules[index] = updated
            saveAll(schedules)

            when (updated.timing.type) {
                ScheduleTiming.ScheduleType.MANUAL -> {
                    startSession(context, updated)
                }
                ScheduleTiming.ScheduleType.DAILY,
                ScheduleTiming.ScheduleType.WEEKLY -> {
                    if (ScheduleManager.isScheduleActiveNow(updated)) {
                        startSession(context, updated)
                    }
                    ScheduleManager.scheduleTimedSchedule(context, updated)
                }
            }
        } else {
            // Enabled but no active session — toggle to activate (manual) or just enable scheduling
            val updated = schedule.copy(isEnabled = true)

            val schedules = getAll().toMutableList()
            val index = schedules.indexOfFirst { it.id == id }
            if (index >= 0) schedules[index] = updated
            saveAll(schedules)

            when (updated.timing.type) {
                ScheduleTiming.ScheduleType.MANUAL -> {
                    startSession(context, updated)
                }
                ScheduleTiming.ScheduleType.DAILY,
                ScheduleTiming.ScheduleType.WEEKLY -> {
                    if (ScheduleManager.isScheduleActiveNow(updated)) {
                        startSession(context, updated)
                    }
                    ScheduleManager.scheduleTimedSchedule(context, updated)
                }
            }
        }
    }

    fun reEnableSchedule(context: Context, scheduleId: String) {
        Log.d(TAG, "Auto-re-enabling schedule: $scheduleId")
        val schedule = get(scheduleId) ?: return
        if (schedule.isEnabled) return // already enabled

        val updated = schedule.copy(isEnabled = true, disabledUntil = null)
        val schedules = getAll().toMutableList()
        val index = schedules.indexOfFirst { it.id == scheduleId }
        if (index >= 0) schedules[index] = updated
        saveAll(schedules)

        when (updated.timing.type) {
            ScheduleTiming.ScheduleType.MANUAL -> {
                startSession(context, updated)
            }
            ScheduleTiming.ScheduleType.DAILY,
            ScheduleTiming.ScheduleType.WEEKLY -> {
                if (ScheduleManager.isScheduleActiveNow(updated)) {
                    startSession(context, updated)
                }
                ScheduleManager.scheduleTimedSchedule(context, updated)
            }
        }
    }

    private fun saveAll(schedules: List<Schedule>) {
        val json = JSONArray().apply {
            schedules.forEach { put(scheduleToJson(it)) }
        }
        prefs.edit { putString(SCHEDULES_KEY, json.toString()) }
    }

    fun startSession(context: Context, schedule: Schedule) {
        Log.d(TAG, "Starting session for: ${schedule.name}")

        val sessions = getActiveSessions().toMutableList()
        sessions.removeAll { it.scheduleId == schedule.id }

        val newSession = ActiveSession(
            scheduleId = schedule.id,
            startTime = System.currentTimeMillis(),
            blockedApps = schedule.blockedApps.toSet(),
            blockedWebsites = schedule.blockedWebsites.toSet()
        )
        sessions.add(newSession)
        saveActiveSessions(sessions)

        Log.d(TAG, "Started session for ${schedule.name} with ${schedule.blockedApps.size} blocked apps and ${schedule.blockedWebsites.size} blocked websites")

        broadcast(context)
        NotificationHelper.showScheduleActivatedNotification(context, schedule)
    }

    fun stopSession(context: Context, scheduleId: String) {
        Log.d(TAG, "Stopping session for schedule: $scheduleId")

        val schedule = get(scheduleId)
        val sessions = getActiveSessions().toMutableList()
        val removed = sessions.removeAll { it.scheduleId == scheduleId }

        if (removed) {
            saveActiveSessions(sessions)
            broadcast(context)
            schedule?.let { NotificationHelper.showScheduleDeactivatedNotification(context, it) }
        }
    }

    fun getActiveSessions(): List<ActiveSession> {
        val json = prefs.getString(ACTIVE_SESSIONS_KEY, "[]") ?: "[]"
        return try {
            JSONArray(json).let { arr ->
                (0 until arr.length()).mapNotNull {
                    try {
                        val obj = arr.getJSONObject(it)

                        val blockedApps = mutableSetOf<String>()
                        obj.optJSONArray("blockedApps")?.let { appsArr ->
                            for (i in 0 until appsArr.length()) {
                                blockedApps.add(appsArr.getString(i))
                            }
                        }

                        val blockedWebsites = mutableSetOf<String>()
                        obj.optJSONArray("blockedWebsites")?.let { sitesArr ->
                            for (i in 0 until sitesArr.length()) {
                                blockedWebsites.add(sitesArr.getString(i))
                            }
                        }

                        ActiveSession(
                            scheduleId = obj.optString("scheduleId", obj.optString("routineId", "")),
                            startTime = obj.getLong("startTime"),
                            blockedApps = blockedApps,
                            blockedWebsites = blockedWebsites
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveActiveSessions(sessions: List<ActiveSession>) {
        val json = JSONArray().apply {
            sessions.forEach { session ->
                put(JSONObject().apply {
                    put("scheduleId", session.scheduleId)
                    put("routineId", session.scheduleId) // back-compat
                    put("startTime", session.startTime)
                    put("blockedApps", JSONArray(session.blockedApps.toList()))
                    put("blockedWebsites", JSONArray(session.blockedWebsites.toList()))
                })
            }
        }
        prefs.edit { putString(ACTIVE_SESSIONS_KEY, json.toString()) }
    }

    /**
     * Check if an app is blocked by ANY active schedule.
     */
    fun isAppBlocked(packageName: String): Boolean {
        val sessions = getActiveSessions()
        if (sessions.isEmpty()) return false

        return sessions.any { session ->
            val schedule = get(session.scheduleId) ?: return@any false
            val maxDuration = ScheduleManager.getMaxScheduleDuration(schedule.timing)
            if (System.currentTimeMillis() - session.startTime > maxDuration) return@any false
            session.blockedApps.contains(packageName)
        }
    }

    /**
     * Check if a website domain is blocked by ANY active schedule.
     */
    fun isWebsiteBlocked(domain: String): Boolean {
        val sessions = getActiveSessions()
        if (sessions.isEmpty()) return false

        return sessions.any { session ->
            val schedule = get(session.scheduleId) ?: return@any false
            val maxDuration = ScheduleManager.getMaxScheduleDuration(schedule.timing)
            if (System.currentTimeMillis() - session.startTime > maxDuration) return@any false
            session.blockedWebsites.any { blocked ->
                domain == blocked || domain.endsWith(".$blocked")
            }
        }
    }

    /**
     * Check if any schedule is currently active (has an active session).
     */
    fun isAnyScheduleActive(): Boolean {
        return getActiveSessions().isNotEmpty()
    }

    /**
     * Check if a specific schedule is currently active.
     */
    fun isScheduleActive(scheduleId: String): Boolean {
        return getActiveSessions().any { it.scheduleId == scheduleId }
    }

    private fun broadcast(context: Context) {
        context.sendBroadcast(Intent(ACTION_CHANGED).apply {
            setPackage(context.packageName)
        })
    }

    fun createDefaults(): List<Schedule> = listOf(
        Schedule(
            id = UUID.randomUUID().toString(),
            name = "Weekend Digital Detox",
            isEnabled = false,
            timing = ScheduleTiming(
                type = ScheduleTiming.ScheduleType.WEEKLY,
                timeHour = 9,
                timeMinute = 0,
                endTimeHour = 18,
                endTimeMinute = 0,
                daysOfWeek = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            )
        ),
        Schedule(
            id = UUID.randomUUID().toString(),
            name = "Workday Focus",
            isEnabled = false,
            timing = ScheduleTiming(
                type = ScheduleTiming.ScheduleType.WEEKLY,
                timeHour = 9,
                timeMinute = 0,
                endTimeHour = 17,
                endTimeMinute = 0,
                daysOfWeek = setOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                )
            )
        )
    )

    private fun parseSchedule(json: JSONObject): Schedule? = try {
        val timingJson = json.getJSONObject("schedule")
        val timing = ScheduleTiming(
            type = ScheduleTiming.ScheduleType.valueOf(timingJson.getString("type")),
            timeHour = timingJson.optInt("timeHour").takeIf { timingJson.has("timeHour") },
            timeMinute = timingJson.optInt("timeMinute").takeIf { timingJson.has("timeMinute") },
            endTimeHour = timingJson.optInt("endTimeHour").takeIf { timingJson.has("endTimeHour") },
            endTimeMinute = timingJson.optInt("endTimeMinute").takeIf { timingJson.has("endTimeMinute") },
            daysOfWeek = timingJson.optJSONArray("daysOfWeek")?.let { arr ->
                (0 until arr.length()).mapNotNull {
                    try { DayOfWeek.valueOf(arr.getString(it)) } catch (_: Exception) { null }
                }.toSet()
            } ?: emptySet(),
            isRecurring = timingJson.optBoolean("isRecurring", true)
        )

        val blockedApps = json.optJSONArray("blockedApps")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        val blockedWebsites = json.optJSONArray("blockedWebsites")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        Schedule(
            id = json.getString("id"),
            name = json.getString("name"),
            isEnabled = json.getBoolean("isEnabled"),
            timing = timing,
            blockedApps = blockedApps,
            blockedWebsites = blockedWebsites,
            frictionWordCount = json.optInt("frictionWordCount", 15),
            autoReenableMinutes = json.optInt("autoReenableMinutes", 1440),
            disabledUntil = if (json.has("disabledUntil")) json.optLong("disabledUntil") else null
        )
    } catch (_: Exception) {
        null
    }

    private fun scheduleToJson(schedule: Schedule) = JSONObject().apply {
        put("id", schedule.id)
        put("name", schedule.name)
        put("isEnabled", schedule.isEnabled)

        put("schedule", JSONObject().apply {
            val s = schedule.timing
            put("type", s.type.name)
            s.timeHour?.let { put("timeHour", it) }
            s.timeMinute?.let { put("timeMinute", it) }
            s.endTimeHour?.let { put("endTimeHour", it) }
            s.endTimeMinute?.let { put("endTimeMinute", it) }
            put("daysOfWeek", JSONArray().apply {
                s.daysOfWeek.forEach { put(it.name) }
            })
            put("isRecurring", s.isRecurring)
        })

        put("blockedApps", JSONArray(schedule.blockedApps))
        put("blockedWebsites", JSONArray(schedule.blockedWebsites))
        put("frictionWordCount", schedule.frictionWordCount)
        put("autoReenableMinutes", schedule.autoReenableMinutes)
        schedule.disabledUntil?.let { put("disabledUntil", it) }
    }
}
