package com.zhengui.waterreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.data.entity.PersonType
import com.zhengui.waterreminder.data.repository.PersonTypeRepository
import com.zhengui.waterreminder.data.repository.WaterRecordRepository
import com.zhengui.waterreminder.service.WaterReminderService
import com.zhengui.waterreminder.service.ReminderScheduler
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val waterRecordRepo = WaterRecordRepository(app.database.waterRecordDao())
    private val personTypeRepo = PersonTypeRepository(app.database.personTypeDao())

    private val _dailyTotal = MutableLiveData(0)
    val dailyTotal: LiveData<Int> = _dailyTotal

    private val _dailyGoal = MutableLiveData(2500)
    val dailyGoal: LiveData<Int> = _dailyGoal

    private val _currentType = MutableLiveData<PersonType?>()
    val currentType: LiveData<PersonType?> = _currentType

    val isReminderEnabled = MutableLiveData(WaterReminderService.isReminderEnabled(application))

    private val _streakDays = MutableLiveData(0)
    val streakDays: LiveData<Int> = _streakDays

    val allPersonTypes: LiveData<List<PersonType>> = personTypeRepo.getAllTypesFlow().asLiveData()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L

            val total = waterRecordRepo.getDailyTotal(dayStart, dayEnd)
            _dailyTotal.value = total

            val typeId = WaterReminderService.getCurrentTypeId(getApplication())
            val type = personTypeRepo.getById(typeId)
            _currentType.value = type
            _dailyGoal.value = type?.dailyGoalMl ?: 2500

            val streakCal = Calendar.getInstance()
            var streak = 0
            val goalMl = type?.dailyGoalMl ?: 2500
            for (i in 0 until 365) {
                streakCal.set(Calendar.HOUR_OF_DAY, 0)
                streakCal.set(Calendar.MINUTE, 0)
                streakCal.set(Calendar.SECOND, 0)
                streakCal.set(Calendar.MILLISECOND, 0)
                val dStart = streakCal.timeInMillis
                val dEnd = dStart + 24 * 60 * 60 * 1000L
                val dayTotal = waterRecordRepo.getDailyTotal(dStart, dEnd)
                if (dayTotal >= goalMl) {
                    streak++
                    streakCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
            _streakDays.value = streak
            isReminderEnabled.value = WaterReminderService.isReminderEnabled(getApplication())
        }
    }

    fun quickDrink() {
        viewModelScope.launch {
            val typeId = WaterReminderService.getCurrentTypeId(getApplication())
            val type = personTypeRepo.getById(typeId)
            waterRecordRepo.insert(type?.defaultAmountMl ?: 200, typeId)
            if (WaterReminderService.isReminderEnabled(getApplication())) {
                ReminderScheduler.scheduleAfterDrink(getApplication())
            }
            refreshData()
        }
    }

    fun customDrink(amountMl: Int) {
        viewModelScope.launch {
            val typeId = WaterReminderService.getCurrentTypeId(getApplication())
            waterRecordRepo.insert(amountMl, typeId)
            if (WaterReminderService.isReminderEnabled(getApplication())) {
                ReminderScheduler.scheduleAfterDrink(getApplication())
            }
            refreshData()
        }
    }

    fun initPresetTypesIfNeeded() {
        viewModelScope.launch {
            val presets = personTypeRepo.getPresetTypes()
            if (presets.isEmpty()) {
                personTypeRepo.insert(PersonType(name = "成年男性", dailyGoalMl = 2500, defaultAmountMl = 250, reminderIntervalMin = 90, isPreset = true))
                personTypeRepo.insert(PersonType(name = "成年女性", dailyGoalMl = 2000, defaultAmountMl = 200, reminderIntervalMin = 120, isPreset = true))
                personTypeRepo.insert(PersonType(name = "青少年", dailyGoalMl = 1800, defaultAmountMl = 200, reminderIntervalMin = 120, isPreset = true))
                personTypeRepo.insert(PersonType(name = "运动人群", dailyGoalMl = 3000, defaultAmountMl = 300, reminderIntervalMin = 60, isPreset = true))
            }
        }
    }

    fun deletePersonType(type: PersonType) {
        viewModelScope.launch {
            personTypeRepo.delete(type)
        }
    }
}
