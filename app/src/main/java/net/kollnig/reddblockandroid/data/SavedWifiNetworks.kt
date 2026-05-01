package net.kollnig.reddblockandroid.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class SavedWifiNetwork(
    val label: String,
    val ssid: String?
) {
    fun isConfigured(): Boolean = !ssid.isNullOrBlank()
}

class SavedWifiNetworksStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getNetworks(): List<SavedWifiNetwork> {
        val json = prefs.getString(KEY_NETWORKS, null) ?: return DEFAULT_NETWORKS
        return try {
            val arr = JSONArray(json)
            val parsed = (0 until arr.length()).map { index ->
                val obj = arr.getJSONObject(index)
                SavedWifiNetwork(
                    label = obj.getString("label"),
                    ssid = obj.optString("ssid").takeIf { it.isNotBlank() }
                )
            }
            if (parsed.isEmpty()) DEFAULT_NETWORKS else parsed
        } catch (_: Exception) {
            DEFAULT_NETWORKS
        }
    }

    fun saveNetwork(network: SavedWifiNetwork) {
        val networks = getNetworks().toMutableList()
        val index = networks.indexOfFirst { it.label.equals(network.label, ignoreCase = true) }
        if (index >= 0) networks[index] = network else networks.add(network)
        prefs.edit {
            putString(KEY_NETWORKS, JSONArray().apply {
                networks.forEach {
                    put(JSONObject().apply {
                        put("label", it.label)
                        it.ssid?.let { ssid -> put("ssid", ssid) }
                    })
                }
            }.toString())
        }
    }

    companion object {
        private const val PREFS_NAME = "saved_wifi_networks"
        private const val KEY_NETWORKS = "networks"

        val DEFAULT_NETWORKS = listOf(
            SavedWifiNetwork("Home", null),
            SavedWifiNetwork("Work", null),
            SavedWifiNetwork("Campus", null)
        )
    }
}
