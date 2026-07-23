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
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.data.entity.WaterRecord
import com.zhengui.waterreminder.databinding.FragmentHomeBinding
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.service.WaterReminderService
import com.zhengui.waterreminder.ui.MainViewModel
import com.zhengui.waterreminder.widget.WidgetUpdateHelper
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
        setupRecordChips()

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

        viewModel.dailyRecords.observe(viewLifecycleOwner) { records ->
            val adapter = binding.rvTodayRecords.adapter as? RecordChipAdapter
            if (records.isEmpty()) {
                binding.rvTodayRecords.visibility = View.GONE
            } else {
                binding.rvTodayRecords.visibility = View.VISIBLE
                adapter?.submitList(records)
            }
        }
    }

    private fun setupListeners() {
        binding.btnCheckIn.setOnClickListener { showCheckInDialog() }
        binding.layoutPersonType.setOnClickListener { showPersonTypeSelector() }
    }

    private fun setupRecordChips() {
        binding.rvTodayRecords.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvTodayRecords.adapter = RecordChipAdapter()
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
                WidgetUpdateHelper.updateAllWidgets(requireContext())
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
            WidgetUpdateHelper.updateAllWidgets(requireContext())
            bottomSheet.dismiss()
        }

        btnConfirm.setOnClickListener {
            val amount = etCustomAmount.text.toString().toIntOrNull()
            if (amount != null && amount > 0) {
                viewModel.customDrink(amount)
                WidgetUpdateHelper.updateAllWidgets(requireContext())
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
                val oldTypeId = WaterReminderService.getCurrentTypeId(requireContext())
                WaterReminderService.setCurrentTypeId(requireContext(), type.id)
                viewModel.refreshData()
                // 切换不同类型时，清除上次喝水时间并从现在按新间隔调度
                if (oldTypeId != type.id && WaterReminderService.isReminderEnabled(requireContext())) {
                    ReminderScheduler.setLastDrinkTime(requireContext(), 0L)
                    ReminderScheduler.cancelReminder(requireContext())
                    ReminderScheduler.cancelSmallCycle(requireContext())
                    ReminderScheduler.scheduleNextReminder(requireContext())
                }
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

    private val cheerGifResIds = intArrayOf(
        R.raw.cheer_gif_1,
        R.raw.cheer_gif_2,
        R.raw.cheer_gif_3,
        R.raw.cheer_gif_4,
        R.raw.cheer_gif_5
    )

    private val cheerTexts = arrayOf(
        "你真棒啊！",
        "又漂亮啦~",
        "越来越好看啦",
        "好厉害呀！",
        "你最棒啦",
        "真不错呢",
        "闪闪发光！",
        "太优秀啦",
        "元气满满哦",
        "状态满分！",
        "好样的~",
        "每天都有进步",
        "比昨天更好啦",
        "超赞的！",
        "太可爱了吧",
        "今天也在发光"
    )

    private fun showEncouragementDialog(@Suppress("UNUSED_PARAMETER") amount: Int) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_encouragement, null)
        val ivGif = dialogView.findViewById<ImageView>(R.id.ivCheerGif)

        val gifResId = cheerGifResIds.random()
        Glide.with(this)
            .asGif()
            .load(gifResId)
            .override(180.dpToPx(), 180.dpToPx())
            .centerInside()
            .into(ivGif)

        // 鼓励文案
        dialogView.findViewById<TextView>(R.id.tvEncouragementText).text = cheerTexts.random()

        // 弹入动画
        ivGif.scaleX = 0f
        ivGif.scaleY = 0f
        ivGif.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()

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
        }, 2000)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

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
        // 补调度：App 被杀后重新打开时，如果提醒已开启则重新调度闹钟
        if (WaterReminderService.isReminderEnabled(requireContext())) {
            ReminderScheduler.scheduleNextReminder(requireContext())
            ReminderScheduler.scheduleAllReminders(requireContext())
        }
    }

    override fun onDestroyView() {
        numberAnimator?.cancel()
        _binding = null
        super.onDestroyView()
    }

    // --- 今日饮水记录小块适配器 ---

    inner class RecordChipAdapter :
        RecyclerView.Adapter<RecordChipAdapter.VH>() {

        private var items: List<WaterRecord> = emptyList()

        fun submitList(list: List<WaterRecord>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drink_record_chip, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val record = items[position]
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = record.drinkTime }
            val time = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            holder.tv.text = "$time\n${record.amountMl}ml"

            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("删除记录")
                    .setMessage("确定删除 $time 的 ${record.amountMl}ml 喝水记录吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteRecord(record)
                    }
                    .show()
                true
            }
        }

        override fun getItemCount(): Int = items.size

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tv: TextView = itemView.findViewById(R.id.tvRecordChip)
        }
    }
}
