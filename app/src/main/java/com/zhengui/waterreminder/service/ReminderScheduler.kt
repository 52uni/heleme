package com.zhengui.waterreminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.receiver.ReminderReceiver
import com.zhengui.waterreminder.ui.MainActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "协程异常", e)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private const val PREFS_NAME = "water_reminder_prefs"
    private const val KEY_LAST_DRINK_TIME = "last_drink_time"
    private const val REQUEST_CODE_INTERVAL = 0
    private const val REQUEST_CODE_SMALL_CYCLE = -1
    private const val REQUEST_CODE_ALARM_CLOCK = -2
    private const val KEY_LAST_INTERVAL_TRIGGER_TIME = "last_interval_trigger_time"
    const val SMALL_CYCLE_MINUTES = 5

    /**
     * 调度间隔提醒（基于开始时间或打卡时间）
     * - 首次提醒：从类型的开始时间（如 9:00）算起
     * - 如果已过开始时间但还没到结束时间：从当前时间 + 间隔
     * - 打卡后调用时：从打卡时刻 + 间隔
     */
    fun scheduleNextReminder(context: Context) {
        // 先取消旧的间隔闹钟，防止重复调度
        cancelReminder(context)
        scope.launch {
            try {
                val db = (context.applicationContext as App).database
                val typeId = WaterReminderService.getCurrentTypeId(context)
                val type = db.personTypeDao().getById(typeId)
                val intervalMin = type?.reminderIntervalMin ?: 120
                val defaultAmount = type?.defaultAmountMl ?: 200

                val startHour = type?.notificationStartHour ?: 8
                val startMinute = type?.notificationStartMinute ?: 0
                val endHour = type?.notificationEndHour ?: 21
                val endMinute = type?.notificationEndMinute ?: 0

                Log.d(TAG, "调度间隔提醒: interval=${intervalMin}min, 时段=${startHour}:${"%02d".format(startMinute)}-${endHour}:${"%02d".format(endMinute)}")

                val now = System.currentTimeMillis()
                val triggerTime: Long

                // 获取上次打卡时间
                val lastDrinkTime = getLastDrinkTime(context)

                if (lastDrinkTime > 0) {
                    // 有打卡记录：从打卡时间 + 间隔
                    triggerTime = lastDrinkTime + intervalMin * 60 * 1000L
                } else {
                    // 没有打卡记录：从今天的开始时间算起
                    val startCalendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startTime = startCalendar.timeInMillis

                    triggerTime = if (now < startTime) {
                        // 还没到开始时间，从开始时间算
                        startTime
                    } else {
                        // 已过开始时间，从当前时间 + 间隔
                        now + intervalMin * 60 * 1000L
                    }
                }

                // 检查触发时间是否早于今天的开始时间
                val startCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startTime = startCalendar.timeInMillis

                // 钳位：triggerTime 不能早于开始时间
                var effectiveTriggerTime = if (triggerTime < startTime) startTime else triggerTime

                // 安全兜底：确保触发时间在未来，防止无限循环
                if (effectiveTriggerTime <= now) {
                    effectiveTriggerTime = now + intervalMin * 60 * 1000L
                }

                // 检查触发时间是否超出今天的结束时间
                val endCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, endHour)
                    set(Calendar.MINUTE, endMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endTime = endCalendar.timeInMillis

                if (effectiveTriggerTime > endTime) {
                    // 已超出结束时间，调度到明天的开始时间
                    val tomorrowStart = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    scheduleAlarm(context, tomorrowStart.timeInMillis, defaultAmount)
                } else {
                    scheduleAlarm(context, effectiveTriggerTime, defaultAmount)
                }
            } catch (e: Exception) {
                Log.e(TAG, "调度间隔提醒失败", e)
            }
        }
        WaterReminderService.setReminderEnabled(context, true)
    }

    /**
     * 用户喝水打卡后重新调度提醒
     * 从打卡时刻 + 间隔分钟
     */
    fun scheduleAfterDrink(context: Context) {
        val now = System.currentTimeMillis()
        setLastDrinkTime(context, now)
        cancelSmallCycle(context)
        scheduleNextReminder(context)
    }

    /**
     * 记录间隔提醒触发时间，并启动小周期检查
     */
    fun onIntervalReminderTriggered(context: Context, suggestedAmount: Int = 200) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_INTERVAL_TRIGGER_TIME, now).apply()
        scheduleSmallCycle(context, suggestedAmount)
    }

    /**
     * 调度小周期检查闹钟（5分钟后）
     * 使用 setAlarmClock 确保最高优先级，防止被 OEM 系统丢弃
     */
    fun scheduleSmallCycle(context: Context, suggestedAmount: Int = 200) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("is_small_cycle_reminder", true)
            putExtra("suggested_amount", suggestedAmount)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SMALL_CYCLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + SMALL_CYCLE_MINUTES * 60 * 1000L
        val triggerDate = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(triggerTime))
        Log.d(TAG, "调度小周期闹钟 → $triggerDate，使用 setAlarmClock 确保可靠性")

        // 小周期闹钟直接使用 setAlarmClock，确保在 OEM 杀进程后仍能触发
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val showIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_ALARM_CLOCK - 1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, showIntent),
                pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * 取消小周期闹钟
     */
    fun cancelSmallCycle(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SMALL_CYCLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * 获取上次间隔提醒触发时间
     */
    fun getLastIntervalTriggerTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_INTERVAL_TRIGGER_TIME, 0L)
    }

    /**
     * 精确调度闹钟，自动处理 Android 12+ 的精确闹钟权限问题
     * - 有精确闹钟权限：使用 setExactAndAllowWhileIdle（精确且可唤醒）
     * - 无精确闹钟权限：使用 setAlarmClock（始终精确，会在状态栏显示闹钟图标）
     * - Android 12 以下：使用 setExactAndAllowWhileIdle，失败则回退到 set
     */
    private fun scheduleExactAlarm(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent,
        context: Context
    ) {
        val triggerDate = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(triggerTime))
        Log.d(TAG, "调度闹钟 → $triggerDate")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "使用 setExactAndAllowWhileIdle")
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // 无精确闹钟权限，使用 setAlarmClock 确保准时触发
                Log.d(TAG, "无精确闹钟权限，使用 setAlarmClock")
                val showIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_ALARM_CLOCK,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, showIntent),
                    pendingIntent
                )
            }
        } else {
            try {
                Log.d(TAG, "使用 setExactAndAllowWhileIdle (API < 31)")
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "setExact 失败，回退到 set", e)
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
    }

    private fun scheduleAlarm(context: Context, triggerTime: Long, defaultAmount: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("suggested_amount", defaultAmount)
            putExtra("is_interval_reminder", true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_INTERVAL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactAlarm(alarmManager, triggerTime, pendingIntent, context)
    }

    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_INTERVAL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleAllReminders(context: Context) {
        scope.launch {
            try {
                val db = (context.applicationContext as App).database
                val enabledTimes = db.reminderTimeDao().getEnabled()

                val typeId = WaterReminderService.getCurrentTypeId(context)
                val type = db.personTypeDao().getById(typeId)
                val defaultAmount = type?.defaultAmountMl ?: 200

                Log.d(TAG, "调度固定时间提醒，共 ${enabledTimes.size} 个启用项")

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                enabledTimes.forEach { reminderTime ->
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, reminderTime.hour)
                        set(Calendar.MINUTE, reminderTime.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    }

                    val triggerTime = calendar.timeInMillis
                    val amount = if (reminderTime.amountMl > 0) reminderTime.amountMl else defaultAmount

                    val intent = Intent(context, ReminderReceiver::class.java).apply {
                        putExtra("suggested_amount", amount)
                        putExtra("reminder_time_id", reminderTime.id)
                        putExtra("reminder_hour", reminderTime.hour)
                        putExtra("reminder_minute", reminderTime.minute)
                        putExtra("reminder_label", reminderTime.label)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        reminderTime.id.toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    scheduleExactAlarm(alarmManager, triggerTime, pendingIntent, context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "调度固定时间提醒失败", e)
            }
        }
        WaterReminderService.setReminderEnabled(context, true)
    }

    /**
     * 将单个固定时间点提醒重新调度到明天同一时间
     */
    fun scheduleFixedReminderToTomorrow(context: Context, reminderTimeId: Long, hour: Int, minute: Int) {
        scope.launch {
            try {
                val db = (context.applicationContext as App).database
                val reminderTime = db.reminderTimeDao().getById(reminderTimeId)
                val defaultAmount = reminderTime?.amountMl?.takeIf { it > 0 } ?: run {
                    val typeId = WaterReminderService.getCurrentTypeId(context)
                    val type = db.personTypeDao().getById(typeId)
                    type?.defaultAmountMl ?: 200
                }
                val label = reminderTime?.label ?: ""

                val calendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("suggested_amount", defaultAmount)
                    putExtra("reminder_time_id", reminderTimeId)
                    putExtra("reminder_hour", hour)
                    putExtra("reminder_minute", minute)
                    putExtra("reminder_label", label)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reminderTimeId.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleExactAlarm(alarmManager, calendar.timeInMillis, pendingIntent, context)
            } catch (e: Exception) {
                Log.e(TAG, "调度明天固定提醒失败", e)
            }
        }
    }

    fun cancelAllReminders(context: Context) {
        scope.launch {
            try {
                val db = (context.applicationContext as App).database
                val allTimes = db.reminderTimeDao().getEnabled()

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                // 取消所有固定时间提醒闹钟
                allTimes.forEach { reminderTime ->
                    val intent = Intent(context, ReminderReceiver::class.java)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        reminderTime.id.toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(pendingIntent)
                }

                // 取消间隔提醒（大周期）闹钟
                val intervalIntent = Intent(context, ReminderReceiver::class.java)
                val intervalPendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_INTERVAL,
                    intervalIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(intervalPendingIntent)

                // 取消小周期闹钟
                val smallCycleIntent = Intent(context, ReminderReceiver::class.java)
                val smallCyclePendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_SMALL_CYCLE,
                    smallCycleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(smallCyclePendingIntent)
            } catch (e: Exception) {
                Log.e(TAG, "取消所有提醒失败", e)
            }
        }
    }

    fun getLastDrinkTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_DRINK_TIME, 0L)
    }

    fun setLastDrinkTime(context: Context, time: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_DRINK_TIME, time).apply()
    }
}
