package net.kollnig.reddblockandroid.ui.screen

import androidx.compose.foundation.background
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
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.theme.*
import net.kollnig.reddblockandroid.util.hasNotificationPermission
import net.kollnig.reddblockandroid.util.isAccessibilityServiceEnabled
import net.kollnig.reddblockandroid.util.isBatteryOptimizationDisabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSchedules: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(context.isAccessibilityServiceEnabled()) }
    var hasNotifications by remember { mutableStateOf(context.hasNotificationPermission()) }
    var hasBatteryOpt by remember { mutableStateOf(context.isBatteryOptimizationDisabled()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = context.isAccessibilityServiceEnabled()
                hasNotifications = context.hasNotificationPermission()
                hasBatteryOpt = context.isBatteryOptimizationDisabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allPermissionsGranted = isAccessibilityEnabled && hasNotifications && hasBatteryOpt
    val activeSessionCount = remember { Schedules.getActiveSessions().size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
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
                    onClick = onNavigateToPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SoftRedBg
                    )
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
                                color = TextPrimary
                            )
                            Text(
                                stringResource(R.string.setup_required_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
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
                            color = BadgeGreen.copy(alpha = 0.15f)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = BadgeGreen
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.protection_active),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                if (activeSessionCount > 0)
                                    stringResource(R.string.schedules_active_count, activeSessionCount)
                                else
                                    stringResource(R.string.no_active_schedules),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
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
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onNavigateToSchedules,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.create_schedule),
                        modifier = Modifier.size(20.dp),
                        tint = TextSecondary
                    )
                }
            }

            // ── Schedules card ──
            Card(
                onClick = onNavigateToSchedules,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                        color = IndigoPrimary.copy(alpha = 0.1f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Block,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = IndigoPrimary
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.schedules),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.schedules_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = CoolGrey
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Permissions card — hidden when all permissions are granted ──
            if (!allPermissionsGranted) {
                Card(
                    onClick = onNavigateToPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
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
                            color = SlateBlue.copy(alpha = 0.1f)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = SlateBlue
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.permissions),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                stringResource(R.string.permissions_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = CoolGrey
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ── Footer ──
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.footer_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHint
                )
            }
        }
    }
}
