package com.zhengui.waterreminder.ui.home

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.FragmentHomeBinding
import com.zhengui.waterreminder.service.WaterReminderService
import com.zhengui.waterreminder.ui.MainViewModel
import com.zhengui.waterreminder.ui.persontype.PersonTypeAdapter
import com.zhengui.waterreminder.ui.persontype.PersonTypeEditActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private var numberAnimator: ValueAnimator? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        viewModel.initPresetTypesIfNeeded()
        setupObservers()
        setupListeners()

        if (activity?.intent?.getBooleanExtra("show_quick_drink", false) == true) {
            showCustomDrinkDialog()
            activity?.intent?.removeExtra("show_quick_drink")
        }
    }

    private fun setupObservers() {
        viewModel.dailyTotal.observe(viewLifecycleOwner) { total ->
            val oldTotal = binding.tvCurrentAmount.text.toString().toIntOrNull() ?: 0
            if (oldTotal != total) {
                animateNumberChange(oldTotal, total)
            }
            val goal = viewModel.dailyGoal.value ?: 2500
            val progress = if (goal > 0) (total * 100 / goal) else 0
            binding.waveProgress.setProgress(progress.coerceAtMost(100))
            binding.tvGoalReached.visibility = if (total >= goal) View.VISIBLE else View.GONE
        }

        viewModel.dailyGoal.observe(viewLifecycleOwner) { goal ->
            binding.tvGoalAmount.text = goal.toString()
            val total = viewModel.dailyTotal.value ?: 0
            val progress = if (goal > 0) (total * 100 / goal) else 0
            binding.waveProgress.setProgress(progress.coerceAtMost(100))
        }

        viewModel.currentType.observe(viewLifecycleOwner) { type ->
            binding.tvCurrentType.text = type?.name ?: "未选择"
        }

        viewModel.streakDays.observe(viewLifecycleOwner) { days ->
            binding.tvStreakDays.text = "连续达标 ${days} 天"
        }
    }

    private fun setupListeners() {
        binding.btnCheckIn.setOnClickListener { showCheckInDialog() }
        binding.layoutPersonType.setOnClickListener { showPersonTypeSelector() }
    }

    private fun showCheckInDialog() {
        val bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_custom_drink, null)
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.setContentView(bottomSheetView)

        val chipGroup = bottomSheetView.findViewById<ChipGroup>(R.id.chipGroupAmounts)
        val etCustomAmount = bottomSheetView.findViewById<TextInputEditText>(R.id.etCustomAmount)
        val btnConfirm = bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmDrink)

        // 设置默认喝水量
        val defaultAmount = viewModel.currentType.value?.defaultAmountMl ?: 200
        etCustomAmount.setText(defaultAmount.toString())

        val chipAmounts = mapOf(
            R.id.chip150 to 150,
            R.id.chip200 to 200,
            R.id.chip250 to 250,
            R.id.chip500 to 500
        )

        // 点击快捷选择时更新输入框
        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            val amount = chipAmounts[checkedId]
            if (amount != null) {
                etCustomAmount.setText(amount.toString())
            }
        }

        btnConfirm.setOnClickListener {
            val amount = etCustomAmount.text.toString().toIntOrNull()
            if (amount != null && amount > 0) {
                viewModel.customDrink(amount)
                bottomSheet.dismiss()
                // 打卡成功后显示鼓励弹窗
                showEncouragementDialog(amount)
            } else {
                Toast.makeText(requireContext(), "请输入有效饮水量", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheet.show()
    }

    private fun showCustomDrinkDialog() {
        val bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_custom_drink, null)
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.setContentView(bottomSheetView)

        val chipGroup = bottomSheetView.findViewById<ChipGroup>(R.id.chipGroupAmounts)
        val etCustomAmount = bottomSheetView.findViewById<TextInputEditText>(R.id.etCustomAmount)
        val btnConfirm = bottomSheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmDrink)

        etCustomAmount.setText(viewModel.currentType.value?.defaultAmountMl?.toString() ?: "200")

        val chipAmounts = mapOf(
            R.id.chip150 to 150,
            R.id.chip200 to 200,
            R.id.chip250 to 250,
            R.id.chip500 to 500
        )

        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            val amount = chipAmounts[checkedId] ?: 200
            viewModel.customDrink(amount)
            bottomSheet.dismiss()
        }

        btnConfirm.setOnClickListener {
            val amount = etCustomAmount.text.toString().toIntOrNull()
            if (amount != null && amount > 0) {
                viewModel.customDrink(amount)
                bottomSheet.dismiss()
            } else {
                Toast.makeText(requireContext(), "请输入有效饮水量", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheet.show()
    }

    private fun showPersonTypeSelector() {
        val bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_person_type_selector, null)
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.setContentView(bottomSheetView)

        val recyclerView = bottomSheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerTypes)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val adapter = PersonTypeAdapter(
            currentTypeId = WaterReminderService.getCurrentTypeId(requireContext()),
            onSelect = { type ->
                WaterReminderService.setCurrentTypeId(requireContext(), type.id)
                viewModel.refreshData()
                bottomSheet.dismiss()
            },
            onEdit = { type ->
                bottomSheet.dismiss()
                startActivity(Intent(requireContext(), PersonTypeEditActivity::class.java).apply {
                    putExtra("person_type_id", type.id)
                })
            },
            onDelete = { type ->
                viewModel.deletePersonType(type)
                bottomSheet.dismiss()
            }
        )
        recyclerView.adapter = adapter

        viewModel.allPersonTypes.observe(viewLifecycleOwner) { types ->
            adapter.submitList(types)
            adapter.updateCurrentTypeId(WaterReminderService.getCurrentTypeId(requireContext()))
        }

        bottomSheet.show()
    }

    private fun showEncouragementDialog(amount: Int) {
        val encouragements = listOf("完成一杯", "继续保持", "今天状态不错", "补水进度更新", "离目标更近了")
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_encouragement, null)
        dialogView.findViewById<TextView>(R.id.tvEncouragementText).text = encouragements.random()
        dialogView.findViewById<TextView>(R.id.tvAmount).text = "本次饮水 ${amount}ml"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.setCancelable(false)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) dialog.dismiss()
        }, 1500)
    }

    private fun animateNumberChange(from: Int, to: Int) {
        numberAnimator?.cancel()
        numberAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = 600
            addUpdateListener { animation ->
                if (_binding != null) {
                    binding.tvCurrentAmount.text = (animation.animatedValue as Int).toString()
                }
            }
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    override fun onDestroyView() {
        numberAnimator?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
