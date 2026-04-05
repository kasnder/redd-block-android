package net.kollnig.reddblockandroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.theme.ReDDBlockAndroidTheme

/**
 * Standalone activity launched by BlockerService when an app or website is blocked.
 * Shows the friction gate; on completion, temporarily disables the
 * schedule that caused the block.
 *
 * If [EXTRA_BLOCKED_PACKAGE] is set the blocked app is relaunched
 * after the gate is passed so the user lands back where they were.
 * If [EXTRA_BLOCKED_DOMAIN] is set the blocked website is opened
 * in the default browser after the gate is passed.
 */
class UnlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_BLOCKED_DOMAIN = "blocked_domain"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        val blockedDomain = intent.getStringExtra(EXTRA_BLOCKED_DOMAIN)
        val schedule = scheduleId?.let { Schedules.get(it) }

        if (schedule == null || !schedule.isEnabled) {
            finish()
            return
        }

        // Resolve a human-readable label for the return target
        val returnTargetLabel = when {
            blockedPackage != null -> getAppLabel(blockedPackage)
            blockedDomain != null -> blockedDomain
            else -> null
        }

        val unlockDurationText = formatUnlockDuration(schedule.autoReenableMinutes)

        setContent {
            ReDDBlockAndroidTheme {
                FrictionGateScreen(
                    wordCount = schedule.frictionWordCount,
                    returnTargetLabel = returnTargetLabel,
                    unlockDurationText = unlockDurationText,
                    onPassed = {
                        // Just unlock, don't return to blocked content
                        Schedules.toggle(schedule.id, this@UnlockActivity)
                        finish()
                    },
                    onPassedAndReturn = {
                        // Unlock and return to the blocked app/website
                        Schedules.toggle(schedule.id, this@UnlockActivity)
                        relaunchBlockedApp(blockedPackage)
                        openBlockedWebsite(blockedDomain)
                        finish()
                    },
                    onBackPressed = {
                        finish()
                    }
                )
            }
        }
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun formatUnlockDuration(autoReenableMinutes: Int): String? {
        if (autoReenableMinutes <= 0) return null
        val hours = autoReenableMinutes / 60
        val minutes = autoReenableMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> getString(R.string.duration_hours_minutes, hours, minutes)
            hours > 0 -> resources.getQuantityString(R.plurals.duration_hours, hours, hours)
            else -> resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
        }
    }

    private fun relaunchBlockedApp(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (_: Exception) {
            // Package not found or uninstalled — silently ignore
        }
    }

    private fun openBlockedWebsite(domain: String?) {
        if (domain.isNullOrBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$domain")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        } catch (_: Exception) {
            // Malformed domain or no browser available — silently ignore
        }
    }
}
