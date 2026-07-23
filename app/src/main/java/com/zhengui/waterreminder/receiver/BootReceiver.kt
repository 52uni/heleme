package com.zhengui.waterreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.service.WaterReminderService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (WaterReminderService.isReminderEnabled(context)) {
                    ReminderScheduler.scheduleNextReminder(context)
                    ReminderScheduler.scheduleAllReminders(context)
                }

            }
        }
    }
}
