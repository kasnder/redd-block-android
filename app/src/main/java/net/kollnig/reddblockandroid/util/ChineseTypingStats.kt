package net.kollnig.reddblockandroid.util

import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object ChineseTypingStats {
    private const val KEY = "chinese_typing_events"
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    data class WeeklyStats(val wordCount: Int, val totalDurationMs: Long)

    fun recordWord(durationMs: Long) {
        if (!isPrefsInitialized) return
        val cutoff = System.currentTimeMillis() - WEEK_MS
        val arr = readArray().prune(cutoff)
        arr.put(JSONObject().apply {
            put("t", System.currentTimeMillis())
            put("d", durationMs.coerceAtLeast(0))
        })
        prefs.edit { putString(KEY, arr.toString()) }
    }

    fun getWeeklyStats(): WeeklyStats {
        if (!isPrefsInitialized) return WeeklyStats(0, 0)
        val cutoff = System.currentTimeMillis() - WEEK_MS
        val arr = readArray().prune(cutoff)
        var total = 0L
        for (i in 0 until arr.length()) {
            total += arr.getJSONObject(i).optLong("d", 0)
        }
        return WeeklyStats(arr.length(), total)
    }

    private fun readArray(): JSONArray {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }

    private fun JSONArray.prune(cutoff: Long): JSONArray {
        val out = JSONArray()
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            if (o.optLong("t", 0) >= cutoff) out.put(o)
        }
        return out
    }
}
