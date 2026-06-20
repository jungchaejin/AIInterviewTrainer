package com.example.aiinterviewtrainer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aiinterviewtrainer.databinding.ActivityQuestionBinding
import com.example.aiinterviewtrainer.model.InterviewQuestion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class QuestionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuestionBinding
    private lateinit var questionAdapter: QuestionAdapter
    private val firestore = FirebaseFirestore.getInstance()

    // 이 화면의 핵심 키가 될 연습 ID
    private var practiceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 인텐트로 넘어온 고유 ID 받기
        practiceId = intent.getStringExtra("EXTRA_PRACTICE_ID")

        setupUI()
        setupRecyclerView()

        // 2. ID가 정상적으로 있으면 파이어베이스에서 질문 데이터 원격 로드
        if (!practiceId.isNullOrEmpty()) {
            loadQuestionsFromFirebase(practiceId!!)
        } else {
            Toast.makeText(this, "오류: 면접 기록 ID를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        // 🌟 아직 ID가 없으므로 빈 리스트와 빈 문자열("")을 넘겨서 임시 초기화합니다.
        questionAdapter = QuestionAdapter(emptyList(), "")

        binding.rvQuestions.apply {
            adapter = questionAdapter
            layoutManager = LinearLayoutManager(this@QuestionActivity)
        }
    }

    /**
     * 🌟 파이어베이스 서브 컬렉션에서 질문 5개를 가져오는 핵심 함수
     */
    private fun loadQuestionsFromFirebase(id: String) {
        // [A] 먼저 상위 문서에서 직무 제목(jobTitle)을 가져와 타이틀 바에 꽂아줍니다.
        firestore.collection("History").document(id).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val jobTitle = document.getString("jobTitle") ?: "면접 연습"
                    binding.tvTitle.text = jobTitle
                }
            }

        // [B] 서브 컬렉션 'Questions'에 접근해서 q0, q1, q2... 순서대로 가져옵니다.
        firestore.collection("History")
            .document(id)
            .collection("Questions")
            .get()
            .addOnSuccessListener { result ->
                val questionList = mutableListOf<InterviewQuestion>()

                for (document in result) {
                    val text = document.getString("questionText") ?: ""
                    val keywords = document.getString("keywords") ?: ""

                    // 만약 예상 키워드가 문자열로 저장되어 있다면 대괄호나 콤마를 제거하고 리스트화해줍니다.
                    val keywordList = keywords.replace("[", "").replace("]", "")
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    // 우리가 정의한 네트워크 데이터 모델(InterviewQuestion) 형식으로 바인딩
                    // 🌟 모든 필수 파라미터에 값을 채워줍니다.
                    questionList.add(
                        InterviewQuestion(
                            id = 0,                                 // id 기본값
                            question = text,
                            questionType = "직무역량",               // 혹은 빈 문자열 ""
                            expectedKeywords = keywordList,
                            evaluationPoints = emptyList()          // 빈 리스트 전달
                        )
                    )
                }

                // [C] 🌟 어댑터에 파이어베이스에서 읽어온 진짜 질문 5개를 넣어 화면에 뿌려줍니다!
                // 기존 어댑터에 데이터 리스트를 업데이트하는 함수가 있다면 그걸 호출하셔도 됩니다.
                // 기존: questionAdapter = QuestionAdapter(questionList)
// 🌟 변경: 파이어베이스에서 읽어온 진짜 질문 리스트와, 상단에서 추출한 practiceId를 함께 전달합니다!
                questionAdapter = QuestionAdapter(questionList, id)
                binding.rvQuestions.adapter = questionAdapter
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "질문을 불러오는데 실패했습니다: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}