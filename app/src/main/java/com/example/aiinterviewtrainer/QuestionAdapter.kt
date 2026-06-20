package com.example.aiinterviewtrainer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aiinterviewtrainer.contract.QuestionActivityContract
import com.example.aiinterviewtrainer.databinding.ItemQuestionBinding
import com.example.aiinterviewtrainer.model.InterviewQuestion // 🌟 파이어베이스 데이터 모델 경로

class QuestionAdapter(
    private val questionList: List<InterviewQuestion>,
    private val practiceId: String // 🌟 인텐트 이동에 필요한 고유 ID를 생성자에서 추가로 받습니다.
) : RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder>() {

    inner class QuestionViewHolder(val binding: ItemQuestionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InterviewQuestion, position: Int) {

            // 1. 질문 번호 및 내용 설정 (기존 로직 유지)
            binding.tvQuestionNumber.text = (position + 1).toString()
            binding.tvQuestionText.text = item.question

            // 2. 🌟 답변 기록 리스트 세팅 (기존 로직 유지)
            // 파이어베이스 구조에서는 아직 과거 기록 리스트를 담아오지 않으므로 임시로 빈 리스트를 주거나,
            // 추후 필요 시 item.historyList 형태로 매핑해서 사용하도록 뼈대를 유지합니다.
            val historyAdapter = AnswerHistoryAdapter(emptyList()) // 혹은 원래 쓰시던 HistoryData 리스트 연동
            binding.rvAnswerHistory.apply {
                adapter = historyAdapter
                layoutManager = LinearLayoutManager(binding.root.context)
            }

            // 3. 🌟 새 답변 작성하기 버튼 클릭 이벤트 (기존 로직 완벽 유지)
            binding.btnNewAnswer.setOnClickListener {
                val viewContext = binding.root.context

                // 기존에 정의해 두신 Contract와 매개변수를 그대로 연결합니다.
                val intent = QuestionActivityContract.createAnswerIntent(
                    context = viewContext,
                    practiceId = practiceId, // 생성자로부터 넘겨받은 ID 사용
                    selectedQuestion = item  // 파이어베이스에서 온 InterviewQuestion 객체 전달
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
}