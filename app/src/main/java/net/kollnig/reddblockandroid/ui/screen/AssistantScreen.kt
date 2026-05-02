package net.kollnig.reddblockandroid.ui.screen

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kollnig.reddblockandroid.assistant.ActivityRecognitionManager
import net.kollnig.reddblockandroid.assistant.AssistantImportParser
import net.kollnig.reddblockandroid.assistant.ContextProvider
import net.kollnig.reddblockandroid.assistant.ImportedAssistantAction
import net.kollnig.reddblockandroid.assistant.PromptOptions
import net.kollnig.reddblockandroid.assistant.ScheduleAmendmentProposal
import net.kollnig.reddblockandroid.assistant.ScheduleProposal
import net.kollnig.reddblockandroid.assistant.WifiContextProvider
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onReviewProposal: (ScheduleProposal) -> Unit,
    onReviewAmendment: (ScheduleAmendmentProposal) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val contextProvider = remember { ContextProvider(context) }
    val activityRecognitionManager = remember { ActivityRecognitionManager(context) }
    val wifiContextProvider = remember { WifiContextProvider(context) }
    val screenPrefs = remember {
        context.getSharedPreferences("assistant_prompt_flow", Context.MODE_PRIVATE)
    }

    var userProblem by remember { mutableStateOf(screenPrefs.getString(KEY_OPENING_MESSAGE, "") ?: "") }
    var goals by remember { mutableStateOf(screenPrefs.getString(KEY_GOALS, "") ?: "") }
    var usagePermissionAvailable by remember { mutableStateOf(contextProvider.hasUsageStatsPermission()) }
    var options by remember {
        mutableStateOf(
            PromptOptions(
                includeExistingSchedules = screenPrefs.getBoolean(KEY_INCLUDE_SCHEDULES, true),
                includeTopUsedApps = screenPrefs.getBoolean(KEY_INCLUDE_TOP_USED, usagePermissionAvailable),
                includeScheduledApps = screenPrefs.getBoolean(KEY_INCLUDE_SCHEDULED_APPS, true),
                includeAllInstalledApps = screenPrefs.getBoolean(KEY_INCLUDE_ALL_APPS, false),
                includeUsageStats = screenPrefs.getBoolean(KEY_INCLUDE_USAGE, false),
                includeMotionContext = screenPrefs.getBoolean(KEY_INCLUDE_MOTION, false),
                includeWifiContext = screenPrefs.getBoolean(KEY_INCLUDE_WIFI, false),
                includeSavedWifiNetworks = screenPrefs.getBoolean(KEY_INCLUDE_SAVED_WIFI, false),
                includeGoals = true
            )
        )
    }
    var parsedActions by remember { mutableStateOf<List<ImportedAssistantAction>>(emptyList()) }
    var importError by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingCopyAfterSettings by remember { mutableStateOf(false) }
    var hasSeenSettings by remember { mutableStateOf(screenPrefs.getBoolean(KEY_HAS_SEEN_SETTINGS, false)) }

    val usageSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        usagePermissionAvailable = contextProvider.hasUsageStatsPermission()
        if (usagePermissionAvailable) {
            options = options.copy(includeTopUsedApps = true)
        }
    }
    val motionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        options = options.copy(includeMotionContext = granted)
        if (granted) activityRecognitionManager.startUpdates()
        if (!granted) scope.launch { snackbarHostState.showSnackbar("Motion permission was not granted.") }
    }
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        options = options.copy(includeWifiContext = granted, includeSavedWifiNetworks = granted)
        if (!granted) scope.launch { snackbarHostState.showSnackbar("Wi-Fi context permission was not granted.") }
    }

    fun saveSettings() {
        screenPrefs.edit()
            .putBoolean(KEY_HAS_SEEN_SETTINGS, true)
            .putString(KEY_OPENING_MESSAGE, userProblem)
            .putString(KEY_GOALS, goals)
            .putBoolean(KEY_INCLUDE_SCHEDULES, options.includeExistingSchedules)
            .putBoolean(KEY_INCLUDE_TOP_USED, options.includeTopUsedApps)
            .putBoolean(KEY_INCLUDE_SCHEDULED_APPS, options.includeScheduledApps)
            .putBoolean(KEY_INCLUDE_ALL_APPS, options.includeAllInstalledApps)
            .putBoolean(KEY_INCLUDE_USAGE, options.includeUsageStats)
            .putBoolean(KEY_INCLUDE_MOTION, options.includeMotionContext)
            .putBoolean(KEY_INCLUDE_WIFI, options.includeWifiContext)
            .putBoolean(KEY_INCLUDE_SAVED_WIFI, options.includeSavedWifiNetworks)
            .apply()
        hasSeenSettings = true
    }

    fun copyPrompt() {
        isGenerating = true
        try {
            val prompt = contextProvider.buildPrompt(
                userProblem = userProblem.trim(),
                goals = goals.trim(),
                options = options
            )
            clipboardManager.setText(AnnotatedString(prompt))
            scope.launch { snackbarHostState.showSnackbar("Prompt copied.") }
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar(e.message ?: "Could not copy prompt.") }
        } finally {
            isGenerating = false
        }
    }

    fun copyPromptWithFirstRunCheck() {
        if (!hasSeenSettings) {
            pendingCopyAfterSettings = true
            showSettings = true
            return
        }
        copyPrompt()
    }

    fun importReply(replyText: String) {
        parsedActions = emptyList()
        importError = null
        try {
            val actions = AssistantImportParser.parseActions(
                replyText = replyText,
                installedApps = contextProvider.getInstalledApps()
            ).getOrElse { throw it }
            parsedActions = actions
            scope.launch { snackbarHostState.showSnackbar("Parsed ${actions.size} schedule action${if (actions.size == 1) "" else "s"}.") }
        } catch (e: Exception) {
            importError = e.message ?: "Could not import the AI reply."
        }
    }

    fun pasteAndImportReply() {
        val text = clipboardManager.getText()?.text.orEmpty()
        if (text.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Clipboard is empty.") }
            return
        }
        importReply(text)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Helper", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Prompt settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                SectionSurface {
                    Text(
                        "Configure your digital home with your favourite AI chatbot.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Step 1: copy a ReDD prompt and paste it into ChatGPT, Claude, Gemini, or another AI you like. Talk there normally about schedules, routines, or digital self-control.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Step 2: when the AI suggests ReDD changes, copy its full reply and paste it back here. ReDD will show the proposed schedules for review before saving.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                SectionSurface {
                    Button(
                        onClick = { copyPromptWithFirstRunCheck() },
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Step 1: Copy prompt")
                    }
                    Button(
                        onClick = { pasteAndImportReply() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Step 2: Paste AI reply")
                    }
                }
            }

            importError?.let {
                item {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (parsedActions.isNotEmpty()) {
                item {
                    Text(
                        "Parsed actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                itemsIndexed(parsedActions) { index, action ->
                    ParsedActionCard(
                        index = index,
                        action = action,
                        onReviewProposal = onReviewProposal,
                        onReviewAmendment = onReviewAmendment
                    )
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = {
                showSettings = false
                pendingCopyAfterSettings = false
            },
            title = { Text("Prompt settings", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 520.dp)
                ) {
                    item {
                        Text(
                            "Choose what ReDD includes in the prompt before you paste it into your favourite AI chatbot.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = userProblem,
                            onValueChange = { userProblem = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Optional first question") },
                            placeholder = { Text("Leave blank for starter options") },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = goals,
                            onValueChange = { goals = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Goal notes") },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    item {
                        PromptOptionSwitch(
                            title = "Existing schedules",
                            description = "Names, blocked apps/sites, timing, friction, pause duration, and context conditions.",
                            checked = options.includeExistingSchedules,
                            onCheckedChange = { options = options.copy(includeExistingSchedules = it) }
                        )
                    }
                    item {
                        PromptOptionSwitch(
                            title = "Top 20 used apps",
                            description = if (usagePermissionAvailable) "Default app list for recommendations." else "Needs Usage Access permission.",
                            checked = options.includeTopUsedApps,
                            onCheckedChange = { enabled ->
                                if (!enabled || usagePermissionAvailable) {
                                    options = options.copy(includeTopUsedApps = enabled)
                                } else {
                                    try {
                                        usageSettingsLauncher.launch(contextProvider.usageSettingsIntent())
                                    } catch (_: ActivityNotFoundException) {
                                        scope.launch { snackbarHostState.showSnackbar("Could not open Usage Access settings.") }
                                    }
                                }
                            }
                        )
                    }
                    item {
                        PromptOptionSwitch(
                            title = "Apps already in schedules",
                            description = "Keeps existing blocked apps available even if they are not in today's top usage.",
                            checked = options.includeScheduledApps,
                            onCheckedChange = { options = options.copy(includeScheduledApps = it) }
                        )
                    }
                    item {
                        PromptOptionSwitch(
                            title = "All installed apps",
                            description = "Opt-in full app inventory for broader recommendations.",
                            checked = options.includeAllInstalledApps,
                            onCheckedChange = { options = options.copy(includeAllInstalledApps = it) }
                        )
                    }
                    item {
                        PromptOptionSwitch(
                            title = "Usage stats",
                            description = "App labels, package names, minutes used, and time-of-day buckets.",
                            checked = options.includeUsageStats,
                            onCheckedChange = { enabled ->
                                if (!enabled || usagePermissionAvailable) {
                                    options = options.copy(includeUsageStats = enabled)
                                } else {
                                    try {
                                        usageSettingsLauncher.launch(contextProvider.usageSettingsIntent())
                                    } catch (_: ActivityNotFoundException) {
                                        scope.launch { snackbarHostState.showSnackbar("Could not open Usage Access settings.") }
                                    }
                                }
                            }
                        )
                    }
                    item {
                        PromptOptionSwitch(
                            title = "Motion context",
                            description = "Recent Android activity such as still, walking, cycling, running, or in vehicle.",
                            checked = options.includeMotionContext,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    options = options.copy(includeMotionContext = false)
                                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    options = options.copy(includeMotionContext = true)
                                    activityRecognitionManager.startUpdates()
                                } else {
                                    motionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                }
                            }
                        )
                    }
                    item {
                        PromptOptionSwitch(
                            title = "Wi-Fi context",
                            description = "Current Wi-Fi name for home, work, or campus schedules.",
                            checked = options.includeWifiContext,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    options = options.copy(includeWifiContext = false)
                                } else if (wifiContextProvider.hasPermission()) {
                                    options = options.copy(includeWifiContext = true)
                                } else {
                                    wifiPermissionLauncher.launch(wifiContextProvider.runtimePermission())
                                }
                            }
                        )
                    }
                    item {
                        PromptOptionSwitch(
                            title = "Saved Wi-Fi networks",
                            description = "Saved labels and SSIDs used for context-triggered schedules.",
                            checked = options.includeSavedWifiNetworks,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    options = options.copy(includeSavedWifiNetworks = false)
                                } else if (wifiContextProvider.hasPermission()) {
                                    options = options.copy(includeSavedWifiNetworks = true)
                                } else {
                                    wifiPermissionLauncher.launch(wifiContextProvider.runtimePermission())
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    saveSettings()
                    showSettings = false
                    if (pendingCopyAfterSettings) {
                        pendingCopyAfterSettings = false
                        copyPrompt()
                    }
                }) {
                    Text(if (pendingCopyAfterSettings) "Save and copy" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSettings = false
                    pendingCopyAfterSettings = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun PromptOptionSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ParsedActionCard(
    index: Int,
    action: ImportedAssistantAction,
    onReviewProposal: (ScheduleProposal) -> Unit,
    onReviewAmendment: (ScheduleAmendmentProposal) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (action) {
                is ImportedAssistantAction.Proposal -> {
                    Text("New schedule ${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(action.proposal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        proposalSummary(action.proposal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(onClick = { onReviewProposal(action.proposal) }) {
                        Text("Review and create")
                    }
                }
                is ImportedAssistantAction.Amendment -> {
                    Text("Schedule change ${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Amend ${action.amendment.originalName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        scheduleSummary(action.amendment.updatedSchedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(onClick = { onReviewAmendment(action.amendment) }) {
                        Text("Review changes")
                    }
                }
            }
        }
    }
}

private fun proposalSummary(proposal: ScheduleProposal): String {
    val targets = (proposal.blockedApps + proposal.blockedWebsites).joinToString(", ")
    val conditions = listOfNotNull(
        proposal.timing.motionCondition?.let { "when ${it.activity.label.lowercase()}" },
        proposal.timing.wifiCondition?.let { "on ${it.label} Wi-Fi" }
    )
    val timing = when (proposal.timing.type) {
        ScheduleTiming.ScheduleType.MANUAL -> "Manual"
        ScheduleTiming.ScheduleType.DAILY ->
            "Daily ${proposal.timing.timeHour.twoDigits()}:${proposal.timing.timeMinute.twoDigits()}-${proposal.timing.endTimeHour.twoDigits()}:${proposal.timing.endTimeMinute.twoDigits()}"
        ScheduleTiming.ScheduleType.WEEKLY ->
            "Weekly ${proposal.timing.daysOfWeek.joinToString { it.name.take(3) }} ${proposal.timing.timeHour.twoDigits()}:${proposal.timing.timeMinute.twoDigits()}-${proposal.timing.endTimeHour.twoDigits()}:${proposal.timing.endTimeMinute.twoDigits()}"
    }
    val activeWhen = if (conditions.isEmpty()) "" else "\nActive ${conditions.joinToString(" and ")}"
    return "$timing$activeWhen\nBlocks: $targets\nFriction: ${proposal.frictionWordCount} words\nPause: ${proposal.autoReenableMinutes.pauseLabel()}"
}

private fun scheduleSummary(schedule: Schedule): String {
    val timing = when (schedule.timing.type) {
        ScheduleTiming.ScheduleType.MANUAL -> "Manual"
        ScheduleTiming.ScheduleType.DAILY ->
            "Daily ${schedule.timing.timeHour.twoDigits()}:${schedule.timing.timeMinute.twoDigits()}-${schedule.timing.endTimeHour.twoDigits()}:${schedule.timing.endTimeMinute.twoDigits()}"
        ScheduleTiming.ScheduleType.WEEKLY ->
            "Weekly ${schedule.timing.daysOfWeek.joinToString { it.name.take(3) }} ${schedule.timing.timeHour.twoDigits()}:${schedule.timing.timeMinute.twoDigits()}-${schedule.timing.endTimeHour.twoDigits()}:${schedule.timing.endTimeMinute.twoDigits()}"
    }
    val conditions = listOfNotNull(
        schedule.timing.motionCondition?.let { "when ${it.activity.label.lowercase()}" },
        schedule.timing.wifiCondition?.let { "on ${it.label} Wi-Fi" }
    )
    val activeWhen = if (conditions.isEmpty()) "" else "\nActive ${conditions.joinToString(" and ")}"
    val targets = (schedule.blockedApps + schedule.blockedWebsites).joinToString(", ")
    return "$timing$activeWhen\nBlocks: $targets\nFriction: ${schedule.frictionWordCount} words\nPause: ${schedule.autoReenableMinutes.pauseLabel()}"
}

private fun Int?.twoDigits(): String = String.format("%02d", this ?: 0)

private fun Int.pauseLabel(): String = when (this) {
    0 -> "until manually re-enabled"
    5, 10, 15, 30 -> "$this minutes"
    60 -> "1 hour"
    120 -> "2 hours"
    240 -> "4 hours"
    480 -> "8 hours"
    1440 -> "24 hours"
    else -> "$this minutes"
}

private const val KEY_HAS_SEEN_SETTINGS = "has_seen_settings"
private const val KEY_OPENING_MESSAGE = "opening_message"
private const val KEY_GOALS = "goals"
private const val KEY_INCLUDE_SCHEDULES = "include_schedules"
private const val KEY_INCLUDE_TOP_USED = "include_top_used"
private const val KEY_INCLUDE_SCHEDULED_APPS = "include_scheduled_apps"
private const val KEY_INCLUDE_ALL_APPS = "include_all_apps"
private const val KEY_INCLUDE_USAGE = "include_usage"
private const val KEY_INCLUDE_MOTION = "include_motion"
private const val KEY_INCLUDE_WIFI = "include_wifi"
private const val KEY_INCLUDE_SAVED_WIFI = "include_saved_wifi"
