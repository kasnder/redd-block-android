package net.kollnig.reddblockandroid.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.schedule.ScheduleManager
import net.kollnig.reddblockandroid.schedule.Schedules
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
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
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
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
            // Name
            OutlinedTextField(
                value = scheduleName,
                onValueChange = { scheduleName = it },
                label = { Text(stringResource(R.string.schedule_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Schedule Type
            Text(
                stringResource(R.string.schedule_type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

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

            // Time pickers (not for manual)
            if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedCard(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.start_time),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                selectedTime.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.end_time),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                selectedEndTime.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Day selector (weekly only)
            if (scheduleType == ScheduleTiming.ScheduleType.WEEKLY) {
                Text(
                    stringResource(R.string.days),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DayOfWeek.entries.forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedDays = if (isSelected) selectedDays - day
                                else selectedDays + day
                            },
                            label = {
                                Text(
                                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.size(width = 42.dp, height = 36.dp)
                        )
                    }
                }
            }

            // Friction Settings
            Text(
                stringResource(R.string.friction_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Friction word count
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.friction_word_count),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "$frictionWordCount",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = frictionWordCount.toFloat(),
                        onValueChange = { frictionWordCount = it.toInt() },
                        valueRange = 1f..50f,
                        steps = 48,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.friction_word_count_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Auto re-enable
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.auto_reenable),
                        style = MaterialTheme.typography.bodyMedium
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
                            shape = RoundedCornerShape(12.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Blocked Apps
            Text(
                stringResource(R.string.blocked_apps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Cache app name lookups to avoid IPC on every recomposition
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

            blockedApps.forEach { pkg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            appNameCache[pkg] ?: pkg,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { blockedApps = blockedApps - pkg },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.remove),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_app))
            }

            // Blocked Websites
            Text(
                stringResource(R.string.blocked_websites),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            blockedWebsites.forEach { domain ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            domain,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { blockedWebsites = blockedWebsites - domain },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.remove),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showWebsiteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_website))
            }

            // Save button
            Button(
                onClick = { saveSchedule() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = scheduleName.isNotBlank()
            ) {
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

    // Website dialog
    if (showWebsiteDialog) {
        WebsiteInputDialog(
            onAdd = { domain ->
                if (domain.isNotBlank()) {
                    blockedWebsites = (blockedWebsites + domain.lowercase().trim()).distinct()
                }
                showWebsiteDialog = false
            },
            onDismiss = { showWebsiteDialog = false }
        )
    }

    // Delete dialog
    if (showDeleteDialog && existingSchedule != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_schedule)) },
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
                        color = MaterialTheme.colorScheme.error
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
    // Cache labels to avoid repeated IPC calls
    var labelCache by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Load apps asynchronously on a background thread
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
        title = { Text(stringResource(R.string.select_apps)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                )
                Spacer(Modifier.height(8.dp))
                if (filteredApps == null) {
                    // Show loading spinner while apps load on background thread
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
                                    }
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(stringResource(R.string.add_selected, selected.size))
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
private fun WebsiteInputDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_website)) },
        text = {
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                placeholder = { Text(stringResource(R.string.website_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (domain.isNotBlank()) onAdd(domain)
                })
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(domain) },
                enabled = domain.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
