package net.kollnig.reddblockandroid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.kollnig.reddblockandroid.schedule.ScheduleManager
import net.kollnig.reddblockandroid.util.isPrefsInitialized
import net.kollnig.reddblockandroid.util.prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val safeContext = context.createDeviceProtectedStorageContext()

        if (!isPrefsInitialized) {
            prefs = safeContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }

        Log.d("BootReceiver", "Action received: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ScheduleManager.scheduleAllSchedules(safeContext)
            }
        }
    }
}
