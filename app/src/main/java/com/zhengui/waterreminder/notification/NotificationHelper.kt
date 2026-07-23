package com.zhengui.waterreminder.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.ui.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "water_reminder_channel_v3"
        const val CHANNEL_NAME = "喝水提醒"
        const val REMINDER_NOTIFICATION_ID = 1001
        private const val OLD_CHANNEL_ID_V2 = "water_reminder_channel_v2"
        private const val OLD_CHANNEL_ID_V1 = "water_reminder_channel"
        private const val TAG = "NotificationHelper"
    }

    private val random = java.util.Random()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID_V1)
            notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID_V2)

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_description)
                setShowBadge(true)
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildIntervalReminderNotification(suggestedAmountMl: Int): android.app.Notification {
        val titles = context.resources.getStringArray(R.array.interval_reminder_titles)
        val title = titles[random.nextInt(titles.size)]
        val text = context.getString(R.string.reminder_notification_text, suggestedAmountMl)
        return buildNotification(title, text, suggestedAmountMl, FullscreenReminderActivity.TYPE_INTERVAL)
    }

    fun buildSmallCycleReminderNotification(suggestedAmountMl: Int): android.app.Notification {
        val titles = context.resources.getStringArray(R.array.small_cycle_reminder_titles)
        val title = titles[random.nextInt(titles.size)]
        val text = context.getString(R.string.reminder_notification_text, suggestedAmountMl)
        return buildNotification(title, text, suggestedAmountMl, FullscreenReminderActivity.TYPE_SMALL_CYCLE)
    }

    fun buildFixedReminderNotification(label: String, suggestedAmountMl: Int): android.app.Notification {
        val title = if (label.isNotBlank()) {
            context.getString(R.string.fixed_reminder_title, label)
        } else {
            context.getString(R.string.reminder_notification_title)
        }
        val text = context.getString(R.string.reminder_notification_text, suggestedAmountMl)
        return buildNotification(title, text, suggestedAmountMl, FullscreenReminderActivity.TYPE_FIXED)
    }

    private fun buildNotification(title: String, text: String, suggestedAmountMl: Int = 200, reminderType: String = FullscreenReminderActivity.TYPE_INTERVAL): android.app.Notification {
        // 点击通知打开主页
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_quick_drink", true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 全屏提醒Intent（锁屏时亮屏显示 - 作为备用方案）
        val fullScreenIntent = Intent(context, FullscreenReminderActivity::class.java).apply {
            putExtra(FullscreenReminderActivity.EXTRA_REMINDER_TYPE, reminderType)
            putExtra(FullscreenReminderActivity.EXTRA_AMOUNT, suggestedAmountMl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            suggestedAmountMl,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .build()
    }

    fun showIntervalReminderNotification(suggestedAmountMl: Int) {
        Log.d(TAG, "showIntervalReminderNotification: amount=$suggestedAmountMl, channel=$CHANNEL_ID")
        notificationManager.notify(REMINDER_NOTIFICATION_ID, buildIntervalReminderNotification(suggestedAmountMl))
    }

    fun showSmallCycleReminderNotification(suggestedAmountMl: Int) {
        Log.d(TAG, "showSmallCycleReminderNotification: amount=$suggestedAmountMl, channel=$CHANNEL_ID")
        notificationManager.notify(REMINDER_NOTIFICATION_ID, buildSmallCycleReminderNotification(suggestedAmountMl))
    }

    fun showFixedReminderNotification(label: String, suggestedAmountMl: Int) {
        Log.d(TAG, "showFixedReminderNotification: label=$label, amount=$suggestedAmountMl")
        notificationManager.notify(REMINDER_NOTIFICATION_ID, buildFixedReminderNotification(label, suggestedAmountMl))
    }
}
