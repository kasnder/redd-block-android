package net.kollnig.reddblockandroid.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.schedule.ScheduleManager
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
    onBackPressed: () -> Unit,
    onSaveComplete: () -> Unit
) {
    val context = LocalContext.current
    val existingSchedule = scheduleId?.let { Schedules.get(it) }

    var scheduleName by remember { mutableStateOf(existingSchedule?.name ?: "") }
    var scheduleType by remember {
        mutableStateOf(existingSchedule?.timing?.type ?: ScheduleTiming.ScheduleType.WEEKLY)
    }
    var selectedTime by remember {
        mutableStateOf(existingSchedule?.timing?.time ?: LocalTime.of(9, 0))
    }
    var selectedEndTime by remember {
        mutableStateOf(existingSchedule?.timing?.endTime ?: LocalTime.of(17, 0))
    }
    var selectedDays by remember {
        mutableStateOf(existingSchedule?.timing?.daysOfWeek ?: emptySet())
    }
    var blockedApps by remember {
        mutableStateOf(existingSchedule?.blockedApps ?: emptyList())
    }
    var blockedWebsites by remember {
        mutableStateOf(existingSchedule?.blockedWebsites ?: emptyList())
    }
    var frictionWordCount by remember {
        mutableIntStateOf(existingSchedule?.frictionWordCount ?: 15)
    }
    var autoReenableMinutes by remember {
        mutableIntStateOf(existingSchedule?.autoReenableMinutes ?: 1440)
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var showWebsiteDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

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

    fun saveSchedule() {
        if (scheduleName.isBlank()) return

        val timing = ScheduleTiming(
            type = scheduleType,
            timeHour = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedTime.hour else null,
            timeMinute = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedTime.minute else null,
            endTimeHour = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedEndTime.hour else null,
            endTimeMinute = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedEndTime.minute else null,
            daysOfWeek = if (scheduleType == ScheduleTiming.ScheduleType.WEEKLY) selectedDays else emptySet()
        )

        val schedule = Schedule(
            id = existingSchedule?.id ?: UUID.randomUUID().toString(),
            name = scheduleName.trim(),
            isEnabled = existingSchedule?.isEnabled ?: true,
            timing = timing,
            blockedApps = blockedApps,
            blockedWebsites = blockedWebsites,
            frictionWordCount = frictionWordCount,
            autoReenableMinutes = autoReenableMinutes
        )

        Schedules.save(schedule, context)

        if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) {
            ScheduleManager.scheduleTimedSchedule(context, schedule)
        }

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
                placeholder = { Text(stringResource(R.string.schedule_name), color = TextHint) },
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
                                    color = ChipGreen,
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
                                            color = ChipGreenText
                                        )
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.remove),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { blockedWebsites = blockedWebsites - domain },
                                            tint = ChipGreenText
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Add website inline
                    var inlineWebsite by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = inlineWebsite,
                        onValueChange = { inlineWebsite = it },
                        placeholder = { Text(stringResource(R.string.website_placeholder), color = TextHint) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (inlineWebsite.isNotBlank()) {
                                blockedWebsites = (blockedWebsites + inlineWebsite.lowercase().trim()).distinct()
                                inlineWebsite = ""
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            unfocusedContainerColor = SurfaceLight,
                            focusedContainerColor = SurfaceLight
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
                                    color = TextPrimary
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
                    containerColor = DarkNavy,
                    contentColor = White
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
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TimeBox(String.format("%02d", selectedTime.hour))
                                Text(":", fontWeight = FontWeight.Bold, color = TextSecondary)
                                TimeBox(String.format("%02d", selectedTime.minute))
                            }
                        }
                    }

                    // Arrow
                    Box(
                        modifier = Modifier.align(Alignment.CenterVertically).padding(top = 16.dp)
                    ) {
                        Text("→", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TimeBox(String.format("%02d", selectedEndTime.hour))
                                Text(":", fontWeight = FontWeight.Bold, color = TextSecondary)
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
                            color = if (isSelected) DayChipSelected else DayChipUnselected
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) White else TextSecondary
                                )
                            }
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
                            color = TextPrimary
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
                        color = TextHint
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
                        color = TextPrimary
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
                                unfocusedContainerColor = SurfaceLight,
                                focusedContainerColor = SurfaceLight
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
                        color = TextHint
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
                    containerColor = DarkNavy,
                    contentColor = White
                ),
                enabled = scheduleName.isNotBlank()
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
                    Schedules.delete(existingSchedule.id, context)
                    ScheduleManager.cancelSchedule(context, existingSchedule.id)
                    showDeleteDialog = false
                    onSaveComplete()
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
        color = TextSecondary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun TimeBox(value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CoolGrey
    ) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
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
                                        color = TextHint,
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
