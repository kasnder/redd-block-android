package net.kollnig.reddblockandroid.ui.screen

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.assistant.ActivityRecognitionManager
import net.kollnig.reddblockandroid.assistant.WifiContextProvider
import net.kollnig.reddblockandroid.data.MotionCondition
import net.kollnig.reddblockandroid.data.SavedWifiNetwork
import net.kollnig.reddblockandroid.data.SavedWifiNetworksStore
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.data.WifiCondition
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateScheduleScreen(
    scheduleId: String?,
    draftSchedule: Schedule? = null,
    onBackPressed: () -> Unit,
    onSaveComplete: () -> Unit,
    onFrictionGateRequired: (wordCount: Int, onPassed: () -> Unit) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val existingSchedule = scheduleId?.let { Schedules.get(it) }
    val initialSchedule = draftSchedule?.takeIf { draft ->
        scheduleId == null || draft.id == scheduleId
    } ?: existingSchedule
    val activityRecognitionManager = remember { ActivityRecognitionManager(context) }
    val savedWifiNetworksStore = remember { SavedWifiNetworksStore(context) }
    val wifiContextProvider = remember { WifiContextProvider(context) }

    var scheduleName by remember { mutableStateOf(initialSchedule?.name ?: "") }
    var scheduleType by remember {
        mutableStateOf(initialSchedule?.timing?.type ?: ScheduleTiming.ScheduleType.WEEKLY)
    }
    var selectedTime by remember {
        mutableStateOf(initialSchedule?.timing?.time ?: LocalTime.of(9, 0))
    }
    var selectedEndTime by remember {
        mutableStateOf(initialSchedule?.timing?.endTime ?: LocalTime.of(17, 0))
    }
    var selectedDays by remember {
        mutableStateOf(initialSchedule?.timing?.daysOfWeek ?: emptySet())
    }
    var blockedApps by remember {
        mutableStateOf(initialSchedule?.blockedApps ?: emptyList())
    }
    var blockedWebsites by remember {
        mutableStateOf(initialSchedule?.blockedWebsites ?: emptyList())
    }
    var frictionWordCount by remember {
        mutableIntStateOf(initialSchedule?.frictionWordCount ?: 15)
    }
    var autoReenableMinutes by remember {
        mutableIntStateOf(initialSchedule?.autoReenableMinutes ?: 1440)
    }
    var motionCondition by remember {
        mutableStateOf(initialSchedule?.timing?.motionCondition)
    }
    var savedWifiNetworks by remember { mutableStateOf(savedWifiNetworksStore.getNetworks()) }
    var currentWifi by remember { mutableStateOf(wifiContextProvider.currentWifi()) }
    var wifiConditionEnabled by remember {
        mutableStateOf(initialSchedule?.timing?.wifiCondition != null)
    }
    var selectedWifiLabel by remember {
        mutableStateOf(initialSchedule?.timing?.wifiCondition?.label ?: savedWifiNetworks.first().label)
    }
    var wifiSsid by remember {
        mutableStateOf(initialSchedule?.timing?.wifiCondition?.ssid ?: currentWifi?.ssid.orEmpty())
    }
    var pendingMotionCondition by remember { mutableStateOf<MotionCondition?>(null) }
    var permissionError by remember { mutableStateOf<String?>(null) }

    var showAppPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val motionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            motionCondition = pendingMotionCondition
            activityRecognitionManager.startUpdates()
            permissionError = null
        } else {
            permissionError = "Motion permission is needed for motion-based schedules."
        }
        pendingMotionCondition = null
    }
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            currentWifi = wifiContextProvider.currentWifi()
            wifiConditionEnabled = true
            if (wifiSsid.isBlank()) {
                wifiSsid = currentWifi?.ssid.orEmpty()
            }
            permissionError = null
        } else {
            wifiConditionEnabled = false
            permissionError = "Location permission is needed for Wi-Fi-based schedules."
        }
    }

    val autoReenableOptions = listOf(
        0 to stringResource(R.string.auto_reenable_never),
        5 to stringResource(R.string.auto_reenable_5min),
        10 to stringResource(R.string.auto_reenable_10min),
        15 to stringResource(R.string.auto_reenable_15min),
        30 to stringResource(R.string.auto_reenable_30min),
        60 to stringResource(R.string.auto_reenable_1hr),
        120 to stringResource(R.string.auto_reenable_2hr),
        240 to stringResource(R.string.auto_reenable_4hr),
        480 to stringResource(R.string.auto_reenable_8hr),
        1440 to stringResource(R.string.auto_reenable_24hr)
    )

    fun hasMotionRuntimePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    fun requestMotionCondition(condition: MotionCondition?) {
        if (condition == null) {
            motionCondition = null
            return
        }
        if (hasMotionRuntimePermission()) {
            motionCondition = condition
            activityRecognitionManager.startUpdates()
        } else {
            pendingMotionCondition = condition
            motionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    fun requestWifiConditionEnabled(enabled: Boolean) {
        if (!enabled) {
            wifiConditionEnabled = false
            return
        }
        if (wifiContextProvider.hasPermission()) {
            currentWifi = wifiContextProvider.currentWifi()
            wifiConditionEnabled = true
            if (wifiSsid.isBlank()) {
                wifiSsid = currentWifi?.ssid.orEmpty()
            }
        } else {
            wifiPermissionLauncher.launch(wifiContextProvider.runtimePermission())
        }
    }

    fun currentWifiCondition(): WifiCondition? {
        if (!wifiConditionEnabled) return null
        val ssid = wifiSsid.trim()
        if (ssid.isBlank()) return null
        return WifiCondition(
            label = selectedWifiLabel,
            ssid = ssid
        )
    }

    fun isWifiConditionValid(): Boolean {
        return !wifiConditionEnabled || currentWifiCondition() != null
    }

    fun buildSchedule(): Schedule {
        val wifiCondition = currentWifiCondition()
        val timing = ScheduleTiming(
            type = scheduleType,
            timeHour = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedTime.hour else null,
            timeMinute = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedTime.minute else null,
            endTimeHour = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedEndTime.hour else null,
            endTimeMinute = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedEndTime.minute else null,
            daysOfWeek = if (scheduleType == ScheduleTiming.ScheduleType.WEEKLY) selectedDays else emptySet(),
            motionCondition = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) motionCondition else null,
            wifiCondition = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) wifiCondition else null
        )

        return Schedule(
            id = existingSchedule?.id ?: UUID.randomUUID().toString(),
            name = scheduleName.trim(),
            isEnabled = if (existingSchedule != null) {
                if (existingSchedule.timing.type != ScheduleTiming.ScheduleType.MANUAL &&
                    scheduleType == ScheduleTiming.ScheduleType.MANUAL) {
                    false
                } else {
                    existingSchedule.isEnabled
                }
            } else {
                scheduleType != ScheduleTiming.ScheduleType.MANUAL
            },
            timing = timing,
            blockedApps = blockedApps,
            blockedWebsites = blockedWebsites,
            frictionWordCount = frictionWordCount,
            autoReenableMinutes = autoReenableMinutes
        )
    }

    /**
     * Returns true if the new schedule is at least as strict as the original.
     * "Strictly more restrictive" means:
     *  - All original blocked apps are still present
     *  - All original blocked websites are still present
     *  - Friction word count has not decreased
     *  - Schedule timing / type / auto-reenable have not changed
     */
    fun isStrictlyMoreRestrictive(original: Schedule, updated: Schedule): Boolean {
        // All original blocked apps must still be present
        if (!updated.blockedApps.containsAll(original.blockedApps)) return false
        // All original blocked websites must still be present
        if (!updated.blockedWebsites.containsAll(original.blockedWebsites)) return false
        // Friction word count must not decrease
        if (updated.frictionWordCount < original.frictionWordCount) return false
        // Schedule type must not change
        if (updated.timing.type != original.timing.type) return false
        // Timing windows must not change
        if (updated.timing.timeHour != original.timing.timeHour ||
            updated.timing.timeMinute != original.timing.timeMinute ||
            updated.timing.endTimeHour != original.timing.endTimeHour ||
            updated.timing.endTimeMinute != original.timing.endTimeMinute) return false
        // Days of week must not change
        if (updated.timing.daysOfWeek != original.timing.daysOfWeek) return false
        // Auto-reenable must not change
        if (updated.autoReenableMinutes != original.autoReenableMinutes) return false
        // Context conditions must not be relaxed or changed
        if (updated.timing.motionCondition != original.timing.motionCondition) return false
        if (updated.timing.wifiCondition != original.timing.wifiCondition) return false
        return true
    }

    fun saveSchedule() {
        if (scheduleName.isBlank()) return
        if (!isWifiConditionValid()) return
        if (scheduleType != ScheduleTiming.ScheduleType.MANUAL && motionCondition != null && !hasMotionRuntimePermission()) {
            pendingMotionCondition = motionCondition
            motionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return
        }
        if (scheduleType != ScheduleTiming.ScheduleType.MANUAL && wifiConditionEnabled && !wifiContextProvider.hasPermission()) {
            wifiPermissionLauncher.launch(wifiContextProvider.runtimePermission())
            return
        }

        val schedule = buildSchedule()
        schedule.timing.wifiCondition?.let { condition ->
            savedWifiNetworksStore.saveNetwork(
                SavedWifiNetwork(
                    label = condition.label,
                    ssid = condition.ssid
                )
            )
            savedWifiNetworks = savedWifiNetworksStore.getNetworks()
        }

        // If editing an active schedule and changes make it less strict, require friction gate
        if (existingSchedule != null &&
            Schedules.isScheduleActive(existingSchedule.id, context) &&
            !isStrictlyMoreRestrictive(existingSchedule, schedule)
        ) {
            onFrictionGateRequired(existingSchedule.frictionWordCount) {
                Schedules.save(schedule, context)
                onSaveComplete()
            }
            return
        }

        Schedules.save(schedule, context)
        onSaveComplete()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existingSchedule != null) stringResource(R.string.edit_schedule)
                        else stringResource(R.string.create_schedule),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (existingSchedule != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = SoftRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── NAME section ──
            SectionHeader(stringResource(R.string.schedule_name).uppercase())
            OutlinedTextField(
                value = scheduleName,
                onValueChange = { scheduleName = it },
                placeholder = { Text(stringResource(R.string.schedule_name), color = MaterialTheme.colorScheme.outline) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // ── WEBSITES section ──
            SectionHeader(stringResource(R.string.blocked_websites).uppercase())

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Chip grid of blocked websites
                    if (blockedWebsites.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            blockedWebsites.forEach { domain ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            domain,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.remove),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { blockedWebsites = blockedWebsites - domain },
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Add website inline
                    var inlineWebsite by remember { mutableStateOf("") }
                    val domainPattern = remember {
                        Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$")
                    }
                    fun cleanDomain(input: String): String {
                        var d = input.lowercase().trim()
                        // Strip protocol prefixes and paths
                        d = d.removePrefix("https://").removePrefix("http://")
                        d = d.removePrefix("www.")
                        d = d.split("/").first()
                        return d
                    }
                    OutlinedTextField(
                        value = inlineWebsite,
                        onValueChange = { inlineWebsite = it },
                        placeholder = { Text(stringResource(R.string.website_placeholder), color = MaterialTheme.colorScheme.outline) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (inlineWebsite.isNotBlank()) {
                                val cleaned = cleanDomain(inlineWebsite)
                                if (cleaned.isNotBlank() && domainPattern.matches(cleaned)) {
                                    blockedWebsites = (blockedWebsites + cleaned).distinct()
                                    inlineWebsite = ""
                                }
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }
            }

            // ── APPS section ──
            SectionHeader(stringResource(R.string.blocked_apps).uppercase())

            // Cache app name lookups
            val appNameCache = remember(blockedApps) {
                blockedApps.associateWith { pkg ->
                    try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(pkg, 0)
                        ).toString()
                    } catch (_: PackageManager.NameNotFoundException) {
                        pkg
                    }
                }
            }

            if (blockedApps.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        blockedApps.forEachIndexed { index, pkg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    appNameCache[pkg] ?: pkg,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { blockedApps = blockedApps - pkg },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.remove),
                                        modifier = Modifier.size(16.dp),
                                        tint = TextHint
                                    )
                                }
                            }
                            if (index < blockedApps.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_app), fontWeight = FontWeight.SemiBold)
            }

            // ── SCHEDULE TYPE section ──
            SectionHeader(stringResource(R.string.schedule_type).uppercase())

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val types = ScheduleTiming.ScheduleType.entries
                types.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = scheduleType == type,
                        onClick = { scheduleType = type },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size)
                    ) {
                        Text(
                            when (type) {
                                ScheduleTiming.ScheduleType.DAILY -> stringResource(R.string.daily)
                                ScheduleTiming.ScheduleType.WEEKLY -> stringResource(R.string.weekly)
                                ScheduleTiming.ScheduleType.MANUAL -> stringResource(R.string.manual)
                            }
                        )
                    }
                }
            }

            // ── Time pickers (not for manual) ──
            if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Start time card
                    Card(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .shadow(1.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                stringResource(R.string.start_time).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TimeBox(String.format("%02d", selectedTime.hour))
                                Text(":", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TimeBox(String.format("%02d", selectedTime.minute))
                            }
                        }
                    }

                    // Arrow
                    Box(
                        modifier = Modifier.align(Alignment.CenterVertically).padding(top = 16.dp)
                    ) {
                        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }

                    // End time card
                    Card(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .shadow(1.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                stringResource(R.string.end_time).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TimeBox(String.format("%02d", selectedEndTime.hour))
                                Text(":", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TimeBox(String.format("%02d", selectedEndTime.minute))
                            }
                        }
                    }
                }
            }

            // ── Day selector (weekly only) ──
            if (scheduleType == ScheduleTiming.ScheduleType.WEEKLY) {
                SectionHeader(stringResource(R.string.days).uppercase())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DayOfWeek.entries.forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable {
                                    selectedDays = if (isSelected) selectedDays - day
                                    else selectedDays + day
                                },
                            shape = CircleShape,
                            color = if (isSelected) DayChipSelected else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) {
                SectionHeader("CONTEXT")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MotionConditionPicker(
                            selected = motionCondition,
                            onSelected = { requestMotionCondition(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Wi-Fi",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Only activate this schedule on a specific network.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = wifiConditionEnabled,
                                onCheckedChange = { requestWifiConditionEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = IndigoPrimary
                                )
                            )
                        }

                        if (wifiConditionEnabled) {
                            WifiConditionEditor(
                                savedNetworks = savedWifiNetworks,
                                selectedLabel = selectedWifiLabel,
                                onNetworkSelected = { network ->
                                    selectedWifiLabel = network.label
                                    wifiSsid = network.ssid.orEmpty()
                                },
                                ssid = wifiSsid,
                                onSsidChange = { wifiSsid = it },
                                currentWifi = currentWifi,
                                onUseCurrentWifi = {
                                    currentWifi?.let { wifi ->
                                        wifiSsid = wifi.ssid
                                    }
                                },
                                isValid = isWifiConditionValid()
                            )
                        }

                        permissionError?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ── FRICTION SETTINGS section ──
            SectionHeader(stringResource(R.string.friction_settings).uppercase())

            // Friction word count
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.friction_word_count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "$frictionWordCount",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }
                    Slider(
                        value = frictionWordCount.toFloat(),
                        onValueChange = { frictionWordCount = it.toInt() },
                        valueRange = 1f..50f,
                        steps = 48,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = IndigoPrimary,
                            activeTrackColor = IndigoPrimary
                        )
                    )
                    Text(
                        stringResource(R.string.friction_word_count_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Auto re-enable
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.auto_reenable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = autoReenableOptions.firstOrNull { it.first == autoReenableMinutes }?.second ?: "",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            autoReenableOptions.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        autoReenableMinutes = minutes
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.auto_reenable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // ── Save button ──
            Button(
                onClick = { saveSchedule() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = scheduleName.isNotBlank() && isWifiConditionValid()
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.save),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Time pickers
    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = selectedTime,
            onConfirm = {
                selectedTime = it
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = selectedEndTime,
            onConfirm = {
                selectedEndTime = it
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    // App picker dialog
    if (showAppPicker) {
        AppPickerDialog(
            alreadySelected = blockedApps.toSet(),
            onAppsSelected = { selected ->
                blockedApps = (blockedApps + selected).distinct()
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }

    // Delete dialog
    if (showDeleteDialog && existingSchedule != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_schedule), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_schedule_confirm, existingSchedule.name)) },
            confirmButton = {
                TextButton(onClick = {
                    if (Schedules.isScheduleActive(existingSchedule.id, context)) {
                        showDeleteDialog = false
                        onFrictionGateRequired(existingSchedule.frictionWordCount) {
                            Schedules.delete(existingSchedule.id, context)
                            onSaveComplete()
                        }
                    } else {
                        Schedules.delete(existingSchedule.id, context)
                        showDeleteDialog = false
                        onSaveComplete()
                    }
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = SoftRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ── Reusable Components ──

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun TimeBox(value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotionConditionPicker(
    selected: MotionCondition?,
    onSelected: (MotionCondition?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.activity?.label ?: "Any motion"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Motion",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Any motion") },
                    onClick = {
                        onSelected(null)
                        expanded = false
                    }
                )
                MotionCondition.Activity.entries.forEach { activity ->
                    DropdownMenuItem(
                        text = { Text(activity.label) },
                        onClick = {
                            onSelected(MotionCondition(activity))
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiConditionEditor(
    savedNetworks: List<SavedWifiNetwork>,
    selectedLabel: String,
    onNetworkSelected: (SavedWifiNetwork) -> Unit,
    ssid: String,
    onSsidChange: (String) -> Unit,
    currentWifi: WifiContextProvider.CurrentWifi?,
    onUseCurrentWifi: () -> Unit,
    isValid: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                leadingIcon = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                savedNetworks.forEach { network ->
                    DropdownMenuItem(
                        text = { Text(network.label) },
                        onClick = {
                            onNetworkSelected(network)
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChange,
            label = { Text("Wi-Fi name") },
            singleLine = true,
            isError = ssid.isBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        if (currentWifi != null) {
            OutlinedButton(
                onClick = onUseCurrentWifi,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use current Wi-Fi: ${currentWifi.ssid}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (!isValid) {
            Text(
                "Enter a Wi-Fi network name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute))
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AppPickerDialog(
    alreadySelected: Set<String>,
    onAppsSelected: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<ApplicationInfo>?>(null) }
    var labelCache by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(alreadySelected) {
        val (apps, labels) = withContext(Dispatchers.IO) {
            val allApps = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val hasLauncher = context.packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
                    val isNotSelf = appInfo.packageName != context.packageName
                    (!isSystem || hasLauncher) && isNotSelf && !alreadySelected.contains(appInfo.packageName)
                }
            val cache = allApps.associate { it.packageName to context.packageManager.getApplicationLabel(it).toString() }
            val sorted = allApps.sortedBy { cache[it.packageName]?.lowercase() }
            sorted to cache
        }
        installedApps = apps
        labelCache = labels
    }

    var searchQuery by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    val filteredApps = installedApps?.let { apps ->
        if (searchQuery.isBlank()) apps
        else apps.filter {
            val label = labelCache[it.packageName] ?: it.packageName
            label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_apps), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                )
                Spacer(Modifier.height(8.dp))
                if (filteredApps == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = IndigoPrimary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { appInfo ->
                            val label = labelCache[appInfo.packageName] ?: appInfo.packageName
                            val isChecked = selected.contains(appInfo.packageName)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (isChecked) selected.remove(appInfo.packageName)
                                        else selected.add(appInfo.packageName)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = IndigoPrimary
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        appInfo.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAppsSelected(selected.toList()) },
                enabled = selected.isNotEmpty()
            ) {
                Text(stringResource(R.string.add_selected, selected.size), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
