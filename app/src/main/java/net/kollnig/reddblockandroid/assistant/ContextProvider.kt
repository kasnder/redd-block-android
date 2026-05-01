package net.kollnig.reddblockandroid.assistant

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.Build
import android.provider.Settings
import net.kollnig.reddblockandroid.data.SavedWifiNetworksStore
import net.kollnig.reddblockandroid.schedule.Schedules
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ContextProvider(context: Context, private val preferences: AssistantPreferences) {
    private val appContext = context.applicationContext
    private val activityRecognitionManager = ActivityRecognitionManager(appContext)
    private val wifiContextProvider = WifiContextProvider(appContext)
    private val savedWifiNetworksStore = SavedWifiNetworksStore(appContext)

    fun buildContext(): AssistantContext {
        val installedApps = getInstalledApps()
        val usageEnabled = preferences.isUsageSharingEnabled() && hasUsageStatsPermission()
        return AssistantContext(
            existingSchedules = Schedules.exportSchedules(),
            installedApps = installedApps,
            usageSummaries = if (usageEnabled) getUsageSummaries(installedApps) else emptyList(),
            usageSharingEnabled = usageEnabled,
            motionSharingEnabled = preferences.isMotionSharingEnabled() && activityRecognitionManager.hasPermission(),
            motionStateJson = if (preferences.isMotionSharingEnabled() && activityRecognitionManager.hasPermission()) {
                activityRecognitionManager.latestActivityJson()
            } else {
                null
            },
            wifiSharingEnabled = preferences.isWifiSharingEnabled() && wifiContextProvider.hasPermission(),
            wifiJson = if (preferences.isWifiSharingEnabled() && wifiContextProvider.hasPermission()) {
                wifiContextProvider.currentWifiJson()
            } else {
                null
            },
            goals = preferences.getGoals()
        )
    }

    fun buildPrompt(userMessage: String, conversationMessages: List<AssistantMessage>): String {
        val context = buildContext()
        return JSONObject()
            .put("user_problem", userMessage)
            .put("conversation_history", JSONArray().apply {
                conversationMessages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
                    put(
                        JSONObject()
                            .put("role", message.role.name.lowercase())
                            .put("text", message.text)
                    )
                }
            })
            .put("user_goals", context.goals)
            .put("local_time", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault())))
            .put("timezone", ZoneId.systemDefault().id)
            .put("existing_schedules_read_only", JSONArray(context.existingSchedules))
            .put("installed_apps", JSONArray().apply {
                context.installedApps.forEach {
                    put(JSONObject().put("packageName", it.packageName).put("label", it.label))
                }
            })
            .put("usage_stats", JSONArray().apply {
                context.usageSummaries.forEach {
                    put(
                        JSONObject()
                            .put("packageName", it.packageName)
                            .put("label", it.label)
                            .put("minutesUsed", it.minutesUsed)
                            .put("bucket", it.bucket)
                    )
                }
            })
            .put("usage_stats_shared", context.usageSharingEnabled)
            .put("motion_shared", context.motionSharingEnabled)
            .put("motion_state", context.motionStateJson ?: JSONObject.NULL)
            .put("wifi_shared", context.wifiSharingEnabled)
            .put("current_wifi", context.wifiJson ?: JSONObject.NULL)
            .put("saved_wifi_networks", JSONArray().apply {
                savedWifiNetworksStore.getNetworks().forEach { network ->
                    put(
                        JSONObject()
                            .put("label", network.label)
                            .put("ssid", network.ssid ?: JSONObject.NULL)
                    )
                }
            })
            .toString(2)
    }

    fun getInstalledApps(): List<InstalledAppSummary> {
        val packageManager = appContext.packageManager
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val hasLauncher = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
                val isNotSelf = appInfo.packageName != appContext.packageName
                (!isSystem || hasLauncher) && isNotSelf
            }
            .map { appInfo ->
                InstalledAppSummary(
                    packageName = appInfo.packageName,
                    label = packageManager.getApplicationLabel(appInfo).toString()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    private fun getUsageSummaries(installedApps: List<InstalledAppSummary>): List<UsageSummary> {
        val labels = installedApps.associate { it.packageName to it.label }
        val usageStatsManager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val start = now - 24 * 60 * 60 * 1000L
        return usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
            .orEmpty()
            .filter { it.totalTimeInForeground > 60_000L && labels.containsKey(it.packageName) }
            .sortedByDescending { it.totalTimeInForeground }
            .take(20)
            .map {
                UsageSummary(
                    packageName = it.packageName,
                    label = labels[it.packageName] ?: it.packageName,
                    minutesUsed = it.totalTimeInForeground / 60_000L,
                    bucket = bucketForLastUsed(it.lastTimeUsed)
                )
            }
    }

    private fun bucketForLastUsed(lastTimeUsed: Long): String {
        val hour = Instant.ofEpochMilli(lastTimeUsed).atZone(ZoneId.systemDefault()).hour
        return when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 16
    }
}
