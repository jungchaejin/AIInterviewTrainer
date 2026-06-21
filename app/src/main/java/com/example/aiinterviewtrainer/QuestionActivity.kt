package com.example.aiinterviewtrainer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aiinterviewtrainer.databinding.ActivityQuestionBinding
import com.example.aiinterviewtrainer.model.InterviewQuestion
import com.google.firebase.firestore.FirebaseFirestore

class QuestionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuestionBinding
    private lateinit var questionAdapter: QuestionAdapter
    private val firestore = FirebaseFirestore.getInstance()

    private var practiceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindAppHomeTitle()

        practiceId = intent.getStringExtra(AnswerActivity.EXTRA_PRACTICE_ID)

        setupUI()
        setupRecyclerView()

        if (!practiceId.isNullOrEmpty()) {
            loadQuestionsFromFirebase(practiceId!!)
        } else {
            Toast.makeText(this, "오류: 면접 기록을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        questionAdapter = QuestionAdapter(emptyList(), "")

        binding.rvQuestions.apply {
            adapter = questionAdapter
            layoutManager = LinearLayoutManager(this@QuestionActivity)
        }
    }

    private fun loadQuestionsFromFirebase(id: String) {
        firestore.collection("History").document(id).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val jobTitle = document.getString("jobTitle") ?: "면접 연습"
                    binding.tvTitle.text = jobTitle
                }
            }

        firestore.collection("History")
            .document(id)
            .collection("Questions")
            .get()
            .addOnSuccessListener { result ->
                val questionList = mutableListOf<InterviewQuestion>()

                val documents = result.documents.sortedBy { document ->
                    document.getLong("order")?.toInt()
                        ?: document.id.removePrefix("q").toIntOrNull()
                        ?: Int.MAX_VALUE
                }

                for (document in documents) {
                    val text = document.getString("questionText") ?: ""
                    val keywordList = readStringList(document.get("keywords"))
                    val evaluationPoints = readStringList(document.get("evaluationPoints"))
                    val questionOrder = document.getLong("order")?.toInt()
                        ?: document.id.removePrefix("q").toIntOrNull()
                        ?: questionList.size

                    questionList.add(
                        InterviewQuestion(
                            id = questionOrder,
                            question = text,
                            questionType = document.getString("questionType") ?: "직무역량",
                            expectedKeywords = keywordList,
                            evaluationPoints = evaluationPoints
                        )
                    )
                }

                questionAdapter = QuestionAdapter(questionList, id)
                binding.rvQuestions.adapter = questionAdapter
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "질문을 불러오는데 실패했습니다: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun readStringList(value: Any?): List<String> {
        return when (value) {
            is String -> value
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }
}
