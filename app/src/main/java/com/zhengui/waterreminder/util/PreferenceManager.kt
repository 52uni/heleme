package com.zhengui.waterreminder.util

import android.content.Context

object PreferenceManager {

    const val PREFS_NAME = "water_reminder_prefs"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_CURRENT_TYPE_ID = "current_type_id"
    private const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
    private const val KEY_HAS_TRIED_SWIPE_DELETE_REMINDER = "has_tried_swipe_delete_reminder"

    fun isReminderEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_REMINDER_ENABLED, false)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getCurrentTypeId(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_CURRENT_TYPE_ID, 1L)
    }

    fun setCurrentTypeId(context: Context, typeId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_CURRENT_TYPE_ID, typeId).apply()
    }

    fun isAutoStartEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_START_ENABLED, false)
    }

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_START_ENABLED, enabled).apply()
    }

    fun hasTriedSwipeDeleteReminder(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_TRIED_SWIPE_DELETE_REMINDER, false)
    }

    fun setHasTriedSwipeDeleteReminder(context: Context, tried: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HAS_TRIED_SWIPE_DELETE_REMINDER, tried).apply()
    }
}
