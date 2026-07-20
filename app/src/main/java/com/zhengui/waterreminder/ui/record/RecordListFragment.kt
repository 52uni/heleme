package com.zhengui.waterreminder.ui.record

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.FragmentRecordListBinding
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
        setupObservers()
        viewModel.loadData()
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                if (tab.position == 0) viewModel.switchToDayView() else viewModel.switchToMonthView()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun setupDateNavigation() {
        binding.btnPrevDate.setOnClickListener { viewModel.prevDate() }
        binding.btnNextDate.setOnClickListener { viewModel.nextDate() }
    }

    private fun setupObservers() {
        viewModel.currentDate.observe(viewLifecycleOwner) { cal -> updateDateDisplay(cal) }

        viewModel.isDayView.observe(viewLifecycleOwner) { isDayView ->
            if (isDayView) {
                binding.recyclerRecords.adapter = dayAdapter
                binding.tvSummaryLabel.text = "当日总饮水量"
            } else {
                binding.recyclerRecords.adapter = monthAdapter
                binding.tvSummaryLabel.text = "当月总记录"
            }
        }

        viewModel.records.observe(viewLifecycleOwner) { records -> dayAdapter.submitList(records) }
        viewModel.monthlySummaries.observe(viewLifecycleOwner) { summaries -> monthAdapter.submitList(summaries) }

        viewModel.dailyTotal.observe(viewLifecycleOwner) { total ->
            binding.tvSummaryAmount.text = "${total} ml"
            updateGoalStatus(total, viewModel.dailyGoal.value ?: 2500)
        }

        viewModel.dailyGoal.observe(viewLifecycleOwner) { goal ->
            monthAdapter = MonthSummaryAdapter(goal)
            if (viewModel.isDayView.value != true) {
                binding.recyclerRecords.adapter = monthAdapter
                viewModel.monthlySummaries.value?.let { monthAdapter.submitList(it) }
            }
            updateGoalStatus(viewModel.dailyTotal.value ?: 0, goal)
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
