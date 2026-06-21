package com.example.aiinterviewtrainer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aiinterviewtrainer.contract.QuestionActivityContract
import com.example.aiinterviewtrainer.databinding.ItemQuestionBinding
import com.example.aiinterviewtrainer.model.InterviewQuestion // 🌟 파이어베이스 데이터 모델 경로
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuestionAdapter(
    private val questionList: List<InterviewQuestion>,
    private val practiceId: String // 🌟 인텐트 이동에 필요한 고유 ID를 생성자에서 추가로 받습니다.
) : RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder>() {

    inner class QuestionViewHolder(val binding: ItemQuestionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InterviewQuestion, position: Int) {

            // 1. 질문 번호 및 내용 설정 (기존 로직 유지)
            binding.tvQuestionNumber.text = (position + 1).toString()
            binding.tvQuestionText.text = item.question

            val questionId = "q${item.id}"
            val historyAdapter = AnswerHistoryAdapter(emptyList())
            binding.rvAnswerHistory.apply {
                adapter = historyAdapter
                layoutManager = LinearLayoutManager(binding.root.context)
            }
            loadAnswerHistory(questionId, historyAdapter)

            // 3. 🌟 새 답변 작성하기 버튼 클릭 이벤트 (기존 로직 완벽 유지)
            binding.btnNewAnswer.setOnClickListener {
                val viewContext = binding.root.context

                // 기존에 정의해 두신 Contract와 매개변수를 그대로 연결합니다.
                val intent = QuestionActivityContract.createAnswerIntent(
                    context = viewContext,
                    practiceId = practiceId,
                    questionId = questionId,
                    selectedQuestion = item
                )

                viewContext.startActivity(intent)
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

    override fun getItemCount(): Int = questionList.size

    private fun loadAnswerHistory(
        questionId: String,
        historyAdapter: AnswerHistoryAdapter
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
                val histories = result.documents.map { document ->
                    val createdAt = document.getLong("createdAt") ?: 0L
                    val dateText = document.getString("dateText")
                        ?: formatter.format(Date(createdAt))
                    HistoryData(
                        practiceId = practiceId,
                        questionId = questionId,
                        answerId = document.id,
                        dateText = "$dateText 답변 기록 보기"
                    )
                }
                historyAdapter.submitList(histories)
            }
    }
}
