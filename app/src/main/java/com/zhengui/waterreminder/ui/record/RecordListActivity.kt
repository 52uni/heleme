package com.zhengui.waterreminder.ui.record

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.ActivityRecordListBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RecordListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordListBinding
    private lateinit var viewModel: RecordListViewModel
    private lateinit var dayAdapter: DayRecordAdapter
    private lateinit var monthAdapter: MonthSummaryAdapter
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[RecordListViewModel::class.java]
        binding.toolbar.setNavigationOnClickListener { finish() }

        dayAdapter = DayRecordAdapter()
        monthAdapter = MonthSummaryAdapter(2500)
        binding.recyclerRecords.layoutManager = LinearLayoutManager(this)

        setupTabLayout()
        setupDateNavigation()
        setupSwipeToDelete()
        setupObservers()
        viewModel.loadData()
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        viewModel.switchToDayView()
                        binding.recyclerRecords.visibility = View.VISIBLE
                        binding.chartContainer.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.shimmerContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.VISIBLE
                    }
                    1 -> {
                        viewModel.switchToMonthView()
                        binding.recyclerRecords.visibility = View.VISIBLE
                        binding.chartContainer.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.shimmerContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.recyclerRecords.visibility = View.GONE
                        binding.chartContainer.visibility = View.VISIBLE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.shimmerContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.GONE
                        viewModel.loadChartData()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun setupDateNavigation() {
        binding.btnPrevDate.setOnClickListener { viewModel.prevDate() }
        binding.btnNextDate.setOnClickListener { viewModel.nextDate() }

        binding.tvCurrentDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(binding.tvCurrentDate.tag as? Long ?: System.currentTimeMillis())
                .build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                Calendar.getInstance().also { cal ->
                    cal.timeInMillis = selection
                    viewModel.setDate(cal)
                }
            }
            datePicker.show(supportFragmentManager, "date_picker")
        }
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = SwipeToDeleteCallback { position ->
            val record = dayAdapter.getItemAt(position)
            viewModel.deleteRecord(record)
            Snackbar.make(binding.root, "已删除记录", Snackbar.LENGTH_LONG)
                .setAction("撤销") { viewModel.loadData() }
                .show()
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerRecords)
    }

    private fun setupObservers() {
        viewModel.currentDate.observe(this) { cal ->
            updateDateDisplay(cal)
            binding.tvCurrentDate.tag = cal.timeInMillis
        }

        viewModel.isDayView.observe(this) { isDayView ->
            if (isDayView) {
                binding.recyclerRecords.adapter = dayAdapter
                binding.tvSummaryLabel.text = "当日总饮水量"
            } else {
                binding.recyclerRecords.adapter = monthAdapter
                binding.tvSummaryLabel.text = "当月总记录"
            }
        }

        viewModel.records.observe(this) { records ->
            dayAdapter.submitList(records)
            updateEmptyState(records.isEmpty())
        }

        viewModel.monthlySummaries.observe(this) { summaries ->
            monthAdapter.submitList(summaries)
            updateEmptyState(summaries.isEmpty())
        }

        viewModel.dailyTotal.observe(this) { total ->
            binding.tvSummaryAmount.text = "${total} ml"
            updateGoalStatus(total, viewModel.dailyGoal.value ?: 2500)
        }

        viewModel.dailyGoal.observe(this) { goal ->
            monthAdapter = MonthSummaryAdapter(goal)
            if (viewModel.isDayView.value != true) {
                binding.recyclerRecords.adapter = monthAdapter
                viewModel.monthlySummaries.value?.let { monthAdapter.submitList(it) }
            }
            updateGoalStatus(viewModel.dailyTotal.value ?: 0, goal)
        }

        viewModel.typeNames.observe(this) { names ->
            dayAdapter = DayRecordAdapter(names)
            if (viewModel.isDayView.value == true) {
                binding.recyclerRecords.adapter = dayAdapter
                viewModel.records.value?.let { dayAdapter.submitList(it) }
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                binding.shimmerContainer.visibility = View.VISIBLE
                binding.shimmerContainer.startShimmer()
                binding.recyclerRecords.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
            } else {
                binding.shimmerContainer.stopShimmer()
                binding.shimmerContainer.visibility = View.GONE
                if (binding.chartContainer.visibility != View.VISIBLE) {
                    binding.recyclerRecords.visibility = View.VISIBLE
                }
            }
        }

        viewModel.chartData.observe(this) { data ->
            setupBarChart(data)
            setupLineChart(data)
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty && viewModel.isLoading.value != true) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.recyclerRecords.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    private fun updateDateDisplay(cal: Calendar) {
        binding.tvCurrentDate.text = if (viewModel.isDayView.value == true) dayFormat.format(cal.time) else monthFormat.format(cal.time)
    }

    private fun updateGoalStatus(total: Int, goal: Int) {
        if (total >= goal) {
            binding.tvGoalStatus.text = "已达标"
            binding.tvGoalStatus.setTextColor(getColor(R.color.green_success))
        } else {
            binding.tvGoalStatus.text = "未达标(目标 ${goal}ml)"
            binding.tvGoalStatus.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun setupBarChart(summaries: List<com.zhengui.waterreminder.data.dao.WaterRecordDao.DailySummary>) {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        summaries.forEachIndexed { index, summary ->
            entries.add(BarEntry(index.toFloat(), summary.total.toFloat()))
            labels.add(summary.day.substring(5))
        }

        val dataSet = BarDataSet(entries, "饮水量(ml)").apply {
            color = getColor(R.color.blue_primary)
            valueTextSize = 10f
        }

        binding.barChart.apply {
            visibility = View.VISIBLE
            data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            description.isEnabled = false
            invalidate()
        }
        binding.lineChart.visibility = View.GONE
    }

    private fun setupLineChart(summaries: List<com.zhengui.waterreminder.data.dao.WaterRecordDao.DailySummary>) {
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        summaries.forEachIndexed { index, summary ->
            entries.add(Entry(index.toFloat(), summary.total.toFloat()))
            labels.add(summary.day.substring(5))
        }

        val dataSet = LineDataSet(entries, "饮水量(ml)").apply {
            color = getColor(R.color.blue_primary)
            valueTextSize = 10f
            setDrawValues(true)
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            description.isEnabled = false
        }
    }
}
