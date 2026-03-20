package net.kollnig.reddblockandroid.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.scheduleWatcher
import net.kollnig.reddblockandroid.util.BLOCKER_CHANNEL_ID
import net.kollnig.reddblockandroid.util.NotificationHelper.createNotificationChannel
import net.kollnig.reddblockandroid.util.isPrefsInitialized
import net.kollnig.reddblockandroid.util.prefs

@SuppressLint("AccessibilityPolicy")
class BlockerService : AccessibilityService() {

    private val scheduleChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Schedules.ACTION_CHANGED) {
                Log.d(TAG, "Schedule state changed")
            }
        }
    }

    private var lastCheckedUrl: String? = null
    private var lastUrlCheckTime: Long = 0
    private val URL_CHECK_THROTTLE_MS = 500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()

        if (!isPrefsInitialized) {
            val deviceContext = createDeviceProtectedStorageContext()
            prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
        }

        val filter = IntentFilter(Schedules.ACTION_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scheduleChangeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scheduleChangeReceiver, filter)
        }

        scheduleWatcher(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (keyguardManager.isKeyguardLocked) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Check for website blocking in supported browsers
        if (isSupportedBrowser(pkg)) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUrlCheckTime >= URL_CHECK_THROTTLE_MS) {
                val url = extractUrlFromEvent(event)
                if (url != null) {
                    lastUrlCheckTime = currentTime
                    if (url != lastCheckedUrl) {
                        lastCheckedUrl = url
                        val domain = extractDomain(url)
                        if (domain != null && Schedules.isWebsiteBlocked(domain)) {
                            Log.d(TAG, "Blocking website $domain in browser ($pkg)")
                            navigateBrowserToBlank(pkg)
                            showWebsiteBlockedNotification(domain)
                            return
                        }
                    }
                }
            }
        }

        // Check app blocking
        if (shouldSkipPackage(pkg)) return

        if (Schedules.isAppBlocked(pkg)) {
            Log.d(TAG, "Blocking app $pkg")
            performGlobalAction(GLOBAL_ACTION_HOME)
            showAppBlockedNotification(pkg)
        }
    }

    private fun shouldSkipPackage(packageName: String): Boolean {
        return try {
            val info = this.packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) {
                this.packageManager.getLaunchIntentForPackage(packageName) == null
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Maps browser package names to their URL bar view IDs */
    private val browserUrlViewIds = mapOf(
        // Firefox variants
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        "org.mozilla.fenix.nightly" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        // Chrome / Chromium
        "com.android.chrome" to listOf("url_bar", "origin", "display_url"),
        "com.chrome.beta" to listOf("url_bar", "display_url"),
        "org.chromium.chrome" to listOf("url_bar", "display_url"),
        // Brave
        "com.brave.browser" to listOf("url_bar", "display_url"),
        "com.brave.browser_beta" to listOf("url_bar", "display_url"),
        "com.brave.browser_nightly" to listOf("url_bar", "display_url"),
        // Samsung Internet
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        // Microsoft Edge
        "com.microsoft.emmx" to listOf("url_bar"),
        // Opera variants
        "com.opera.browser" to listOf("url_field"),
        "com.opera.browser.beta" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.opera.mini.native.beta" to listOf("url_field"),
        "com.opera.touch" to listOf("addressbarEdit"),
        // Vivaldi
        "com.vivaldi.browser" to listOf("url_bar", "display_url"),
        // Kiwi Browser
        "com.kiwibrowser.browser" to listOf("url_bar", "display_url"),
        // DuckDuckGo
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        // Ecosia
        "com.ecosia.android" to listOf("url_bar"),
        // Huawei Browser
        "com.huawei.browser" to listOf("url_bar"),
        // Android system browser (AOSP)
        "com.android.browser" to listOf("url"),
        // Google Search app (in-app browser)
        "com.google.android.googlequicksearchbox" to listOf("googleapp_srp_search_box_text"),
    )

    private fun isSupportedBrowser(packageName: String): Boolean {
        return packageName in browserUrlViewIds
    }

    private fun navigateBrowserToBlank(browserPackage: String) {
        try {
            val uri = Uri.parse("https://reddfocus.org")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(browserPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            lastCheckedUrl = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate browser to blocked page", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun extractUrlFromEvent(event: AccessibilityEvent): String? {
        val root = rootInActiveWindow ?: return null
        try {
            val pkg = event.packageName?.toString() ?: return null
            val viewIds = browserUrlViewIds[pkg] ?: return null
            val knownUrlViewIds = viewIds.map { "$pkg:id/$it" } + viewIds

            // First try the standard API with fully-qualified resource IDs
            for (viewId in knownUrlViewIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes.isNullOrEmpty()) continue
                val url = extractUrlFromNodes(nodes)
                if (url != null) return url
            }

            // Fallback: manually traverse the tree to find nodes by bare resource-id.
            // Newer browsers (e.g. Firefox with Jetpack Compose) use test tags as
            // resource-ids without the "package:id/" prefix, which
            // findAccessibilityNodeInfosByViewId cannot match.
            val bareIds = viewIds.toSet()
            val fallbackNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
            findNodesByBareResourceId(root, bareIds, fallbackNodes)
            if (fallbackNodes.isNotEmpty()) {
                val url = extractUrlFromNodes(fallbackNodes)
                if (url != null) return url
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting URL", e)
        } finally {
            root.recycle()
        }
        return null
    }

    private fun extractUrlFromNodes(
        nodes: List<android.view.accessibility.AccessibilityNodeInfo>
    ): String? {
        for (node in nodes) {
            try {
                // Skip if the URL bar is focused — user is typing,
                // don't block on autocomplete suggestions
                if (node.isFocused) continue
                val rawText = node.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.toString()
                if (rawText != null) {
                    val words = rawText.split("\\s+".toRegex())
                    for (word in words) {
                        val cleanWord = word.trimEnd('.', ',')
                        if (isValidUrlFormat(cleanWord)) {
                            return cleanWord
                        }
                    }
                }
            } finally {
                node.recycle()
            }
        }
        return null
    }

    /** Recursively walks the accessibility tree looking for nodes whose
     *  viewIdResourceName matches one of [targetIds] (bare, without package prefix). */
    private fun findNodesByBareResourceId(
        node: android.view.accessibility.AccessibilityNodeInfo,
        targetIds: Set<String>,
        results: MutableList<android.view.accessibility.AccessibilityNodeInfo>
    ) {
        val resName = node.viewIdResourceName
        if (resName != null && resName in targetIds) {
            results.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByBareResourceId(child, targetIds, results)
            child.recycle()
        }
    }

    private fun isValidUrlFormat(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return true
        if (trimmed.contains(" ") || trimmed.length < 4) return false
        val domainPattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}(/.*)?$")
        return domainPattern.matches(trimmed)
    }

    private fun extractDomain(url: String): String? {
        return try {
            var normalizedUrl = url.trim()
            if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                normalizedUrl = "https://$normalizedUrl"
            }
            val uri = java.net.URI(normalizedUrl)
            uri.host?.lowercase()?.removePrefix("www.")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain from URL: $url", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun showAppBlockedNotification(pkg: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }

        val notification = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(getString(R.string.app_blocked))
            .setContentText(getString(R.string.blocked_by_schedule, appName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(pkg.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    private fun showWebsiteBlockedNotification(domain: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(this, BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(getString(R.string.website_blocked))
            .setContentText(getString(R.string.website_blocked_by_schedule, domain))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(domain.hashCode(), notification)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scheduleChangeReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    companion object {
        private const val TAG = "BlockerService"
    }
}
