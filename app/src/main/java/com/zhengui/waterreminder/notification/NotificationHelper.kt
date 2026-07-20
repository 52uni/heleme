package com.zhengui.waterreminder.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.ui.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "water_reminder_channel_v2"
        const val CHANNEL_NAME = "喝水提醒"
        const val REMINDER_NOTIFICATION_ID = 1001
        private const val OLD_CHANNEL_ID = "water_reminder_channel"
        private const val TAG = "NotificationHelper"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID)

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_description)
                setShowBadge(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildReminderNotification(suggestedAmountMl: Int): android.app.Notification {
        return buildNotification(
            context.getString(R.string.reminder_notification_title),
            context.getString(R.string.reminder_notification_text, suggestedAmountMl)
        )
    }

    fun buildFixedReminderNotification(label: String, suggestedAmountMl: Int): android.app.Notification {
        val title = if (label.isNotBlank()) {
            context.getString(R.string.fixed_reminder_title, label)
        } else {
            context.getString(R.string.reminder_notification_title)
        }
        val text = context.getString(R.string.reminder_notification_text, suggestedAmountMl)
        return buildNotification(title, text)
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_quick_drink", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showReminderNotification(suggestedAmountMl: Int) {
        Log.d(TAG, "showReminderNotification: amount=$suggestedAmountMl, channel=$CHANNEL_ID")
        notificationManager.notify(REMINDER_NOTIFICATION_ID, buildReminderNotification(suggestedAmountMl))
    }

    fun showFixedReminderNotification(label: String, suggestedAmountMl: Int) {
        Log.d(TAG, "showFixedReminderNotification: label=$label, amount=$suggestedAmountMl")
        notificationManager.notify(REMINDER_NOTIFICATION_ID, buildFixedReminderNotification(label, suggestedAmountMl))
    }
}
