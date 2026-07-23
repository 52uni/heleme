package com.zhengui.waterreminder.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.data.dao.WaterRecordDao
import com.zhengui.waterreminder.data.entity.WaterRecord
import com.zhengui.waterreminder.data.repository.PersonTypeRepository
import com.zhengui.waterreminder.data.repository.WaterRecordRepository
import com.zhengui.waterreminder.service.WaterReminderService
import kotlinx.coroutines.launch
import java.util.Calendar

class RecordListViewModel(application: Application) : AndroidViewModel(application) {

    private val waterRecordRepo = WaterRecordRepository((application as App).database.waterRecordDao())
    private val personTypeRepo = PersonTypeRepository((application as App).database.personTypeDao())

    private val _records = MutableLiveData<List<WaterRecord>>(emptyList())
    val records: LiveData<List<WaterRecord>> = _records

    private val _monthlySummaries = MutableLiveData<List<WaterRecordDao.DailySummary>>(emptyList())
    val monthlySummaries: LiveData<List<WaterRecordDao.DailySummary>> = _monthlySummaries

    private val _dailyTotal = MutableLiveData(0)
    val dailyTotal: LiveData<Int> = _dailyTotal

    private val _dailyGoal = MutableLiveData(2500)
    val dailyGoal: LiveData<Int> = _dailyGoal

    private val _currentDate = MutableLiveData(Calendar.getInstance())
    val currentDate: LiveData<Calendar> = _currentDate

    private val _isDayView = MutableLiveData(true)
    val isDayView: LiveData<Boolean> = _isDayView

    private val _typeNames = MutableLiveData<Map<Long, String>>(emptyMap())
    val typeNames: LiveData<Map<Long, String>> = _typeNames

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _chartData = MutableLiveData<List<WaterRecordDao.DailySummary>>(emptyList())
    val chartData: LiveData<List<WaterRecordDao.DailySummary>> = _chartData

    fun switchToDayView() {
        _isDayView.value = true
        loadDayData()
    }

    fun switchToMonthView() {
        _isDayView.value = false
        loadMonthData()
    }

    fun prevDate() {
        val cal = _currentDate.value ?: Calendar.getInstance()
        if (_isDayView.value == true) cal.add(Calendar.DAY_OF_MONTH, -1) else cal.add(Calendar.MONTH, -1)
        _currentDate.value = cal
        loadData()
    }

    fun nextDate() {
        val cal = _currentDate.value ?: Calendar.getInstance()
        if (_isDayView.value == true) cal.add(Calendar.DAY_OF_MONTH, 1) else cal.add(Calendar.MONTH, 1)
        _currentDate.value = cal
        loadData()
    }

    fun setDate(cal: Calendar) {
        _currentDate.value = cal
        loadData()
    }

    fun loadData() {
        if (_isDayView.value == true) loadDayData() else loadMonthData()
    }

    fun loadChartData() {
        viewModelScope.launch {
            val cal = _currentDate.value ?: return@launch
            _chartData.value = waterRecordRepo.getMonthlySummary(getMonthStart(cal), getMonthEnd(cal))
        }
    }

    private fun loadDayData() {
        viewModelScope.launch {
            _isLoading.value = true
            val cal = _currentDate.value ?: return@launch
            val dayStart = getDayStart(cal)
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L

            _records.value = waterRecordRepo.getByDate(dayStart, dayEnd)
            _dailyTotal.value = waterRecordRepo.getDailyTotal(dayStart, dayEnd)

            val typeId = WaterReminderService.getCurrentTypeId(getApplication())
            val type = personTypeRepo.getById(typeId)
            _dailyGoal.value = type?.dailyGoalMl ?: 2500
            loadTypeNames()
            _isLoading.value = false
        }
    }

    private fun loadMonthData() {
        viewModelScope.launch {
            _isLoading.value = true
            val cal = _currentDate.value ?: return@launch
            val monthStart = getMonthStart(cal)
            val monthEnd = getMonthEnd(cal)
            _monthlySummaries.value = waterRecordRepo.getMonthlySummary(monthStart, monthEnd)
            _dailyTotal.value = waterRecordRepo.getDailyTotal(monthStart, monthEnd) ?: 0

            val typeId = WaterReminderService.getCurrentTypeId(getApplication())
            val type = personTypeRepo.getById(typeId)
            _dailyGoal.value = type?.dailyGoalMl ?: 2500
            _isLoading.value = false
        }
    }

    private suspend fun loadTypeNames() {
        val records = _records.value ?: return
        val typeIds = records.mapNotNull { it.personTypeId }.distinct()
        val nameMap = mutableMapOf<Long, String>()
        for (id in typeIds) {
            personTypeRepo.getById(id)?.let { nameMap[id] = it.name }
        }
        _typeNames.value = nameMap
    }

    fun deleteRecord(record: WaterRecord) {
        viewModelScope.launch {
            waterRecordRepo.delete(record)
            loadData()
        }
    }

    private fun getDayStart(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    private fun getMonthStart(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.set(Calendar.DAY_OF_MONTH, 1)
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    private fun getMonthEnd(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.add(Calendar.MONTH, 1)
        copy.set(Calendar.DAY_OF_MONTH, 1)
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    fun getPersonTypeName(id: Long?, callback: (String) -> Unit) {
        viewModelScope.launch {
            if (id == null) {
                callback("未知")
                return@launch
            }
            callback(personTypeRepo.getById(id)?.name ?: "未知")
        }
    }
}
