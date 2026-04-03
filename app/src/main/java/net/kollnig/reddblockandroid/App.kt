package net.kollnig.reddblockandroid

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.util.prefs

class App : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupSafePreferences()
        Schedules.createDefaults(this)
    }

    private fun setupSafePreferences() {
        val deviceContext = createDeviceProtectedStorageContext()
        deviceContext.moveSharedPreferencesFrom(this, "prefs")
        prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
    }
}
