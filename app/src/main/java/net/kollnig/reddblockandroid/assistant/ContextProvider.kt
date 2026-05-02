package net.kollnig.reddblockandroid.assistant

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import net.kollnig.reddblockandroid.data.SavedWifiNetworksStore
import net.kollnig.reddblockandroid.schedule.Schedules
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ContextProvider(context: Context) {
    private val appContext = context.applicationContext
    private val activityRecognitionManager = ActivityRecognitionManager(appContext)
    private val wifiContextProvider = WifiContextProvider(appContext)
    private val savedWifiNetworksStore = SavedWifiNetworksStore(appContext)

    fun buildContext(goals: String = ""): AssistantContext {
        val installedApps = getInstalledApps()
        val scheduledPackages = Schedules.getAll().flatMap { it.blockedApps }.toSet()
        val usageEnabled = hasUsageStatsPermission()
        return AssistantContext(
            existingSchedules = Schedules.exportSchedules(),
            installedApps = installedApps,
            usageSummaries = if (usageEnabled) getUsageSummaries(installedApps) else emptyList(),
            scheduledApps = installedApps.filter { it.packageName in scheduledPackages },
            motionStateJson = if (activityRecognitionManager.hasPermission()) {
                activityRecognitionManager.latestActivityJson()
            } else {
                null
            },
            wifiJson = if (wifiContextProvider.hasPermission()) {
                wifiContextProvider.currentWifiJson()
            } else {
                null
            },
            savedWifiNetworks = savedWifiNetworksStore.getNetworks().map {
                SavedWifiNetworkSummary(label = it.label, ssid = it.ssid)
            },
            goals = goals
        )
    }

    fun buildPrompt(userProblem: String, goals: String, options: PromptOptions): String {
        val context = buildContext(goals)
        val selectedApps = linkedMapOf<String, InstalledAppSummary>()
        if (options.includeTopUsedApps) {
            context.usageSummaries.forEach { usage ->
                selectedApps[usage.packageName] = InstalledAppSummary(usage.packageName, usage.label)
            }
        }
        if (options.includeScheduledApps) {
            context.scheduledApps.forEach { app -> selectedApps[app.packageName] = app }
        }
        if (options.includeAllInstalledApps) {
            context.installedApps.forEach { app -> selectedApps[app.packageName] = app }
        }

        val export = JSONObject()
            .put("opening_message", userProblem)
            .put("local_time", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault())))
            .put("timezone", ZoneId.systemDefault().id)
            .put("included_data", JSONObject()
                .put("existing_schedules", options.includeExistingSchedules)
                .put("top_used_apps", options.includeTopUsedApps)
                .put("scheduled_apps", options.includeScheduledApps)
                .put("all_installed_apps", options.includeAllInstalledApps)
                .put("usage_stats", options.includeUsageStats)
                .put("motion_context", options.includeMotionContext)
                .put("wifi_context", options.includeWifiContext)
                .put("saved_wifi_networks", options.includeSavedWifiNetworks)
                .put("goal_notes", options.includeGoals))
            .put("user_goals", if (options.includeGoals) context.goals else "")
            .put("existing_schedules_read_only", if (options.includeExistingSchedules) JSONArray(context.existingSchedules) else JSONArray())
            .put("apps_available_for_recommendations", JSONArray().apply {
                selectedApps.values.sortedBy { it.label.lowercase() }.forEach {
                    put(JSONObject().put("packageName", it.packageName).put("label", it.label))
                }
            })
            .put("usage_stats", JSONArray().apply {
                if (options.includeUsageStats) {
                    context.usageSummaries.forEach {
                        put(JSONObject()
                            .put("packageName", it.packageName)
                            .put("label", it.label)
                            .put("minutesUsed", it.minutesUsed)
                            .put("bucket", it.bucket))
                    }
                }
            })
            .put("motion_state", if (options.includeMotionContext) context.motionStateJson ?: JSONObject.NULL else JSONObject.NULL)
            .put("current_wifi", if (options.includeWifiContext) context.wifiJson ?: JSONObject.NULL else JSONObject.NULL)
            .put("saved_wifi_networks", JSONArray().apply {
                if (options.includeSavedWifiNetworks) {
                    context.savedWifiNetworks.forEach { network ->
                        put(JSONObject()
                            .put("label", network.label)
                            .put("ssid", network.ssid ?: JSONObject.NULL))
                    }
                }
            })

        return """
You are Ulrik, a research-informed digital self-control assistant helping me use ReDD Block.

ReDD Block is a local Android app for reducing distracting app and website use with reviewable blocking schedules. I copied this prompt from ReDD Block into this AI chat. We can now go back and forth here like a normal chatbot.

Your role:
- Help me think through app-use wellbeing, digital distraction, routines, cues, and blocking schedules.
- Treat problematic use as cue-driven, automatic, and friction-sensitive, not as weak willpower.
- Be warm, practical, and concise.
- Stay within digital self-control and app/website use. Do not provide medical, mental-health, diagnosis, therapy, or crisis advice.
- If I have not asked a specific question yet, start by offering a few useful next steps such as: "Analyze my schedules", "Help me with my morning routine", "Suggest a bedtime schedule", "Reduce commute scrolling", and "Ask any question about digital self-control".

How to work with ReDD Block:
- You may answer normal questions conversationally without any JSON.
- Include a redd-block-json block only when you are proposing concrete ReDD Block actions that I should paste back into the app for review.
- When you include redd-block-json, first explain the options in friendly user-facing language. Then include exactly one fenced block tagged redd-block-json.
- The JSON is for the app. The surrounding text is for me.
- Do not include JSON for exploratory advice, diagnostic questions, general explanations, or options that are not ready to import.

Safety and validity rules for JSON actions:
- Only use Android package names listed in apps_available_for_recommendations. If the needed app is not listed, ask me to export more apps instead of inventing a package name.
- Do not delete, disable, or silently weaken existing schedules.
- Propose new schedules or amendments that I can review in the app.
- Use bare website domains only, such as reddit.com.

When a concrete schedule action is ready, use this shape:
```redd-block-json
{
  "version": 1,
  "actions": [
    {
      "type": "propose_schedule",
      "arguments": {
        "name": "Short name",
        "blockedApps": ["com.example.app"],
        "blockedWebsites": ["example.com"],
        "timing": {
          "type": "DAILY",
          "timeHour": 21,
          "timeMinute": 0,
          "endTimeHour": 23,
          "endTimeMinute": 30,
          "daysOfWeek": [],
          "motionCondition": null,
          "wifiCondition": null
        },
        "frictionWordCount": 15,
        "autoReenableMinutes": 10,
        "rationale": "Plain language reason.",
        "experimentDays": 7
      }
    }
  ]
}
```

For amendments, use "type": "propose_schedule_amendment" and include "scheduleId" plus full replacement schedule fields in arguments. The only allowed schedule timing types are DAILY, WEEKLY, and MANUAL. Weekly schedules require daysOfWeek entries using MONDAY through SUNDAY. Manual schedules must use null times, empty daysOfWeek, and no motion or Wi-Fi condition. Allowed autoReenableMinutes values are 0, 5, 10, 15, 30, 60, 120, 240, 480, and 1440. frictionWordCount must be 1-50.

Here is my ReDD Block context. Treat it as private context for this chat:
${export.toString(2)}
""".trimIndent()
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
}
