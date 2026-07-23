package com.zhengui.waterreminder

import android.app.Application
import android.util.Log
import com.zhengui.waterreminder.data.AppDatabase
import com.zhengui.waterreminder.util.CrashHandler

class App : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "App.onCreate() 开始")
        try {
            // 初始化崩溃日志收集
            CrashHandler.init(this)
            Log.d(TAG, "CrashHandler 初始化成功")

            // 测试数据库初始化
            Log.d(TAG, "数据库初始化开始")
            val db = database
            Log.d(TAG, "数据库初始化成功: $db")
        } catch (e: Exception) {
            Log.e(TAG, "App 初始化失败", e)
            throw e
        }
        Log.d(TAG, "App.onCreate() 完成")
    }

    companion object {
        private const val TAG = "WaterReminderApp"
    }
}
