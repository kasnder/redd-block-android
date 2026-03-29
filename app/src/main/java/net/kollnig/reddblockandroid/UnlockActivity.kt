package net.kollnig.reddblockandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.theme.ReDDBlockAndroidTheme

/**
 * Standalone activity launched from blocked-notification actions.
 * Shows the friction gate; on completion, temporarily disables the
 * schedule that caused the block.
 */
class UnlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
        val schedule = scheduleId?.let { Schedules.get(it) }

        if (schedule == null || !schedule.isEnabled) {
            finish()
            return
        }

        setContent {
            ReDDBlockAndroidTheme {
                FrictionGateScreen(
                    wordCount = schedule.frictionWordCount,
                    onPassed = {
                        Schedules.toggle(schedule.id, this@UnlockActivity)
                        finish()
                    },
                    onBackPressed = {
                        finish()
                    }
                )
            }
        }
    }
}
