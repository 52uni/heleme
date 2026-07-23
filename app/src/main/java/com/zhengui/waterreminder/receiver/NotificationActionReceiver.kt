package com.zhengui.waterreminder.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.data.entity.WaterRecord
import com.zhengui.waterreminder.notification.NotificationHelper
import com.zhengui.waterreminder.util.PreferenceManager
import com.zhengui.waterreminder.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val drinkAmount = intent.getIntExtra("drink_amount", 200)

        GlobalScope.launch(Dispatchers.IO) {
            val currentTypeId = PreferenceManager.getCurrentTypeId(context)
            val db = (context.applicationContext as App).database
            db.waterRecordDao().insert(
                WaterRecord(
                    drinkTime = System.currentTimeMillis(),
                    amountMl = drinkAmount,
                    personTypeId = currentTypeId
                )
            )

            withContext(Dispatchers.Main) {
                WidgetUpdateHelper.updateAllWidgets(context)
                Toast.makeText(context, "已记录 ${drinkAmount}ml", Toast.LENGTH_SHORT).show()
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NotificationHelper.REMINDER_NOTIFICATION_ID)
            }
        }
    }
}
