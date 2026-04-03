package net.kollnig.reddblockandroid.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.theme.*
import net.kollnig.reddblockandroid.util.isAccessibilityServiceEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateSchedule: () -> Unit,
    onEditSchedule: (Schedule) -> Unit,
    onFrictionGateRequired: (Schedule, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(context.isAccessibilityServiceEnabled()) }

    var schedules by remember { mutableStateOf(Schedules.getAll()) }
    // Incremented to force recomposition after schedule state changes
    var refreshTick by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

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
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(json.toByteArray())
                }
                coroutineScope.launch { snackbarHostState.showSnackbar(exportSuccessMsg) }
            } catch (e: Exception) {
                coroutineScope.launch { snackbarHostState.showSnackbar(exportErrorMsg) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val json = context.contentResolver.openInputStream(it)?.use { inStream ->
                    inStream.bufferedReader().use { reader -> reader.readText() }
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
            } catch (e: Exception) {
                coroutineScope.launch { snackbarHostState.showSnackbar(importErrorMsg) }
            }
        }
    }

    fun refreshSchedules() {
        val now = System.currentTimeMillis()
        for (schedule in Schedules.getAll()) {
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Status card ──
            if (!isAccessibilityEnabled) {
                Card(
                    onClick = { showAccessibilityDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = SoftRed.copy(alpha = 0.15f)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = SoftRed
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.setup_required),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
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
                Spacer(Modifier.height(16.dp))
            }

            // ── YOUR BLOCKLISTS section header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.your_blocklists),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onCreateSchedule,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.create_schedule),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Schedule list (inline) ──
            if (schedules.isEmpty()) {
                Card(
                    onClick = onCreateSchedule,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            stringResource(R.string.no_schedules),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.no_schedules_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                schedules.forEach { schedule ->
                    // refreshTick is read here to trigger recomposition
                    @Suppress("UNUSED_EXPRESSION") refreshTick
                    val isActive = Schedules.isScheduleActive(schedule.id)
                    ScheduleItem(
                        schedule = schedule,
                        isActive = isActive,
                        onClick = {
                            // Always allow editing — strictness is checked at save time
                            onEditSchedule(schedule)
                        },
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
                        },
                        onEdit = {
                            // Always allow editing — strictness is checked at save time
                            onEditSchedule(schedule)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Footer ──
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.size(24.dp)) // To keep text centered (same width as icon)
                Text(
                    stringResource(R.string.footer_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Box {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { isMenuExpanded = true },
                        tint = MaterialTheme.colorScheme.outline
                    )
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
                            leadingIcon = {
                                Icon(Icons.Rounded.Upload, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_rules)) },
                            onClick = {
                                isMenuExpanded = false
                                importLauncher.launch(arrayOf("application/json"))
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                            }
                        )
                    }
                }
            }
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

// ── Schedule item card ──

@Composable
private fun ScheduleItem(
    schedule: Schedule,
    isActive: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isActive) BorderStroke(1.5.dp, IndigoPrimary.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Emoji/icon circle
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (isActive) IndigoPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("🚫", fontSize = 18.sp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        schedule.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BadgeGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                stringResource(R.string.always_badge),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BadgeGreen,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Text(
                    buildScheduleDescription(schedule),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete / Edit icons
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.edit_schedule),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            Switch(
                checked = schedule.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = IndigoPrimary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun buildScheduleDescription(schedule: Schedule): String {
    val parts = mutableListOf<String>()

    when (schedule.timing.type) {
        ScheduleTiming.ScheduleType.MANUAL -> parts.add(stringResource(R.string.manual))
        ScheduleTiming.ScheduleType.DAILY -> {
            schedule.timing.time?.let { start ->
                schedule.timing.endTime?.let { end ->
                    parts.add(
                        stringResource(R.string.daily_time_range, start.toString(), end.toString())
                    )
                }
            }
        }
        ScheduleTiming.ScheduleType.WEEKLY -> {
            if (schedule.timing.daysOfWeek.isNotEmpty()) {
                val dayNames =
                    schedule.timing.daysOfWeek.sortedBy { it.value }.joinToString(", ") {
                        it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    }
                parts.add(dayNames)
            }
        }
    }

    val blockCount = schedule.blockedApps.size + schedule.blockedWebsites.size
    if (blockCount > 0) {
        if (schedule.blockedWebsites.isNotEmpty()) {
            val websiteList = schedule.blockedWebsites.take(3).joinToString(", ")
            parts.add("$blockCount blocked ($websiteList)")
        } else {
            parts.add(stringResource(R.string.blocked_items_count, blockCount))
        }
    }

    return parts.joinToString(" • ")
}
