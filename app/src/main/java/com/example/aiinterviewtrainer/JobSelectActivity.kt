package com.example.aiinterviewtrainer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.aiinterviewtrainer.databinding.ActivityJobSelectBinding
import com.example.aiinterviewtrainer.model.InterviewQuestion
import com.example.aiinterviewtrainer.repository.InterviewQuestionRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.net.SocketTimeoutException
import java.util.Date
import java.util.Locale

class JobSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobSelectBinding
    private var isGenerating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindAppHomeTitle()

        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 웹뷰 초기 설정
        setupWebView()

        // 면접 질문 생성 버튼 클릭
        binding.btnGenerateQuestion.setOnClickListener {
            val url = binding.etJobUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                setLoadingState(true)
                isGenerating = true
                binding.hiddenWebview.loadUrl(url)
            } else {
                Toast.makeText(this, "URL을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWebView() {
        binding.hiddenWebview.settings.javaScriptEnabled = true
        binding.hiddenWebview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (isGenerating) {
                    isGenerating = false

                    view?.evaluateJavascript("document.body.innerText") { htmlText ->
                        val extractedJdText = htmlText?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""

                        if (extractedJdText.isNotBlank() && extractedJdText != "null") {
                            callApiAndNavigate(extractedJdText)
                        } else {
                            setLoadingState(false)
                            Toast.makeText(this@JobSelectActivity, "텍스트를 추출하지 못했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun callApiAndNavigate(extractedJdText: String) {
        lifecycleScope.launch {
            try {
                // API 호출로 질문 5개 받아오기
                val generatedInterview = InterviewQuestionRepository.generateInterview(
                    context = this@JobSelectActivity,
                    jdText = extractedJdText
                )
                val questions = generatedInterview.questions
                if (generatedInterview.isFallback) {
                    Toast.makeText(
                        this@JobSelectActivity,
                        "인터넷에 연결할 수 없어 기본 면접 질문 5개로 진행합니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // 고유 ID 및 메타데이터 생성
                val practiceId = System.currentTimeMillis().toString()
                val jobTitle = generatedInterview.practiceTitle
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                saveToFirebase(
                    practiceId = practiceId,
                    jobTitle = jobTitle,
                    date = currentDate,
                    questions = questions,
                    onSuccess = {
                        setLoadingState(false)
                        val intent = Intent(
                            this@JobSelectActivity,
                            QuestionActivity::class.java
                        ).apply {
                            putExtra(AnswerActivity.EXTRA_PRACTICE_ID, practiceId)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onFailure = { exception ->
                        setLoadingState(false)
                        Toast.makeText(
                            this@JobSelectActivity,
                            "질문 저장 실패: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )

            } catch (e: Exception) {
                setLoadingState(false)
                val message = if (e is SocketTimeoutException) {
                    "응답 시간이 초과되었습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요."
                } else {
                    "질문 생성 실패: ${e.message ?: "알 수 없는 오류"}"
                }
                Toast.makeText(this@JobSelectActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveToFirebase(
        practiceId: String,
        jobTitle: String,
        date: String,
        questions: List<InterviewQuestion>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()
        val historyReference = firestore.collection("History").document(practiceId)
        val batch = firestore.batch()

        // 상위 문서(History) 생성
        val historyMap = hashMapOf(
            "practiceId" to practiceId,
            "jobTitle" to jobTitle,
            "practiceDate" to date
        )

        batch.set(historyReference, historyMap)
        questions.forEachIndexed { index, question ->
            val questionMap = hashMapOf(
                "questionText" to question.question,
                "questionType" to question.questionType,
                "keywords" to question.expectedKeywords.joinToString(", "),
                "evaluationPoints" to question.evaluationPoints,
                "order" to index,
                "userAnswer" to "",
                "feedback" to ""
            )
            batch.set(
                historyReference.collection("Questions").document("q$index"),
                questionMap
            )
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception -> onFailure(exception) }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            binding.loadingOverlay.visibility = View.VISIBLE
        } else {
            binding.loadingOverlay.visibility = View.GONE
        }
    }
}
