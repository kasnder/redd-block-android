package net.kollnig.reddblockandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.theme.FristedAndroidTheme

/**
 * Standalone activity launched by BlockerService when an app or website is blocked.
 * Shows the friction gate; on completion, temporarily disables the
 * schedule that caused the block. When this activity finishes, Android
 * returns to the blocked app/browser automatically.
 */
class UnlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_SCHEDULE_NAME = "schedule_name"
        const val EXTRA_BLOCKED_TARGET = "blocked_target"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
        val scheduleName = intent.getStringExtra(EXTRA_SCHEDULE_NAME)
        val blockedTarget = intent.getStringExtra(EXTRA_BLOCKED_TARGET)
        val schedule = scheduleId?.let { Schedules.get(it) }

        if (schedule == null || !schedule.isEnabled) {
            finish()
            return
        }

        val unlockDurationText = formatUnlockDuration(schedule.autoReenableMinutes)

        setContent {
            FristedAndroidTheme {
                FrictionGateScreen(
                    wordCount = schedule.frictionWordCount,
                    unlockDurationText = unlockDurationText,
                    scheduleName = scheduleName ?: schedule.name,
                    blockedTargetLabel = blockedTarget,
                    isBlockMode = true,
                    onPassed = {
                        Schedules.temporaryUnlock(this@UnlockActivity, schedule.id)
                        finish()
                    },
                    onBackPressed = {
                        // In block mode, go home instead of back to the blocked app
                        moveTaskToBack(true)
                    }
                )
            }
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

}
