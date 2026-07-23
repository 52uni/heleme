package com.zhengui.waterreminder.ui.record

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.FragmentRecordListBinding
import com.zhengui.waterreminder.util.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RecordListFragment : Fragment() {

    private var _binding: FragmentRecordListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RecordListViewModel
    private lateinit var dayAdapter: DayRecordAdapter
    private lateinit var monthAdapter: MonthSummaryAdapter
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private var isBarChartMode = true
    private var goalMl = 2500

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        _binding = FragmentRecordListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[RecordListViewModel::class.java]

        dayAdapter = DayRecordAdapter()
        monthAdapter = MonthSummaryAdapter(2500)
        binding.recyclerRecords.layoutManager = LinearLayoutManager(requireContext())

        setupTabLayout()
        setupDateNavigation()
        setupChartToggle()
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
                        binding.cardSummary.visibility = View.VISIBLE
                        binding.statisticsContainer.visibility = View.GONE
                    }
                    1 -> {
                        viewModel.switchToMonthView()
                        binding.recyclerRecords.visibility = View.VISIBLE
                        binding.chartContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.VISIBLE
                        binding.statisticsContainer.visibility = View.GONE
                    }
                    2 -> {
                        binding.recyclerRecords.visibility = View.GONE
                        binding.chartContainer.visibility = View.VISIBLE
                        binding.cardSummary.visibility = View.GONE
                        binding.statisticsContainer.visibility = View.GONE
                        viewModel.loadChartData()
                    }
                    3 -> {
                        binding.recyclerRecords.visibility = View.GONE
                        binding.chartContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.GONE
                        binding.statisticsContainer.visibility = View.VISIBLE
                        viewModel.loadStatistics()
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
    }

    private fun setupChartToggle() {
        binding.btnBarChart.setOnClickListener {
            isBarChartMode = true
            binding.barChart.visibility = View.VISIBLE
            binding.lineChart.visibility = View.GONE
            setChartButtonActive(binding.btnBarChart, binding.btnLineChart)
        }
        binding.btnLineChart.setOnClickListener {
            isBarChartMode = false
            binding.barChart.visibility = View.GONE
            binding.lineChart.visibility = View.VISIBLE
            setChartButtonActive(binding.btnLineChart, binding.btnBarChart)
        }
    }

    private fun setChartButtonActive(active: com.google.android.material.button.MaterialButton, inactive: com.google.android.material.button.MaterialButton) {
        active.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_primary))
        active.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_light)
        inactive.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        inactive.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.transparent)
    }

    private fun setupObservers() {
        binding.btnExportCsv.setOnClickListener {
            val records = viewModel.records.value ?: emptyList()
            if (records.isEmpty()) {
                Toast.makeText(requireContext(), "暂无记录可导出", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val typeNames = viewModel.typeNames.value ?: emptyMap()
                val uri = com.zhengui.waterreminder.util.CsvExporter.export(requireContext(), records, typeNames)
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "导出喝水记录"))
                    } else {
                        Toast.makeText(requireContext(), "导出失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewModel.currentDate.observe(viewLifecycleOwner) { cal -> updateDateDisplay(cal) }

        viewModel.isDayView.observe(viewLifecycleOwner) { isDayView ->
            binding.tvGoalStatus.visibility = if (isDayView) View.VISIBLE else View.GONE
            if (isDayView) {
                binding.recyclerRecords.adapter = dayAdapter
                binding.tvSummaryLabel.text = "当日总饮水量"
            } else {
                binding.recyclerRecords.adapter = monthAdapter
                binding.tvSummaryLabel.text = "当月总饮水量"
            }
        }

        viewModel.records.observe(viewLifecycleOwner) { records -> dayAdapter.submitList(records) }
        viewModel.monthlySummaries.observe(viewLifecycleOwner) { summaries -> monthAdapter.submitList(summaries) }

        viewModel.dailyTotal.observe(viewLifecycleOwner) { total ->
            binding.tvSummaryAmount.text = "${total} ml"
            updateGoalStatus(total, viewModel.dailyGoal.value ?: 2500)
        }

        viewModel.dailyGoal.observe(viewLifecycleOwner) { goal ->
            goalMl = goal
            monthAdapter = MonthSummaryAdapter(goal)
            if (viewModel.isDayView.value != true) {
                binding.recyclerRecords.adapter = monthAdapter
                viewModel.monthlySummaries.value?.let { monthAdapter.submitList(it) }
            }
            updateGoalStatus(viewModel.dailyTotal.value ?: 0, goal)
        }

        viewModel.chartData.observe(viewLifecycleOwner) { data ->
            setupBarChart(data)
            setupLineChart(data)
        }

        viewModel.statistics.observe(viewLifecycleOwner) { stats ->
            val content = binding.statisticsContent
            val emptyText = binding.tvStatEmpty
            content.removeAllViews()
            content.addView(emptyText)

            if (stats == null) {
                emptyText.visibility = View.VISIBLE
                return@observe
            }
            emptyText.visibility = View.GONE

            val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_primary)

            content.addView(createStatCard("本周日均饮水量", "${stats.dailyAverage} ml", blueColor))
            content.addView(createStatCard("喝水最多的一天", "${stats.maxDay ?: "-"}  ${stats.maxAmount}ml", blueColor))
            content.addView(createStatCard("喝水最少的一天", "${stats.minDay ?: "-"}  ${stats.minAmount}ml", blueColor))
            content.addView(createStatCard("最活跃时段", stats.peakPeriod, blueColor))
            content.addView(createStatCard("本周达标率", "${stats.achievedDays}/${stats.totalDays}天  ${(stats.achievementRate * 100).toInt()}%", blueColor))
        }
    }

    private fun updateDateDisplay(cal: Calendar) {
        binding.tvCurrentDate.text = if (viewModel.isDayView.value == true) dayFormat.format(cal.time) else monthFormat.format(cal.time)
    }

    private fun updateGoalStatus(total: Int, goal: Int) {
        if (total >= goal) {
            binding.tvGoalStatus.text = "已达标"
            binding.tvGoalStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_success))
        } else {
            binding.tvGoalStatus.text = "未达标(目标 ${goal}ml)"
            binding.tvGoalStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
    }

    // ===== Chart Styling =====

    private fun styleBarChart(chart: BarChart) {
        chart.apply {
            setBackgroundColor(Color.WHITE)
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setScaleEnabled(false)
            setDoubleTapToZoomEnabled(false)
            description.isEnabled = false
            legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            legend.textSize = 12f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.divider)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 11f
                granularity = 1f
                setAvoidFirstLastClipping(true)
            }

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.gray)
                gridLineWidth = 0.5f
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 11f
                setDrawZeroLine(true)
                zeroLineColor = ContextCompat.getColor(requireContext(), R.color.divider)
            }

            axisRight.isEnabled = false
        }
    }

    private fun styleLineChart(chart: LineChart) {
        chart.apply {
            setBackgroundColor(Color.WHITE)
            setDrawGridBackground(false)
            setPinchZoom(false)
            setScaleEnabled(false)
            setDoubleTapToZoomEnabled(false)
            description.isEnabled = false
            legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            legend.textSize = 12f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.divider)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 11f
                granularity = 1f
                setAvoidFirstLastClipping(true)
            }

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.gray)
                gridLineWidth = 0.5f
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 11f
            }

            axisRight.isEnabled = false
        }
    }

    private fun setupBarChart(summaries: List<com.zhengui.waterreminder.data.dao.WaterRecordDao.DailySummary>) {
        if (summaries.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.setNoDataText("暂无数据")
            return
        }

        styleBarChart(binding.barChart)

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        summaries.forEachIndexed { index, summary ->
            entries.add(BarEntry(index.toFloat(), summary.total.toFloat()))
            labels.add(summary.day.substring(5))
        }

        val goalLine = LimitLine(goalMl.toFloat(), "目标 ${goalMl}ml").apply {
            lineWidth = 1.5f
            lineColor = ContextCompat.getColor(requireContext(), R.color.green_success)
            textColor = ContextCompat.getColor(requireContext(), R.color.green_success)
            textSize = 11f
            enableDashedLine(8f, 4f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        binding.barChart.axisLeft.removeAllLimitLines()
        binding.barChart.axisLeft.addLimitLine(goalLine)

        val dataSet = BarDataSet(entries, "饮水量(ml)").apply {
            color = ContextCompat.getColor(requireContext(), R.color.blue_primary)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 10f
            setDrawValues(true)
            isHighlightEnabled = true
            highLightColor = ContextCompat.getColor(requireContext(), R.color.blue_primary)
            highLightAlpha = 40
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry?): String {
                    return if (barEntry != null && barEntry.y > 0) "${barEntry.y.toInt()}" else ""
                }
            }
        }

        binding.barChart.apply {
            visibility = View.VISIBLE
            data = BarData(dataSet)
            data.barWidth = 0.6f
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            barData.setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}"
            })
            setFitBars(true)
            animateY(600)
            invalidate()
        }

        if (isBarChartMode) {
            binding.barChart.visibility = View.VISIBLE
            binding.lineChart.visibility = View.GONE
        }
    }

    private fun setupLineChart(summaries: List<com.zhengui.waterreminder.data.dao.WaterRecordDao.DailySummary>) {
        if (summaries.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.setNoDataText("暂无数据")
            return
        }

        styleLineChart(binding.lineChart)

        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        summaries.forEachIndexed { index, summary ->
            entries.add(Entry(index.toFloat(), summary.total.toFloat()))
            labels.add(summary.day.substring(5))
        }

        val goalLine = LimitLine(goalMl.toFloat(), "目标 ${goalMl}ml").apply {
            lineWidth = 1.5f
            lineColor = ContextCompat.getColor(requireContext(), R.color.green_success)
            textColor = ContextCompat.getColor(requireContext(), R.color.green_success)
            textSize = 11f
            enableDashedLine(8f, 4f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        binding.lineChart.axisLeft.removeAllLimitLines()
        binding.lineChart.axisLeft.addLimitLine(goalLine)

        val dataSet = LineDataSet(entries, "饮水量(ml)").apply {
            color = ContextCompat.getColor(requireContext(), R.color.blue_primary)
            lineWidth = 2.5f
            circleRadius = 4f
            circleHoleRadius = 2.5f
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.blue_primary))
            circleHoleColor = Color.WHITE
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 10f
            setDrawValues(true)
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.chart_fill_blue)
            setDrawCircles(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawHighlightIndicators(true)
            highLightColor = ContextCompat.getColor(requireContext(), R.color.blue_primary)
            valueFormatter = object : ValueFormatter() {
                override fun getPointLabel(entry: Entry?): String {
                    return if (entry != null && entry.y > 0) "${entry.y.toInt()}" else ""
                }
            }
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            setVisibleXRangeMaximum(15f)
            moveViewToX(entries.size.toFloat())
            animateX(600)
            invalidate()
        }
    }

    private fun createStatCard(label: String, value: String, valueColor: Int): com.google.android.material.card.MaterialCardView {
        return com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
            cardElevation = 4.dpToPx().toFloat()
            radius = 12.dpToPx().toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background))

            val innerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            }

            val labelTv = TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            val valueTv = TextView(context).apply {
                text = value
                textSize = 18f
                setTextColor(valueColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4.dpToPx() }
            }

            innerLayout.addView(labelTv)
            innerLayout.addView(valueTv)
            addView(innerLayout)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
