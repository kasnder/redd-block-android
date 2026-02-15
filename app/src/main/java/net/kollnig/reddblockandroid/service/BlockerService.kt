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
        if (event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Check for website blocking in supported browsers
        if (isSupportedBrowser(pkg)) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUrlCheckTime >= URL_CHECK_THROTTLE_MS) {
                lastUrlCheckTime = currentTime
                val url = extractUrlFromEvent(event)
                if (url != null && url != lastCheckedUrl) {
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

    private fun isSupportedBrowser(packageName: String): Boolean {
        return packageName == "org.mozilla.firefox" ||
                packageName == "org.mozilla.firefox_beta" ||
                packageName == "org.mozilla.fenix" ||
                packageName == "org.mozilla.fenix.nightly" ||
                packageName == "org.mozilla.focus" ||
                packageName == "com.android.chrome" ||
                packageName == "com.brave.browser" ||
                packageName == "com.brave.browser_beta" ||
                packageName == "com.brave.browser_nightly" ||
                packageName == "org.chromium.chrome"
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
            val knownUrlViewIds = listOf(
                "$pkg:id/mozac_browser_toolbar_url_view",
                "$pkg:id/url_bar_title",
                "$pkg:id/display_url",
                "$pkg:id/url_bar"
            )

            for (viewId in knownUrlViewIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes.isNullOrEmpty()) continue
                for (node in nodes) {
                    try {
                        val text = node.text?.toString()
                        if (text != null && isValidUrlFormat(text)) {
                            return text
                        }
                    } finally {
                        node.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting URL", e)
        } finally {
            root.recycle()
        }
        return null
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
