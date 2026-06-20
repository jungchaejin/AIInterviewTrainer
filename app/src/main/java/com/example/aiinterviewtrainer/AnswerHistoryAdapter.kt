package com.example.aiinterviewtrainer

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aiinterviewtrainer.databinding.ItemAnswerHistoryBinding

class AnswerHistoryAdapter(
    private val historyList: List<HistoryData>
) : RecyclerView.Adapter<AnswerHistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(val binding: ItemAnswerHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(history: HistoryData) {
            binding.tvHistoryDate.text = history.dateText

            binding.root.setOnClickListener {
                val context = binding.root.context
                val intent = Intent(context, ResultActivity::class.java).apply {
                    putExtra(AnswerActivity.EXTRA_PRACTICE_ID, history.practiceId)
                }
                context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemAnswerHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position])
    }

    override fun getItemCount(): Int = historyList.size
}