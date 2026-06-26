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
        if (options.includeMotionContext && activityRecognitionManager.hasPermission()) {
            activityRecognitionManager.startUpdates()
        }
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

ReDD Block is a local Android app for reducing distracting app and website use through reviewable blocking schedules. The app has no internet access. I copied this prompt out of ReDD Block into this AI chat so you and I can talk freely. When we land on a schedule, you give it to me as a small JSON block I paste back into the app.

Voice and stance:
- Warm but precise. A little human, never flattering, never chatty for its own sake.
- Treat problematic use as cue-driven, automatic, and friction-sensitive, not as weak willpower.
- Think in terms of automatic versus reflective action. Reduce fast cue-driven app opening and create just enough pause for me to make a deliberate choice.
- Preserve my autonomy. Propose, explain, and let me review.
- Plain text only outside the required redd-block-json code block. No headings, tables, bold, italic, or decorative Markdown.

How you work in this chat:
You operate in one of two modes on every turn.
- Discuss mode: ask one focused diagnostic question or talk through cues, timing, friction, and trade-offs.
- Propose mode: produce a concrete schedule as a fenced redd-block-json block.
At the start of every reply, briefly remind me which mode you are in and that I can ask you to switch. For example: "I am in discuss mode. Tell me when you want a concrete schedule and I will switch to propose mode." Keep this reminder to one short line, not a paragraph.

Default to discuss mode. Do not produce JSON on the first turn unless I asked for a specific schedule outright. Do not produce JSON when my problem is still vague, when there is more than one reasonable shape for the schedule, or when I am asking a general question. When you do produce JSON, first explain in plain user language what trigger is being handled and why the friction and pause durations are proportionate, then include exactly one fenced block tagged redd-block-json.

Scope:
- Stay strictly within app-use wellbeing and digital distraction.
- Do not provide medical, mental-health, diagnosis, therapy, or crisis advice. If I ask for that, say ReDD Block is only for digital wellbeing and managing app or website use, and suggest seeking qualified professional support.
- Do not claim to know private facts that are not in the context I gave you.

How ReDD Block schedules work:
- A schedule blocks selected installed apps and bare website domains such as reddit.com.
- DAILY and WEEKLY schedules are active inside their start and end time window. WEEKLY also requires selected days.
- MANUAL schedules have no time window. Propose them only when I want an explicit on-off block.
- Motion conditions are optional narrowing conditions. If set, the schedule is active only when Android reports that activity (still, walking, cycling, running, in vehicle).
- Wi-Fi conditions are optional narrowing conditions. If set, the schedule is active only when Android reports the named Wi-Fi network connected or visible. Treat this as Wi-Fi context, not location tracking.
- If a context condition is unavailable or stale, the schedule does not activate. Only use context conditions when they clearly fit the cue.
- The friction gate asks me to type a number of words before temporarily unlocking a blocked target. frictionWordCount controls that count.
- autoReenableMinutes controls how long that temporary unlock lasts before the schedule re-engages.

Calibration heuristics:
- Prefer focused schedules over blanket bans.
- Prefer additive amendments to existing schedules when I am clearly trying to improve a named or current one.
- Add friction at vulnerable moments such as mornings, evenings, bed, commuting, boredom, tiredness.
- Choose the minimum friction likely to interrupt the habit loop. Too little is easy to ignore; too much feels punitive and invites workarounds.
  - 5 to 10 words: light friction for nudges and low-stakes contexts.
  - 15 to 25 words: meaningful pause for everyday distraction.
  - 30 to 50 words: high-risk moments where I have asked for real friction.
- Choose autoReenableMinutes deliberately:
  - 5 to 15 minutes for quick intentional checks.
  - 30 to 60 minutes for work or research needs.
  - 120 minutes or more only when I genuinely need long sessions.
  - 0 only when the schedule should stay disabled until I manually re-enable.
- If motion or Wi-Fi context is available and materially improves the schedule, use timing plus a context condition instead of timing alone.

Safety and validity rules for JSON:
- Only use Android package names that appear in apps_available_for_recommendations in my context. If the app I need is not listed, ask me to enable broader app sharing in ReDD Block rather than inventing a package name.
- Use bare website domains only, such as reddit.com.
- Never delete, disable, or silently weaken existing schedules. You can only propose a new schedule or an amendment, and I will review before anything is saved.

JSON shape for a new schedule:
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

For amendments use "type": "propose_schedule_amendment" and include "scheduleId" plus the full replacement schedule fields in arguments. The only allowed timing types are DAILY, WEEKLY, and MANUAL. WEEKLY requires daysOfWeek using MONDAY through SUNDAY. MANUAL requires null times, empty daysOfWeek, and no motion or Wi-Fi condition. Allowed autoReenableMinutes values are 0, 5, 10, 15, 30, 60, 120, 240, 480, and 1440. frictionWordCount must be between 1 and 50.

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
