package net.kollnig.reddblockandroid.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.util.hasNotificationPermission
import net.kollnig.reddblockandroid.util.isAccessibilityServiceEnabled
import net.kollnig.reddblockandroid.util.isBatteryOptimizationDisabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var accessibilityEnabled by remember { mutableStateOf(context.isAccessibilityServiceEnabled()) }
    var notificationEnabled by remember { mutableStateOf(context.hasNotificationPermission()) }
    var batteryOptDisabled by remember { mutableStateOf(context.isBatteryOptimizationDisabled()) }

    // Refresh permissions when returning from settings (on every resume)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = context.isAccessibilityServiceEnabled()
                notificationEnabled = context.hasNotificationPermission()
                batteryOptDisabled = context.isBatteryOptimizationDisabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.permissions),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.permissions_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            // Accessibility Service (required)
            PermissionCard(
                title = stringResource(R.string.accessibility_service),
                description = stringResource(R.string.accessibility_service_desc),
                isGranted = accessibilityEnabled,
                isRequired = true,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            // Notifications
            PermissionCard(
                title = stringResource(R.string.notifications),
                description = stringResource(R.string.notifications_desc),
                isGranted = notificationEnabled,
                isRequired = false,
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )

            // Battery Optimization
            PermissionCard(
                title = stringResource(R.string.battery_optimization),
                description = stringResource(R.string.battery_optimization_desc),
                isGranted = batteryOptDisabled,
                isRequired = false,
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else if (isRequired)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceContainer
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
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (isGranted)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else if (isRequired)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isGranted) Icons.Rounded.CheckCircle
                        else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isGranted)
                            MaterialTheme.colorScheme.primary
                        else if (isRequired)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isRequired && !isGranted) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                stringResource(R.string.required),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
