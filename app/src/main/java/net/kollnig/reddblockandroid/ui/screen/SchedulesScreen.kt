package net.kollnig.reddblockandroid.ui.screen

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.format.TextStyle
import java.util.Locale
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.ScheduleTiming
import net.kollnig.reddblockandroid.schedule.Schedules

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
    val activeSessions = remember { mutableStateOf(Schedules.getActiveSessions()) }

    fun refreshSchedules() {
        // Auto-reenable any schedules whose disabledUntil has expired
        val now = System.currentTimeMillis()
        for (schedule in Schedules.getAll()) {
            val until = schedule.disabledUntil
            if (!schedule.isEnabled && until != null && until <= now) {
                Schedules.reEnableSchedule(context, schedule.id)
            }
        }
        schedules = Schedules.getAll()
        activeSessions.value = Schedules.getActiveSessions()
    }

    // Refresh on every resume so back-navigation always shows fresh state
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
                        }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onCreateSchedule) {
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                            stringResource(R.string.no_schedules),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            stringResource(R.string.no_schedules_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    val isActive = activeSessions.value.any { it.scheduleId == schedule.id }
                    ScheduleItem(
                            schedule = schedule,
                            isActive = isActive,
                            onClick = {
                                if (isActive) {
                                    // Friction gate needed before editing active schedule
                                    onFrictionGateRequired(schedule) { onEditSchedule(schedule) }
                                } else {
                                    onEditSchedule(schedule)
                                }
                            },
                            onToggle = {
                                if (isActive) {
                                    // Friction gate needed before disabling active schedule
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
        }
    }
}

@Composable
private fun ScheduleItem(
        schedule: Schedule,
        isActive: Boolean,
        onClick: () -> Unit,
        onToggle: () -> Unit
) {
    val containerColor by
            animateColorAsState(
                    targetValue =
                            if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    label = "containerColor"
            )

    Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color =
                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                            if (isActive) Icons.Rounded.PlayArrow else Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint =
                                    if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                        schedule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color =
                                if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                )
                Text(
                        buildScheduleDescription(schedule),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color =
                                if (isActive)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                alpha = 0.7f
                                        )
                                else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(checked = schedule.isEnabled, onCheckedChange = { onToggle() })
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
        parts.add(stringResource(R.string.blocked_items_count, blockCount))
    }

    return parts.joinToString(" • ")
}
