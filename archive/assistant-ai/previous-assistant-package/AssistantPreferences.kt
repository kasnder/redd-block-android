package net.kollnig.reddblockandroid.assistant

import android.content.Context

class AssistantPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    fun saveModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
    }

    fun getModel(): String {
        val savedModel = prefs.getString(KEY_MODEL, null)
        return if (savedModel.isNullOrBlank() || savedModel in LEGACY_DEFAULT_MODELS) {
            AssistantAiModels.DEFAULT_MODEL
        } else {
            AssistantAiModels.normalize(savedModel)
        }
    }

    fun saveGoals(goals: String) {
        prefs.edit().putString(KEY_GOALS, goals).apply()
    }

    fun getGoals(): String = prefs.getString(KEY_GOALS, "") ?: ""

    fun setUsageSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USAGE_SHARING, enabled).apply()
    }

    fun isUsageSharingEnabled(): Boolean = prefs.getBoolean(KEY_USAGE_SHARING, false)

    fun setMotionSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOTION_SHARING, enabled).apply()
    }

    fun isMotionSharingEnabled(): Boolean = prefs.getBoolean(KEY_MOTION_SHARING, false)

    fun setWifiSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_SHARING, enabled).apply()
    }

    fun isWifiSharingEnabled(): Boolean = prefs.getBoolean(KEY_WIFI_SHARING, false)

    companion object {
        private const val PREFS_NAME = "assistant_prefs"
        private const val KEY_API_KEY = "nebius_api_key"
        private const val KEY_MODEL = "assistant_model"
        private val LEGACY_DEFAULT_MODELS = setOf(
            "gpt-5.5",
            "bedrock/kimi-k2.5@eu-north-1"
        )
        private const val KEY_GOALS = "goals"
        private const val KEY_USAGE_SHARING = "usage_sharing"
        private const val KEY_MOTION_SHARING = "motion_sharing"
        private const val KEY_WIFI_SHARING = "wifi_sharing"
    }
}
