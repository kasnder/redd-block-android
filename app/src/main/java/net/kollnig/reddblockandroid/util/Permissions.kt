package net.kollnig.reddblockandroid.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

fun Context.isAccessibilityServiceEnabled(): Boolean {
    val serviceName = "$packageName/$packageName.service.BlockerService"
    val serviceNameShort = "$packageName/.service.BlockerService"
    val enabledServices = Settings.Secure.getString(
        contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    // Split on ':' delimiter to avoid substring false positives
    val services = enabledServices.split(':')
    return services.any { it.equals(serviceName, ignoreCase = true) ||
            it.equals(serviceNameShort, ignoreCase = true) }
}

fun Context.hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun Context.isBatteryOptimizationDisabled(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}
