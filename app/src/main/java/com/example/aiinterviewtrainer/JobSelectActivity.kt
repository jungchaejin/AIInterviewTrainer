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
import com.example.aiinterviewtrainer.network.InterviewQuestionRepository
import com.example.aiinterviewtrainer.model.InterviewQuestion
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JobSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobSelectBinding
    private var isGenerating = false // 웹뷰 로딩 감시 플래그

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                // 1. API 호출로 질문 5개 받아오기
                val questions = InterviewQuestionRepository.getQuestions(
                    context = this@JobSelectActivity,
                    jdText = extractedJdText
                )

                // 2. 고유 ID 및 메타데이터 생성
                val practiceId = System.currentTimeMillis().toString()
                val jobTitle = "새로운 면접 연습"
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // 3. 🌟 파이어베이스에 저장 실행 (백그라운드에서 알아서 처리됨)
                saveToFirebase(practiceId, jobTitle, currentDate, questions)

                // 로딩 끄기
                setLoadingState(false)

                // 4. 생성된 고유 ID만 Intent에 실어서 QuestionActivity 호출
                val intent = Intent(this@JobSelectActivity, QuestionActivity::class.java).apply {
                    putExtra("EXTRA_PRACTICE_ID", practiceId)
                }
                startActivity(intent)

                // 현재 화면 종료
                finish()

            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@JobSelectActivity, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToFirebase(practiceId: String, jobTitle: String, date: String, questions: List<InterviewQuestion>) {
        val firestore = FirebaseFirestore.getInstance()

        // 1) 상위 문서(History) 생성
        val historyMap = hashMapOf(
            "practiceId" to practiceId,
            "jobTitle" to jobTitle,
            "practiceDate" to date
        )

        // 상위 문서 저장 후, 2) 하위 컬렉션(Questions)에 질문 5개 저장
        firestore.collection("History").document(practiceId).set(historyMap)
            .addOnSuccessListener {
                questions.forEachIndexed { index, q ->
                    val questionMap = hashMapOf(
                        "questionText" to q.question,
                        "keywords" to q.expectedKeywords,
                        "userAnswer" to "",
                        "feedback" to ""
                    )

                    firestore.collection("History")
                        .document(practiceId)
                        .collection("Questions")
                        .document("q$index")
                        .set(questionMap)
                }
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            binding.loadingOverlay.visibility = View.VISIBLE
        } else {
            binding.loadingOverlay.visibility = View.GONE
        }
    }
}