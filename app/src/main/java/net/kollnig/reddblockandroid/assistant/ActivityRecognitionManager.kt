package net.kollnig.reddblockandroid.assistant

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import org.json.JSONObject

class ActivityRecognitionManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun startUpdates(onComplete: (Boolean) -> Unit = {}) {
        if (!hasPermission()) {
            onComplete(false)
            return
        }
        ActivityRecognition.getClient(appContext)
            .requestActivityUpdates(DETECTION_INTERVAL_MS, pendingIntent())
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun stopUpdates(onComplete: (Boolean) -> Unit = {}) {
        ActivityRecognition.getClient(appContext)
            .removeActivityUpdates(pendingIntent())
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun saveLatestActivity(activity: String, confidence: Int) {
        prefs.edit()
            .putString(KEY_LATEST_ACTIVITY, activity)
            .putInt(KEY_LATEST_CONFIDENCE, confidence)
            .putLong(KEY_LATEST_AT, System.currentTimeMillis())
            .apply()
    }

    fun latestActivityJson(): JSONObject? {
        val activity = prefs.getString(KEY_LATEST_ACTIVITY, null) ?: return null
        val detectedAt = prefs.getLong(KEY_LATEST_AT, 0L)
        if (detectedAt <= 0L || System.currentTimeMillis() - detectedAt > STALE_AFTER_MS) {
            return null
        }
        return JSONObject()
            .put("activity", activity)
            .put("confidence", prefs.getInt(KEY_LATEST_CONFIDENCE, 0))
            .put("detectedAt", detectedAt)
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(appContext, ActivityRecognitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        private const val PREFS_NAME = "assistant_motion_prefs"
        private const val KEY_LATEST_ACTIVITY = "latest_activity"
        private const val KEY_LATEST_CONFIDENCE = "latest_confidence"
        private const val KEY_LATEST_AT = "latest_at"
        private const val REQUEST_CODE = 4207
        private const val DETECTION_INTERVAL_MS = 60_000L
        private const val STALE_AFTER_MS = 30 * 60_000L
    }
}

