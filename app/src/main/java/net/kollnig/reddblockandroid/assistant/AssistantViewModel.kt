package net.kollnig.reddblockandroid.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssistantViewModel(context: Context) {
    private val appContext = context.applicationContext
    val preferences = AssistantPreferences(appContext)
    private val contextProvider = ContextProvider(appContext, preferences)
    private val openAIClient = OpenAIClient()
    private val activityRecognitionManager = ActivityRecognitionManager(appContext)
    private val wifiContextProvider = WifiContextProvider(appContext)

    var apiKeyDraft: String = preferences.getApiKey().orEmpty()
    var modelDraft: String = preferences.getModel()
    var goalsDraft: String = preferences.getGoals()
    var usageSharingEnabled: Boolean = preferences.isUsageSharingEnabled()
    var motionSharingEnabled: Boolean = preferences.isMotionSharingEnabled()
    var wifiSharingEnabled: Boolean = preferences.isWifiSharingEnabled()

    fun hasApiKey(): Boolean = preferences.getApiKey()?.isNotBlank() == true

    fun saveSettings() {
        val key = apiKeyDraft.trim()
        val model = modelDraft.trim().ifBlank { OpenAIModels.DEFAULT_MODEL }
        if (key.isNotBlank()) {
            preferences.saveApiKey(key)
        }
        preferences.saveModel(model)
        preferences.saveGoals(goalsDraft.trim())
        preferences.setUsageSharingEnabled(usageSharingEnabled)
        preferences.setMotionSharingEnabled(motionSharingEnabled)
        preferences.setWifiSharingEnabled(wifiSharingEnabled)
        if (motionSharingEnabled) {
            activityRecognitionManager.startUpdates()
        } else {
            activityRecognitionManager.stopUpdates()
        }
    }

    fun clearApiKey() {
        apiKeyDraft = ""
        preferences.clearApiKey()
    }

    fun hasUsageStatsPermission(): Boolean = contextProvider.hasUsageStatsPermission()

    fun usageSettingsIntent() = contextProvider.usageSettingsIntent()

    fun hasMotionPermission(): Boolean = activityRecognitionManager.hasPermission()

    fun startMotionUpdates() {
        activityRecognitionManager.startUpdates()
    }

    fun hasWifiPermission(): Boolean = wifiContextProvider.hasPermission()

    fun wifiRuntimePermission(): String = wifiContextProvider.runtimePermission()

    suspend fun sendMessage(userMessage: String): AssistantResult {
        val key = preferences.getApiKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Add an OpenAI API key first.")
        val model = preferences.getModel()
        return withContext(Dispatchers.IO) {
            val installedApps = contextProvider.getInstalledApps()
            val prompt = contextProvider.buildPrompt(userMessage)
            openAIClient.requestAssistantTurn(key, model, prompt, installedApps)
        }
    }
}
