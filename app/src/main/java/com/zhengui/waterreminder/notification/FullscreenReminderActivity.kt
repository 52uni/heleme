package com.zhengui.waterreminder.notification

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.ActivityFullscreenReminderBinding
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.service.WaterReminderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class FullscreenReminderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
        const val EXTRA_AMOUNT = "extra_amount"
        const val TYPE_INTERVAL = "interval"
        const val TYPE_SMALL_CYCLE = "small_cycle"
        const val TYPE_FIXED = "fixed"
    }

    private val random = java.util.Random()
    private lateinit var binding: ActivityFullscreenReminderBinding
    private val cheerGifResIds = intArrayOf(
        R.raw.cheer_gif_1,
        R.raw.cheer_gif_2,
        R.raw.cheer_gif_3,
        R.raw.cheer_gif_4,
        R.raw.cheer_gif_5
    )

    // 锁屏专用俏皮文案
    private val lockScreenTitles = arrayOf(
        "嘿！水杯在呼唤你～",
        "咕噜咕噜时间到！",
        "别让身体喊渴啦～",
        "休息一下，喝杯水吧！",
        "你的小水杯想你了~",
        "来一口，续命神器！",
        "水水更健康～",
        "滴滴滴！补水警告！",
        "小可爱，该喝水啦！",
        "坐太久了吧？喝口水！"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        turnOnScreen()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityFullscreenReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 加载随机鼓励GIF
        val gifResId = cheerGifResIds[random.nextInt(cheerGifResIds.size)]
        Glide.with(this)
            .asGif()
            .load(gifResId)
            .into(binding.ivCheerGif)

        val amount = intent.getIntExtra(EXTRA_AMOUNT, 200)
        val title = lockScreenTitles[random.nextInt(lockScreenTitles.size)]

        binding.tvReminderTitle.text = title
        binding.tvReminderAmount.text = "来一杯 ${amount}ml 吧～"

        binding.btnDrinkNow.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val typeId = WaterReminderService.getCurrentTypeId(this@FullscreenReminderActivity)
                val db = (application as com.zhengui.waterreminder.App).database
                val type = db.personTypeDao().getById(typeId)
                val drinkAmount = type?.defaultAmountMl ?: amount
                db.waterRecordDao().insert(
                    com.zhengui.waterreminder.data.entity.WaterRecord(
                        drinkTime = System.currentTimeMillis(),
                        amountMl = drinkAmount,
                        personTypeId = typeId
                    )
                )

                // 检查是否达标
                val todayStart = getTodayStartMs()
                val dailyTotal = db.waterRecordDao().getDailyTotal(todayStart, System.currentTimeMillis()) ?: 0
                val goalMl = type?.dailyGoalMl ?: 2000
                val didReachGoal = dailyTotal >= goalMl

                runOnUiThread {
                    if (didReachGoal) {
                        showGoalCelebration()
                    } else {
                        if (WaterReminderService.isReminderEnabled(this@FullscreenReminderActivity)) {
                            CoroutineScope(Dispatchers.IO).launch {
                                ReminderScheduler.scheduleAfterDrink(this@FullscreenReminderActivity)
                            }
                        }
                        finish()
                    }
                }

                if (!didReachGoal && WaterReminderService.isReminderEnabled(this@FullscreenReminderActivity)) {
                    ReminderScheduler.scheduleAfterDrink(this@FullscreenReminderActivity)
                }
            }
        }

        binding.btnDismiss.setOnClickListener {
            finish()
        }
    }

    private fun showGoalCelebration() {
        binding.tvGoalCheer.text = "你真棒！今日目标已达成！"
        binding.tvGoalCheer.visibility = android.view.View.VISIBLE
        binding.tvReminderTitle.text = "太厉害了！"
        binding.tvReminderAmount.text = "今天的喝水量已经达标啦～"
        binding.btnDrinkNow.text = "好的！"
        binding.btnDismiss.text = "继续加油"

        binding.btnDrinkNow.setOnClickListener {
            finish()
        }
        binding.btnDismiss.setOnClickListener {
            finish()
        }
    }

    private fun getTodayStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    override fun onDestroy() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(NotificationHelper.REMINDER_NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun turnOnScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WaterReminder:FullscreenWakeLock"
        )
        wakeLock.acquire(3000)
    }
}
