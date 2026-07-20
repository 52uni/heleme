package com.zhengui.waterreminder.ui.record

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zhengui.waterreminder.data.entity.WaterRecord
import com.zhengui.waterreminder.databinding.ItemWaterRecordBinding
import java.text.SimpleDateFormat
import java.util.Locale

class DayRecordAdapter(
    private val typeNames: Map<Long, String> = emptyMap()
) : RecyclerView.Adapter<DayRecordAdapter.ViewHolder>() {

    private var items: List<WaterRecord> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<WaterRecord>) {
        items = list
        notifyDataSetChanged()
    }

    @Suppress("UNUSED_PARAMETER")
    fun updateTypeNames(names: Map<Long, String>) {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWaterRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun getItemAt(position: Int): WaterRecord = items[position]

    inner class ViewHolder(private val binding: ItemWaterRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: WaterRecord) {
            binding.tvTime.text = timeFormat.format(record.drinkTime)
            binding.tvAmount.text = "${record.amountMl} ml"
            binding.tvTypeName.text = record.personTypeId?.let { typeNames[it] } ?: ""
        }
    }
}
