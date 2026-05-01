package net.kollnig.reddblockandroid.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONObject

class WifiContextProvider(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun hasPermission(): Boolean {
        val hasWifiState = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val hasWifiIdentityAccess = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasWifiState && hasWifiIdentityAccess
    }

    fun runtimePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            Manifest.permission.ACCESS_WIFI_STATE
        }
    }

    fun currentWifiJson(): JSONObject? {
        val current = currentWifi() ?: return null
        return JSONObject()
            .put("ssid", current.ssid)
            .put("source", "current_wifi_connection")
    }

    fun currentWifi(): CurrentWifi? {
        if (!hasPermission()) return null
        val info = wifiManager.connectionInfo ?: return null
        val ssid = cleanSsid(info.ssid)
        if (ssid.isBlank() || ssid == UNKNOWN_SSID) return null
        return CurrentWifi(ssid = ssid)
    }

    fun isWifiVisibleOrConnected(ssid: String): Boolean {
        val normalized = ssid.trim()
        if (normalized.isBlank() || !hasPermission()) return false
        if (currentWifi()?.ssid == normalized) return true
        return visibleWifiSsids().contains(normalized)
    }

    fun visibleWifiSsids(): Set<String> {
        if (!hasPermission()) return emptySet()
        return try {
            wifiManager.scanResults
                .orEmpty()
                .mapNotNull { result ->
                    cleanSsid(result.SSID).takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
                }
                .toSet()
        } catch (_: SecurityException) {
            emptySet()
        }
    }

    private fun cleanSsid(value: String?): String {
        return value.orEmpty().trim().removeSurrounding("\"")
    }

    data class CurrentWifi(
        val ssid: String
    )

    companion object {
        private const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
