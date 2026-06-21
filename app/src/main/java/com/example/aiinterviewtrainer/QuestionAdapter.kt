package com.example.aiinterviewtrainer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aiinterviewtrainer.contract.QuestionActivityContract
import com.example.aiinterviewtrainer.databinding.ItemQuestionBinding
import com.example.aiinterviewtrainer.model.InterviewQuestion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuestionAdapter(
    private val questionList: List<InterviewQuestion>,
    private val practiceId: String
) : RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder>() {

    inner class QuestionViewHolder(private val binding: ItemQuestionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InterviewQuestion, position: Int) {
            binding.tvQuestionNumber.text = binding.root.context.getString(
                R.string.question_number_format,
                position + 1
            )
            binding.tvQuestionText.text = item.question

            val questionId = "q${item.id}"
            val historyAdapter = AnswerHistoryAdapter(emptyList())
            binding.rvAnswerHistory.adapter = historyAdapter
            binding.rvAnswerHistory.layoutManager = LinearLayoutManager(binding.root.context)
            loadAnswerHistory(questionId, historyAdapter, binding.root.context)

            binding.btnNewAnswer.setOnClickListener {
                val context = binding.root.context
                context.startActivity(
                    QuestionActivityContract.createAnswerIntent(
                        context = context,
                        practiceId = practiceId,
                        questionId = questionId,
                        selectedQuestion = item
                    )
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val binding = ItemQuestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(questionList[position], position)
    }

    override fun getItemCount() = questionList.size

    private fun loadAnswerHistory(
        questionId: String,
        adapter: AnswerHistoryAdapter,
        context: android.content.Context
    ) {
        FirebaseFirestore.getInstance()
            .collection("History")
            .document(practiceId)
            .collection("Questions")
            .document(questionId)
            .collection("Answers")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val formatter = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                adapter.submitList(result.documents.map { document ->
                    val createdAt = document.getLong("createdAt") ?: 0L
                    val date = document.getString("dateText") ?: formatter.format(Date(createdAt))
                    HistoryData(
                        practiceId = practiceId,
                        questionId = questionId,
                        answerId = document.id,
                        dateText = context.getString(R.string.answer_history_format, date)
                    )
                })
            }
    }
}
