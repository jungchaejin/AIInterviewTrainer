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

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupWebView()
        binding.btnGenerateQuestion.setOnClickListener {
            val url = binding.etJobUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                setLoadingState(true)
                isGenerating = true
                binding.hiddenWebview.loadUrl(url)
            } else {
                Toast.makeText(this, R.string.url_required, Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(
                                this@JobSelectActivity,
                                R.string.text_extract_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun callApiAndNavigate(extractedJdText: String) {
        lifecycleScope.launch {
            try {
                val generatedInterview = InterviewQuestionRepository.generateInterview(
                    context = this@JobSelectActivity,
                    jdText = extractedJdText
                )
                val questions = generatedInterview.questions
                if (generatedInterview.isFallback) {
                    Toast.makeText(
                        this@JobSelectActivity,
                        getString(R.string.fallback_questions_notice),
                        Toast.LENGTH_LONG
                    ).show()
                }

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
                            getString(R.string.question_save_failed, exception.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )

            } catch (e: Exception) {
                setLoadingState(false)
                val message = if (e is SocketTimeoutException) {
                    getString(R.string.request_timeout)
                } else {
                    getString(
                        R.string.question_generation_failed,
                        e.message ?: getString(R.string.unknown_error)
                    )
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
