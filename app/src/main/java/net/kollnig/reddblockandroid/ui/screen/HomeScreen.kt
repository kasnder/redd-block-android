package net.kollnig.reddblockandroid.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.theme.BadgeGreen
import net.kollnig.reddblockandroid.ui.theme.FocusSpaceAccentPalette
import net.kollnig.reddblockandroid.ui.theme.SoftRed
import net.kollnig.reddblockandroid.util.isAccessibilityServiceEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateSchedule: () -> Unit,
    onEditSchedule: (Schedule) -> Unit,
    onFrictionGateRequired: (Schedule, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var isAccessibilityEnabled by remember { mutableStateOf(context.isAccessibilityServiceEnabled()) }
    var schedules by remember { mutableStateOf(Schedules.getAll()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val exportSuccessMsg = stringResource(R.string.export_success)
    val exportErrorMsg = stringResource(R.string.export_error)
    val importSuccessMsg = stringResource(R.string.import_success)
    val importErrorMsg = stringResource(R.string.import_error)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val json = Schedules.exportSchedules()
                context.contentResolver.openOutputStream(it)?.use { out -> out.write(json.toByteArray()) }
                coroutineScope.launch { snackbarHostState.showSnackbar(exportSuccessMsg) }
            } catch (_: Exception) {
                coroutineScope.launch { snackbarHostState.showSnackbar(exportErrorMsg) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val json = context.contentResolver.openInputStream(it)?.use { input ->
                    input.bufferedReader().readText()
                }
                if (json != null) {
                    val importedCount = Schedules.importSchedules(json, context)
                    if (importedCount > 0) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(String.format(importSuccessMsg, importedCount))
                        }
                    } else {
                        coroutineScope.launch { snackbarHostState.showSnackbar(importErrorMsg) }
                    }
                } else {
                    coroutineScope.launch { snackbarHostState.showSnackbar(importErrorMsg) }
                }
            } catch (_: Exception) {
                coroutineScope.launch { snackbarHostState.showSnackbar(importErrorMsg) }
            }
        }
    }

    fun refreshSchedules() {
        val now = System.currentTimeMillis()
        Schedules.getAll().forEach { schedule ->
            val until = schedule.disabledUntil
            if (!schedule.isEnabled && until != null && until <= now) {
                Schedules.reEnableSchedule(context, schedule.id)
            }
        }
        schedules = Schedules.getAll()
        refreshTick++
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = context.isAccessibilityServiceEnabled()
                refreshSchedules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { isMenuExpanded = true }) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_rules)) },
                                onClick = {
                                    isMenuExpanded = false
                                    exportLauncher.launch("reddblock_rules.json")
                                },
                                leadingIcon = { Icon(Icons.Rounded.Upload, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_rules)) },
                                onClick = {
                                    isMenuExpanded = false
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            if (!isAccessibilityEnabled) {
                Card(
                    onClick = { showAccessibilityDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = SoftRed
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.setup_required),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.setup_required_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onCreateSchedule,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_focus_space), fontWeight = FontWeight.Bold)
            }

            if (schedules.isEmpty()) {
                Card(
                    onClick = onCreateSchedule,
                    modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            stringResource(R.string.focus_space_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.focus_space_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                schedules.forEachIndexed { index, schedule ->
                    @Suppress("UNUSED_EXPRESSION") refreshTick
                    val isActive = Schedules.isScheduleActive(schedule.id)
                    ScheduleItem(
                        schedule = schedule,
                        isActive = isActive,
                        accent = FocusSpaceAccentPalette[index % FocusSpaceAccentPalette.size],
                        onClick = { onEditSchedule(schedule) },
                        onToggle = {
                            if (isActive) {
                                onFrictionGateRequired(schedule) {
                                    Schedules.toggle(schedule.id, context)
                                    refreshSchedules()
                                }
                            } else {
                                Schedules.toggle(schedule.id, context)
                                refreshSchedules()
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.footer_text),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(context.getString(R.string.footer_url)) }
                    .padding(top = 18.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        if (showAccessibilityDialog) {
            AccessibilityConsentDialog(
                onDismiss = { showAccessibilityDialog = false },
                onAgree = {
                    showAccessibilityDialog = false
                    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun ScheduleItem(
    schedule: Schedule,
    isActive: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val status = when {
        isActive -> stringResource(R.string.focus_space_blocking_now)
        !schedule.isEnabled && (schedule.disabledUntil ?: 0L) > System.currentTimeMillis() ->
            stringResource(R.string.focus_space_paused)
        !schedule.isEnabled -> stringResource(R.string.focus_space_off)
        else -> stringResource(R.string.focus_space_scheduled)
    }
    val timing = focusSpaceTimingSummary(schedule)
    val toggleDescription = stringResource(R.string.focus_space_toggle, schedule.name)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isActive) BorderStroke(1.dp, BadgeGreen.copy(alpha = 0.45f)) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Surface(
                modifier = Modifier.width(5.dp).fillMaxHeight(),
                color = accent
            ) {}
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        schedule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(
                            R.string.focus_space_items,
                            schedule.blockedWebsites.size,
                            schedule.blockedApps.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.focus_space_status_line, status, timing),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) BadgeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .semantics {
                            contentDescription = toggleDescription
                        },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun focusSpaceTimingSummary(schedule: Schedule): String {
    return when (schedule.timing.type) {
        ScheduleTiming.ScheduleType.MANUAL -> stringResource(R.string.focus_space_manual_summary)
        ScheduleTiming.ScheduleType.DAILY -> {
            val start = schedule.timing.time?.toString() ?: ""
            val end = schedule.timing.endTime?.toString() ?: ""
            stringResource(R.string.focus_space_daily_summary, start, end)
        }
        ScheduleTiming.ScheduleType.WEEKLY -> {
            val days = schedule.timing.daysOfWeek
                .sortedBy { it.value }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            val start = schedule.timing.time?.toString() ?: ""
            val end = schedule.timing.endTime?.toString() ?: ""
            if (days.isBlank()) {
                stringResource(R.string.focus_space_daily_summary, start, end)
            } else {
                stringResource(R.string.focus_space_weekly_summary, days, start, end)
            }
        }
    }
}
