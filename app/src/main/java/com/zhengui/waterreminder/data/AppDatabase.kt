package com.zhengui.waterreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zhengui.waterreminder.data.dao.PersonTypeDao
import com.zhengui.waterreminder.data.dao.ReminderTimeDao
import com.zhengui.waterreminder.data.dao.WaterRecordDao
import com.zhengui.waterreminder.data.entity.PersonType
import com.zhengui.waterreminder.data.entity.ReminderTimeEntity
import com.zhengui.waterreminder.data.entity.WaterRecord

@Database(
    entities = [WaterRecord::class, PersonType::class, ReminderTimeEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun waterRecordDao(): WaterRecordDao
    abstract fun personTypeDao(): PersonTypeDao
    abstract fun reminderTimeDao(): ReminderTimeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "water_reminder_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
