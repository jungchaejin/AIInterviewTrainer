package com.example.aiinterviewtrainer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aiinterviewtrainer.databinding.ItemHistoryBinding

class HistoryAdapter(private val onClick: (HistoryItem) -> Unit) :
    ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            // 레이아웃의 텍스트뷰에 데이터 꽂아주기
            binding.tvTitle.text = item.title
            binding.tvDate.text = item.date

            // 🌟 아이템 전체 뷰를 클릭했을 때의 동작
            binding.root.setOnClickListener {
                onClick(item) // 클릭된 아이템 정보를 HistoryActivity로 넘겨줌
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
    override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
        return oldItem == newItem
    }
}
