package net.kollnig.reddblockandroid.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import net.kollnig.reddblockandroid.R

const val BLOCKER_CHANNEL_ID = "blocker_channel"

object NotificationHelper {

    fun Context.createNotificationChannel() {
        val blockerChannel = NotificationChannel(
            BLOCKER_CHANNEL_ID,
            getString(R.string.blocker_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.blocker_channel_desc)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(blockerChannel)
    }
}
