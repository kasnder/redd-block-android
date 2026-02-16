package net.kollnig.reddblockandroid

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.*
import net.kollnig.reddblockandroid.schedule.ScheduleManager
import net.kollnig.reddblockandroid.util.prefs
import java.util.concurrent.TimeUnit

class App : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupSafePreferences()
        scheduleWatcher(this)
    }

    private fun setupSafePreferences() {
        val deviceContext = createDeviceProtectedStorageContext()
        deviceContext.moveSharedPreferencesFrom(this, "prefs")
        prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
    }
}

fun scheduleWatcher(context: Context) {
    // Periodic work to ensure schedules stay scheduled
    val workRequest = PeriodicWorkRequestBuilder<ScheduleWatcherWorker>(
        15, TimeUnit.MINUTES,
        5, TimeUnit.MINUTES
    ).setConstraints(Constraints.NONE).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "ReDDBlockSafetyNet",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

class ScheduleWatcherWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        if (!net.kollnig.reddblockandroid.util.isPrefsInitialized) {
            net.kollnig.reddblockandroid.util.prefs =
                applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }
        ScheduleManager.scheduleAllSchedules(applicationContext)
        return Result.success()
    }
}
