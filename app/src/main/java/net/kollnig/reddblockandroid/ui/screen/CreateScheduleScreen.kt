package net.kollnig.reddblockandroid.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.theme.DayChipSelected
import net.kollnig.reddblockandroid.ui.theme.SoftRed
import net.kollnig.reddblockandroid.ui.theme.TextHint
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private enum class EditorSection {
    WHAT_TO_BLOCK,
    WHEN_TO_BLOCK,
    TO_STOP_EARLY
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateScheduleScreen(
    scheduleId: String?,
    onBackPressed: () -> Unit,
    onSaveComplete: () -> Unit,
    onFrictionGateRequired: (wordCount: Int, onPassed: () -> Unit) -> Unit = { _, _ -> }
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
    var blockedApps by remember { mutableStateOf(existingSchedule?.blockedApps ?: emptyList()) }
    var blockedWebsites by remember { mutableStateOf(existingSchedule?.blockedWebsites ?: emptyList()) }
    var frictionWordCount by remember { mutableIntStateOf(existingSchedule?.frictionWordCount ?: 15) }
    var autoReenableMinutes by remember { mutableIntStateOf(existingSchedule?.autoReenableMinutes ?: 1440) }

    var expandedSection by remember { mutableStateOf<EditorSection?>(EditorSection.WHAT_TO_BLOCK) }
    var inlineWebsite by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
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
    val selectedAutoReenableLabel = autoReenableOptions.firstOrNull { it.first == autoReenableMinutes }?.second
        ?: stringResource(R.string.auto_reenable_never)
    val domainPattern = remember { Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$") }

    fun cleanDomain(input: String): String {
        var domain = input.lowercase().trim()
        domain = domain.removePrefix("https://").removePrefix("http://")
        domain = domain.removePrefix("www.")
        return domain.split("/").first()
    }

    fun buildSchedule(): Schedule {
        val timing = ScheduleTiming(
            type = scheduleType,
            timeHour = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedTime.hour else null,
            timeMinute = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedTime.minute else null,
            endTimeHour = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedEndTime.hour else null,
            endTimeMinute = if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) selectedEndTime.minute else null,
            daysOfWeek = if (scheduleType == ScheduleTiming.ScheduleType.WEEKLY) selectedDays else emptySet()
        )
        return Schedule(
            id = existingSchedule?.id ?: UUID.randomUUID().toString(),
            name = scheduleName.trim(),
            isEnabled = if (existingSchedule != null) {
                if (existingSchedule.timing.type != ScheduleTiming.ScheduleType.MANUAL &&
                    scheduleType == ScheduleTiming.ScheduleType.MANUAL
                ) false else existingSchedule.isEnabled
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

    fun isStrictlyMoreRestrictive(original: Schedule, updated: Schedule): Boolean {
        if (!updated.blockedApps.containsAll(original.blockedApps)) return false
        if (!updated.blockedWebsites.containsAll(original.blockedWebsites)) return false
        if (updated.frictionWordCount < original.frictionWordCount) return false
        if (updated.timing.type != original.timing.type) return false
        if (updated.timing.timeHour != original.timing.timeHour ||
            updated.timing.timeMinute != original.timing.timeMinute ||
            updated.timing.endTimeHour != original.timing.endTimeHour ||
            updated.timing.endTimeMinute != original.timing.endTimeMinute
        ) return false
        if (updated.timing.daysOfWeek != original.timing.daysOfWeek) return false
        if (updated.autoReenableMinutes != original.autoReenableMinutes) return false
        return true
    }

    fun saveSchedule() {
        if (scheduleName.isBlank()) return
        val schedule = buildSchedule()
        if (existingSchedule != null &&
            Schedules.isScheduleActive(existingSchedule.id) &&
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
    val daysSummary = selectedDays
        .sortedBy { it.value }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    val whenSummary = when (scheduleType) {
        ScheduleTiming.ScheduleType.MANUAL -> stringResource(R.string.when_to_block_summary_manual)
        ScheduleTiming.ScheduleType.DAILY -> stringResource(
            R.string.when_to_block_summary_daily,
            selectedTime.toString(),
            selectedEndTime.toString()
        )
        ScheduleTiming.ScheduleType.WEEKLY -> stringResource(
            R.string.when_to_block_summary_weekly,
            daysSummary,
            selectedTime.toString(),
            selectedEndTime.toString()
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existingSchedule != null) stringResource(R.string.edit_focus_space)
                        else stringResource(R.string.new_focus_space),
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
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
            ) {
                Button(
                    onClick = { saveSchedule() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = scheduleName.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_changes), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EditorSectionHeader(stringResource(R.string.focus_space_name))
            OutlinedTextField(
                value = scheduleName,
                onValueChange = { scheduleName = it },
                placeholder = { Text(stringResource(R.string.focus_space_name), color = MaterialTheme.colorScheme.outline) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            EditorSummaryCard(
                title = stringResource(R.string.what_to_block),
                summary = stringResource(R.string.what_to_block_summary, blockedWebsites.size, blockedApps.size),
                expanded = expandedSection == EditorSection.WHAT_TO_BLOCK,
                onClick = {
                    expandedSection = if (expandedSection == EditorSection.WHAT_TO_BLOCK) null else EditorSection.WHAT_TO_BLOCK
                }
            ) {
                if (blockedWebsites.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        blockedWebsites.forEach { domain ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        domain,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = { blockedWebsites = blockedWebsites - domain },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.remove),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(
                        stringResource(R.string.no_items_added),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = inlineWebsite,
                    onValueChange = { inlineWebsite = it },
                    placeholder = { Text(stringResource(R.string.website_placeholder), color = MaterialTheme.colorScheme.outline) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
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
                Spacer(Modifier.height(10.dp))
                if (blockedApps.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column {
                            blockedApps.forEachIndexed { index, pkg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        appNameCache[pkg] ?: pkg,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_app), fontWeight = FontWeight.SemiBold)
                }
            }

            EditorSummaryCard(
                title = stringResource(R.string.when_to_block),
                summary = whenSummary,
                expanded = expandedSection == EditorSection.WHEN_TO_BLOCK,
                onClick = {
                    expandedSection = if (expandedSection == EditorSection.WHEN_TO_BLOCK) null else EditorSection.WHEN_TO_BLOCK
                }
            ) {
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
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (scheduleType != ScheduleTiming.ScheduleType.MANUAL) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TimeChoiceCard(
                            label = stringResource(R.string.start_time),
                            time = selectedTime,
                            modifier = Modifier.weight(1f),
                            onClick = { showStartTimePicker = true }
                        )
                        TimeChoiceCard(
                            label = stringResource(R.string.end_time),
                            time = selectedEndTime,
                            modifier = Modifier.weight(1f),
                            onClick = { showEndTimePicker = true }
                        )
                    }
                }
                if (scheduleType == ScheduleTiming.ScheduleType.WEEKLY) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.days),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            val isSelected = selectedDays.contains(day)
                            Surface(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable {
                                        selectedDays = if (isSelected) selectedDays - day else selectedDays + day
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
            }

            EditorSummaryCard(
                title = stringResource(R.string.to_stop_early),
                summary = stringResource(R.string.stop_early_summary, frictionWordCount, selectedAutoReenableLabel),
                expanded = expandedSection == EditorSection.TO_STOP_EARLY,
                onClick = {
                    expandedSection = if (expandedSection == EditorSection.TO_STOP_EARLY) null else EditorSection.TO_STOP_EARLY
                }
            ) {
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
                        frictionWordCount.toString(),
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    stringResource(R.string.friction_word_count_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.auto_reenable), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(7.dp))
                var durationMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = durationMenuExpanded,
                    onExpandedChange = { durationMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedAutoReenableLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationMenuExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = durationMenuExpanded,
                        onDismissRequest = { durationMenuExpanded = false }
                    ) {
                        autoReenableOptions.forEach { (minutes, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    autoReenableMinutes = minutes
                                    durationMenuExpanded = false
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
            Spacer(Modifier.height(8.dp))
        }
    }

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
    if (showDeleteDialog && existingSchedule != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_focus_space), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_focus_space_confirm, existingSchedule.name)) },
            confirmButton = {
                TextButton(onClick = {
                    if (Schedules.isScheduleActive(existingSchedule.id)) {
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
                    Text(stringResource(R.string.delete), color = SoftRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun EditorSummaryCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (expanded) "−" else "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun EditorSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun TimeChoiceCard(
    label: String,
    time: LocalTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                time.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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
    val state = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
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
            val cache = allApps.associate {
                it.packageName to context.packageManager.getApplicationLabel(it).toString()
            }
            allApps.sortedBy { cache[it.packageName]?.lowercase() } to cache
        }
        installedApps = apps
        labelCache = labels
    }

    var searchQuery by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val filteredApps = installedApps?.let { apps ->
        if (searchQuery.isBlank()) apps else apps.filter {
            val label = labelCache[it.packageName] ?: it.packageName
            label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
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
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
                )
                Spacer(Modifier.height(8.dp))
                if (filteredApps == null) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { appInfo ->
                            val label = labelCache[appInfo.packageName] ?: appInfo.packageName
                            val isChecked = selected.contains(appInfo.packageName)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (isChecked) selected.remove(appInfo.packageName)
                                        else selected.add(appInfo.packageName)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            TextButton(onClick = { onAppsSelected(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.add_selected, selected.size), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
