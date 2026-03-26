package net.kollnig.reddblockandroid.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.format.TextStyle
import java.util.Locale
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
        onCreateSchedule: () -> Unit,
        onEditSchedule: (Schedule) -> Unit,
        onFrictionGateRequired: (Schedule, () -> Unit) -> Unit,
        onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var schedules by remember { mutableStateOf(Schedules.getAll()) }
    var refreshTick by remember { mutableIntStateOf(0) }

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
                            Text(stringResource(R.string.schedules), fontWeight = FontWeight.Bold)
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.back)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onCreateSchedule,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.create_schedule)
                    )
                }
            }
    ) { innerPadding ->
        if (schedules.isEmpty()) {
            Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
            ) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                            Icons.Rounded.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                            stringResource(R.string.no_schedules),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            stringResource(R.string.no_schedules_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section header
                item {
                    Text(
                        stringResource(R.string.your_blocklists),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(schedules, key = { it.id }) { schedule ->
                    @Suppress("UNUSED_EXPRESSION") refreshTick
                    val isActive = Schedules.isScheduleActive(schedule.id)
                    ScheduleItem(
                            schedule = schedule,
                            isActive = isActive,
                            onClick = {
                                if (isActive) {
                                    onFrictionGateRequired(schedule) { onEditSchedule(schedule) }
                                } else {
                                    onEditSchedule(schedule)
                                }
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
                                if (isActive) {
                                    onFrictionGateRequired(schedule) { onEditSchedule(schedule) }
                                } else {
                                    onEditSchedule(schedule)
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleItem(
        schedule: Schedule,
        isActive: Boolean,
        onClick: () -> Unit,
        onToggle: () -> Unit,
        onEdit: () -> Unit
) {
    val containerColor by
            animateColorAsState(
                    targetValue =
                            if (isActive) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surface,
                    label = "containerColor"
            )

    val borderColor = if (isActive) IndigoPrimary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant

    Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = if (isActive) BorderStroke(1.5.dp, IndigoPrimary.copy(alpha = 0.4f)) else null
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                    Text(
                        "🚫",
                        fontSize = 18.sp
                    )
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

            // Edit button
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
                            stringResource(
                                    R.string.daily_time_range,
                                    start.toString(),
                                    end.toString()
                            )
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
