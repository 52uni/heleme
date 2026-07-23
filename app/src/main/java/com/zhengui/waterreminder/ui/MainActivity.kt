package com.zhengui.waterreminder.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.ActivityMainBinding
import com.zhengui.waterreminder.notification.FullscreenReminderActivity
import com.zhengui.waterreminder.notification.NotificationHelper

import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager
import com.zhengui.waterreminder.ui.home.HomeFragment
import com.zhengui.waterreminder.ui.persontype.PersonTypeListFragment
import com.zhengui.waterreminder.ui.record.RecordListFragment
import com.zhengui.waterreminder.ui.remindertime.ReminderTimeFragment


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var showingHome = true
    private var pendingAutoStartSwitch: SwitchMaterial? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper(this).createChannel()
        setupNavigationDrawer()
        setupBottomNavigation()
        setupToolbarMenu()
        setupBackHandling()

        if (savedInstanceState == null) {
            showHome()
        } else {
            configureToolbarForHome()
        }
    }

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener { showHome() }
        binding.navRecords.setOnClickListener { showSection("喝水记录", RecordListFragment()) }
        binding.navReminder.setOnClickListener { showSection("提醒时间", ReminderTimeFragment()) }
        binding.navTypes.setOnClickListener { showSection("人群类型", PersonTypeListFragment()) }
    }

    private fun setupToolbarMenu() {
        binding.btnToolbarAction.setOnClickListener {
            showSection("人群类型", PersonTypeListFragment())
        }
    }

    private fun setupNavigationDrawer() {
        binding.drawerContent.navHome.setOnClickListener {
            showHome()
            closeDrawer()
        }
        binding.drawerContent.navRecords.setOnClickListener {
            showSection("喝水记录", RecordListFragment())
            closeDrawer()
        }
        binding.drawerContent.navReminderTimes.setOnClickListener {
            showSection("提醒时间", ReminderTimeFragment())
            closeDrawer()
        }
        binding.drawerContent.navPersonTypes.setOnClickListener {
            showSection("人群类型", PersonTypeListFragment())
            closeDrawer()
        }
        binding.drawerContent.navAbout.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
        }
        binding.drawerContent.navPreview.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this@MainActivity, FullscreenReminderActivity::class.java))
        }

        setupSwitches()
    }

    private fun setupSwitches() {
        binding.drawerContent.drawerSwitchReminder.apply {
            isChecked = PreferenceManager.isReminderEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkNotificationPermissionAndStart()
                } else {
                    stopReminder()
                }
            }
        }

        binding.drawerContent.drawerSwitchAutoStart.apply {
            isChecked = isIgnoringBatteryOptimizations()
            pendingAutoStartSwitch = this
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    requestAutoStartPermissions(this)
                } else {
                    PreferenceManager.setAutoStartEnabled(this@MainActivity, false)
                    Toast.makeText(this@MainActivity, "已关闭自动启动", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        } else {
            startReminder()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startReminder()
            } else {
                Toast.makeText(this, "需要通知权限才能开启提醒", Toast.LENGTH_SHORT).show()
                binding.drawerContent.drawerSwitchReminder.isChecked = false
            }
        }
    }

    private fun startReminder() {
        PreferenceManager.setReminderEnabled(this, true)
        // 先取消所有残留闹钟，防止重复或冲突
        ReminderScheduler.cancelReminder(this)
        ReminderScheduler.cancelSmallCycle(this)
        ReminderScheduler.cancelAllReminders(this)
        // 然后重新调度
        ReminderScheduler.scheduleNextReminder(this)
        ReminderScheduler.scheduleAllReminders(this)
        Toast.makeText(this, "喝水提醒已开启", Toast.LENGTH_SHORT).show()
    }

    private fun stopReminder() {
        PreferenceManager.setReminderEnabled(this, false)
        ReminderScheduler.cancelReminder(this)
        ReminderScheduler.cancelSmallCycle(this)
        ReminderScheduler.cancelAllReminders(this)
        Toast.makeText(this, "喝水提醒已关闭", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("BatteryLife")
    private fun requestAutoStartPermissions(autoStartSwitch: SwitchMaterial) {
        // 1. 请求忽略电池优化
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION)
                return // 等 onActivityResult 回来再继续
            }
        }
        // 2. 已经忽略电池优化（或低版本不需要），直接引导自启动
        enableAutoStart(autoStartSwitch)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_BATTERY_OPTIMIZATION) {
            val autoStartSwitch = pendingAutoStartSwitch
            if (autoStartSwitch != null) {
                // 检查用户是否真的授权了电池优化
                if (isIgnoringBatteryOptimizations()) {
                    enableAutoStart(autoStartSwitch)
                } else {
                    // 用户拒绝了，把开关设回 false
                    autoStartSwitch.isChecked = false
                    Toast.makeText(this, "需要允许电池优化才能开启自动启动", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 刷新自动启动开关状态（用户可能从系统设置返回）
        binding.drawerContent.drawerSwitchAutoStart.isChecked = isIgnoringBatteryOptimizations()
    }

    private fun enableAutoStart(autoStartSwitch: SwitchMaterial) {
        // 先保存设置
        PreferenceManager.setAutoStartEnabled(this, true)

        // 尝试引导用户开启系统自启动权限（各厂商设置页）
        if (!hasAutoStartPermission()) {
            showAutoStartGuideDialog(autoStartSwitch)
        } else {
            autoStartSwitch.isChecked = true
            Toast.makeText(this, "已开启自动启动，应用将常驻后台", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 检查是否可能已有自启动权限（通过检查电池优化白名单状态近似判断）
     */
    private fun hasAutoStartPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private fun showAutoStartGuideDialog(autoStartSwitch: SwitchMaterial) {
        val message = """
            为了确保喝水提醒能正常工作，请在系统设置中允许本应用自启动。
            
            不同品牌手机路径可能不同，一般在：
            设置 → 应用管理 → 喝水提醒 → 自启动管理
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("开启自启动权限")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                openAutoStartSettings()
                autoStartSwitch.isChecked = true
                Toast.makeText(this, "已开启自动启动，请在系统设置中允许自启动", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消") { _, _ ->
                autoStartSwitch.isChecked = true
                Toast.makeText(this, "已开启自动启动，建议稍后手动开启系统自启动", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 尝试跳转到各厂商的自启动管理页面
     */
    private fun openAutoStartSettings() {
        val intents = listOf(
            // 小米 MIUI
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            // 华为 EMUI
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            },
            // 华为 EMUI 新版
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            },
            // OPPO ColorOS
            Intent().apply {
                component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            },
            // 一加 (OnePlus) - ColorOS 融合前独立路径 + 融合后兼容 OPPO 路径
            Intent().apply {
                component = android.content.ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            },
            // vivo
            Intent().apply {
                component = android.content.ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.SoftwareManagerActivity"
                )
            },
            // 魅族 Flyme
            Intent().apply {
                component = android.content.ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.security.SafeCenterActivity"
                )
            },
            // 三星
            Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            },
            // 通用 - 应用详情页
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )

        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // 该厂商不支持，尝试下一个
            }
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    closeDrawer()
                } else if (!showingHome) {
                    showHome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showHome() {
        showingHome = true
        updateBottomNavSelection(R.id.navHome)
        switchFragment(HomeFragment())
        configureToolbarForHome()
    }

    private fun showSection(title: String, fragment: Fragment) {
        showingHome = false
        switchFragment(fragment)
        updateBottomNavSelection(null)
        configureToolbarForChild(title)
        closeDrawer()
    }

    private fun updateBottomNavSelection(selectedId: Int?) {
        val items = listOf(R.id.navHome, R.id.navRecords, R.id.navReminder, R.id.navTypes)
        items.forEach { id ->
            val layout = findViewById<android.widget.LinearLayout>(id) ?: return@forEach
            val isSelected = id == selectedId
            val color = if (isSelected) Color.parseColor("#111111") else Color.parseColor("#6B6B6B")
            (layout.getChildAt(0) as? android.widget.ImageView)?.drawable?.setTint(color)
            (layout.getChildAt(1) as? android.widget.TextView)?.setTextColor(color)
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun configureToolbarForHome() {
        binding.tvToolbarTitle.text = getString(R.string.app_name)
        binding.btnToolbarNav.visibility = View.VISIBLE
        binding.btnToolbarNav.setImageResource(R.drawable.ic_menu)
        binding.btnToolbarNav.contentDescription = getString(R.string.open_drawer)
        binding.btnToolbarNav.setOnClickListener { openDrawer() }
        binding.btnToolbarAction.visibility = View.VISIBLE
    }

    private fun configureToolbarForChild(title: String) {
        binding.tvToolbarTitle.text = title
        binding.btnToolbarNav.visibility = View.GONE
        binding.btnToolbarAction.visibility = View.GONE
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    companion object {
        private const val REQUEST_CODE_BATTERY_OPTIMIZATION = 200
    }
}
