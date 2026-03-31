package net.kollnig.reddblockandroid.util

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import net.kollnig.reddblockandroid.R

const val BLOCKER_CHANNEL_ID = "blocker_channel"
private const val BLOCKER_GROUP_ID = "blocker_group"

object NotificationHelper {

    fun Context.createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val group = NotificationChannelGroup(
            BLOCKER_GROUP_ID,
            getString(R.string.blocker_group_name)
        )
        notificationManager.createNotificationChannelGroup(group)

        val blockerChannel = NotificationChannel(
            BLOCKER_CHANNEL_ID,
            getString(R.string.blocker_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.blocker_channel_desc)
            this.group = BLOCKER_GROUP_ID
        }

        notificationManager.createNotificationChannel(blockerChannel)
    }
}
