package net.kollnig.reddblockandroid.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val activity = result.mostProbableActivity ?: return
        if (activity.confidence < MIN_CONFIDENCE) return

        ActivityRecognitionManager(context).saveLatestActivity(
            activity = activity.toAssistantName(),
            confidence = activity.confidence
        )
    }

    private fun DetectedActivity.toAssistantName(): String = when (type) {
        DetectedActivity.IN_VEHICLE -> "in_vehicle"
        DetectedActivity.ON_BICYCLE -> "on_bicycle"
        DetectedActivity.ON_FOOT -> "on_foot"
        DetectedActivity.RUNNING -> "running"
        DetectedActivity.STILL -> "still"
        DetectedActivity.TILTING -> "tilting"
        DetectedActivity.WALKING -> "walking"
        else -> "unknown"
    }

    companion object {
        private const val MIN_CONFIDENCE = 50
    }
}

