package net.kollnig.reddblockandroid.schedule

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import net.kollnig.reddblockandroid.assistant.ActivityRecognitionManager
import net.kollnig.reddblockandroid.assistant.WifiContextProvider
import net.kollnig.reddblockandroid.data.MotionCondition
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.data.WifiCondition
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedule utilities.
 *
 * Blocking for DAILY/WEEKLY schedules is now evaluated in real-time by
 * [Schedules.isScheduleActive] / [isScheduleActiveNow], so there is no
 * need for WorkManager activation/deactivation workers or a periodic watcher.
 *
 * The only WorkManager usage that remains is [scheduleReEnable], which
 * re-enables a schedule after the user temporarily disables it.
 */
object ScheduleManager {
    private const val TAG = "ScheduleManager"

    fun cancelSchedule(context: Context, scheduleId: String) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(getReEnableWorkName(scheduleId))
        Log.d(TAG, "Cancelled work for schedule: $scheduleId")
    }

    fun scheduleReEnable(context: Context, scheduleId: String, delayMs: Long) {
        val inputData = Data.Builder()
            .putString(ReEnableWorker.KEY_SCHEDULE_ID, scheduleId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReEnableWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            getReEnableWorkName(scheduleId),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.d(TAG, "Scheduled re-enable for schedule $scheduleId in ${delayMs / 60000} minutes")
    }

    private fun getReEnableWorkName(scheduleId: String) = "schedule_reenable_$scheduleId"

    fun isScheduleActiveNow(schedule: Schedule, context: Context? = null): Boolean {
        return getScheduleStartTime(schedule, context) != null
    }

    fun getScheduleStartTime(schedule: Schedule, context: Context? = null): Long? {
        val now = LocalDateTime.now()
        val timing = schedule.timing

        if (timing.type == ScheduleTiming.ScheduleType.MANUAL) return null

        val startTime = timing.time ?: return null
        val endTime = timing.endTime ?: return null

        val candidates = listOf(
            now.withHour(startTime.hour).withMinute(startTime.minute).withSecond(0).withNano(0),
            now.minusDays(1).withHour(startTime.hour).withMinute(startTime.minute).withSecond(0).withNano(0)
        )

        for (startCandidate in candidates) {
            val endCandidate = if (endTime.isBefore(startTime)) {
                startCandidate.plusDays(1).withHour(endTime.hour).withMinute(endTime.minute).withSecond(0).withNano(0)
            } else {
                startCandidate.withHour(endTime.hour).withMinute(endTime.minute).withSecond(0).withNano(0)
            }

            if ((now.isEqual(startCandidate) || now.isAfter(startCandidate)) && now.isBefore(endCandidate)) {
                if (timing.type == ScheduleTiming.ScheduleType.WEEKLY) {
                    if (timing.daysOfWeek.contains(startCandidate.dayOfWeek) && conditionsAreMet(timing, context)) {
                        return startCandidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                } else {
                    if (conditionsAreMet(timing, context)) {
                        return startCandidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                }
            }
        }

        return null
    }

    private fun conditionsAreMet(timing: ScheduleTiming, context: Context?): Boolean {
        return motionConditionIsMet(timing.motionCondition, context) &&
                wifiConditionIsMet(timing.wifiCondition, context)
    }

    private fun motionConditionIsMet(condition: MotionCondition?, context: Context?): Boolean {
        if (condition == null) return true
        val appContext = context?.applicationContext ?: return false
        val latest = ActivityRecognitionManager(appContext).latestActivityJson() ?: return false
        val currentActivity = MotionCondition.Activity.fromAssistantName(latest.optString("activity"))
            ?: return false
        return currentActivity == condition.activity
    }

    private fun wifiConditionIsMet(condition: WifiCondition?, context: Context?): Boolean {
        if (condition == null) return true
        val appContext = context?.applicationContext ?: return false
        val current = WifiContextProvider(appContext).currentWifi() ?: return false
        val ssidMatches = current.ssid == condition.ssid
        val bssidMatches = condition.bssid == null || current.bssid == condition.bssid
        return ssidMatches && bssidMatches
    }
}
