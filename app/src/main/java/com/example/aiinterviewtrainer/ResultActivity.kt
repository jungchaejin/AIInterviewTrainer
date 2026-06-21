package com.example.aiinterviewtrainer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aiinterviewtrainer.analysis.AnswerFeatureAnalysis
import com.example.aiinterviewtrainer.analysis.FeatureExtractor
import com.example.aiinterviewtrainer.analysis.StarAnalysisResult
import com.example.aiinterviewtrainer.analysis.StarItemAnalysis
import com.example.aiinterviewtrainer.repository.AnswerFeedbackRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ResultActivity : AppCompatActivity() {
    private lateinit var analysis: AnswerAnalysis
    private var isFeedbackLoading = false
    private var isHistoricalResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        bindAppHomeTitle()

        val practiceId = intent.getStringExtra(AnswerActivity.EXTRA_PRACTICE_ID).orEmpty().ifBlank {
            System.currentTimeMillis().toString()
        }
        val questionId = intent.getStringExtra(AnswerActivity.EXTRA_QUESTION_ID).orEmpty()
        val answerId = intent.getStringExtra(EXTRA_ANSWER_ID).orEmpty()

        if (questionId.isNotBlank() && answerId.isNotBlank()) {
            isHistoricalResult = true
            findViewById<android.widget.ImageView>(R.id.backTextView).setOnClickListener { finish() }
            findViewById<TextView>(R.id.feedbackTextView).setText(R.string.saved_result_loading)
            loadSavedAnalysis(practiceId, questionId, answerId)
            return
        }

        val question = intent.getStringExtra(AnswerActivity.EXTRA_QUESTION).orEmpty()
        val questionType = intent.getStringExtra(AnswerActivity.EXTRA_QUESTION_TYPE).orEmpty()
        val expectedKeywords = intent.getStringArrayListExtra(AnswerActivity.EXTRA_EXPECTED_KEYWORDS).orEmpty()
        val evaluationPoints = intent.getStringArrayListExtra(AnswerActivity.EXTRA_EVALUATION_POINTS).orEmpty()
        val answer = intent.getStringExtra(AnswerActivity.EXTRA_ANSWER).orEmpty()
        val answerSeconds = intent.getIntExtra(AnswerActivity.EXTRA_ANSWER_SECONDS, 0)

        analysis = createTemporaryAnalysis(
            practiceId = practiceId,
            questionId = questionId,
            question = question,
            questionType = questionType,
            expectedKeywords = expectedKeywords,
            evaluationPoints = evaluationPoints,
            answer = answer,
            answerSeconds = answerSeconds
        )
        bindResult(analysis)
        bindButtons()
        loadGeminiFeedback()
    }

    private fun bindResult(analysis: AnswerAnalysis) {
        findViewById<android.widget.ImageView>(R.id.backTextView).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.questionResultTextView).text = analysis.question
        findViewById<TextView>(R.id.answerResultTextView).text = analysis.answer
        findViewById<TextView>(R.id.lengthTextView).text = String.format(
            Locale.getDefault(),
            "%d",
            analysis.answerLength
        )
        findViewById<TextView>(R.id.timeTextView).text = String.format(
            Locale.getDefault(),
            "%d",
            analysis.answerSeconds
        )

        bindKeywords(analysis)
        bindStarStatus(R.id.situationStatusTextView, R.id.situationBadge, analysis.situationStatus)
        bindStarStatus(R.id.taskStatusTextView, R.id.taskBadge, analysis.taskStatus)
        bindStarStatus(R.id.actionStatusTextView, R.id.actionBadge, analysis.actionStatus)
        bindStarStatus(R.id.resultStatusTextView, R.id.resultBadge, analysis.resultStatus)
        findViewById<TextView>(R.id.qualityGradeTextView).text = analysis.mlPrediction.grade
        findViewById<TextView>(R.id.qualityConfidenceTextView).text =
            getString(
                R.string.prediction_confidence_format,
                (analysis.mlPrediction.confidence * 100).roundToInt()
            )
        findViewById<TextView>(R.id.feedbackTextView).text = analysis.feedback
    }

    private fun bindKeywords(analysis: AnswerAnalysis) {
        val keywordFlowLayout = findViewById<KeywordFlowLayout>(R.id.keywordFlowLayout)
        keywordFlowLayout.removeAllViews()

        val includedKeywords = analysis.includedKeywords.map { it.trim().lowercase() }.toSet()
        val keywords = (
            analysis.expectedKeywords + analysis.includedKeywords + analysis.missingKeywords
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        keywords.forEach { keyword ->
            val isIncluded = keyword.lowercase() in includedKeywords
            keywordFlowLayout.addView(createKeywordChip(keyword, isIncluded))
        }
    }

    private fun createKeywordChip(
        keyword: String,
        isIncluded: Boolean
    ): TextView {
        return TextView(this).apply {
            text = keyword
            textSize = 12f
            maxLines = 2
            maxWidth = resources.displayMetrics.widthPixels - dp(80)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setBackgroundResource(if (isIncluded) R.drawable.bg_chip_blue else R.drawable.bg_chip_gray)
            setTextColor(
                ContextCompat.getColor(
                    this@ResultActivity,
                    if (isIncluded) R.color.main_blue else R.color.keyword_missing_text
                )
            )
        }
    }

    private fun bindStarStatus(statusViewId: Int, badgeViewId: Int, rawStatus: String) {
        val status = when (rawStatus) {
            "충분", "포함" -> "포함"
            "일부 포함" -> "일부 포함"
            else -> "부족"
        }
        val statusBackground: Int
        val badgeBackground: Int
        val textColor: Int

        when (status) {
            "포함" -> {
                statusBackground = R.drawable.bg_status_green
                badgeBackground = R.drawable.bg_star_circle_green
                textColor = R.color.star_green_text
            }
            "일부 포함" -> {
                statusBackground = R.drawable.bg_status_yellow
                badgeBackground = R.drawable.bg_star_circle_yellow
                textColor = R.color.star_yellow_text
            }
            else -> {
                statusBackground = R.drawable.bg_status_red
                badgeBackground = R.drawable.bg_star_circle_red
                textColor = R.color.star_red_text
            }
        }

        findViewById<TextView>(statusViewId).apply {
            text = status
            setBackgroundResource(statusBackground)
            setTextColor(ContextCompat.getColor(this@ResultActivity, textColor))
        }
        findViewById<TextView>(badgeViewId).apply {
            setBackgroundResource(badgeBackground)
            setTextColor(ContextCompat.getColor(this@ResultActivity, textColor))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun bindButtons() {
        val saveButton = findViewById<TextView>(R.id.saveButton)
        if (isHistoricalResult) {
            saveButton.isEnabled = false
            saveButton.text = "저장된 기록입니다"
        }
        saveButton.setOnClickListener {
            if (isFeedbackLoading) {
                Toast.makeText(this, "종합 피드백을 생성하고 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveAnalysis(analysis)
        }

        findViewById<TextView>(R.id.retryButton).setOnClickListener {
            openQuestionActivity(analysis)
        }

        findViewById<ImageButton>(R.id.shareButton).setOnClickListener {
            shareAnalysis(analysis)
        }
    }

    private fun loadGeminiFeedback() {
        val feedbackTextView = findViewById<TextView>(R.id.feedbackTextView)
        isFeedbackLoading = true
        feedbackTextView.text = "잠시만 기다려 주세요! 종합 피드백을 생성하고 있습니다."

        lifecycleScope.launch {
            runCatching {
                AnswerFeedbackRepository.getFeedback(
                    context = this@ResultActivity,
                    question = analysis.question,
                    questionType = analysis.questionType,
                    answer = analysis.answer,
                    expectedKeywords = analysis.expectedKeywords,
                    evaluationPoints = analysis.evaluationPoints,
                    includedKeywords = analysis.includedKeywords,
                    missingKeywords = analysis.missingKeywords,
                    situationStatus = analysis.situationStatus,
                    taskStatus = analysis.taskStatus,
                    actionStatus = analysis.actionStatus,
                    resultStatus = analysis.resultStatus,
                    qualityGrade = analysis.mlPrediction.grade
                )
            }.onSuccess { geminiFeedback ->
                analysis = analysis.copy(feedback = geminiFeedback)
                feedbackTextView.text = geminiFeedback
            }.onFailure { exception ->
                Log.e(TAG, "Gemini feedback generation failed", exception)
                feedbackTextView.text = analysis.feedback
                Toast.makeText(
                    this@ResultActivity,
                    "피드백을 불러오지 못해 기본 분석을 표시합니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
            isFeedbackLoading = false
        }
    }

    private fun createTemporaryAnalysis(
        practiceId: String,
        questionId: String,
        question: String,
        questionType: String,
        expectedKeywords: List<String>,
        evaluationPoints: List<String>,
        answer: String,
        answerSeconds: Int
    ): AnswerAnalysis {
        val keywordBasis = expectedKeywords.ifEmpty {
            listOf("커뮤니케이션", "문제 해결", "협업 경험", "성과")
        }
        val featureAnalysis = FeatureExtractor.extract(
            answer = answer,
            answerSeconds = answerSeconds,
            expectedKeywords = keywordBasis,
            evaluationPoints = evaluationPoints
        )

        val modelInput = featureAnalysis.toModelInput()
        val prediction = AnswerQualityPredictor(this).predict(modelInput)

        val feedback = buildString {
            append("답변의 핵심 의도는 전달되지만, 실제 경험과 구체적인 결과가 더 보강되면 좋습니다.")
            append("\n\n추천 보완 사항:")
            append("\n1. 상황과 본인의 역할을 더 명확하게 설명해 주세요.")
            append("\n2. 행동 이후의 결과를 수치나 변화로 표현해 설득력을 높여 보세요.")
            append("\n3. 채용 공고의 핵심 키워드를 답변에 자연스럽게 포함해 보세요.")
        }

        return AnswerAnalysis(
            answerId = System.currentTimeMillis().toString(),
            practiceId = practiceId,
            questionId = questionId,
            question = question.ifBlank { "질문 정보가 없습니다." },
            questionType = questionType,
            expectedKeywords = keywordBasis,
            evaluationPoints = evaluationPoints,
            answer = answer.ifBlank { "답변 정보가 없습니다." },
            answerLength = featureAnalysis.answerLength,
            answerSeconds = featureAnalysis.answerSeconds,
            includedKeywords = featureAnalysis.includedKeywords.ifEmpty { listOf("핵심 의도") },
            missingKeywords = featureAnalysis.missingKeywords.ifEmpty { listOf("구체적인 수치") },
            situationStatus = featureAnalysis.starAnalysis.situation.status,
            taskStatus = featureAnalysis.starAnalysis.task.status,
            actionStatus = featureAnalysis.starAnalysis.action.status,
            resultStatus = featureAnalysis.starAnalysis.result.status,
            featureAnalysis = featureAnalysis,
            mlPrediction = prediction,
            feedback = feedback,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun loadSavedAnalysis(practiceId: String, questionId: String, answerId: String) {
        FirebaseFirestore.getInstance()
            .collection("History")
            .document(practiceId)
            .collection("Questions")
            .document(questionId)
            .collection("Answers")
            .document(answerId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Toast.makeText(this, "저장된 답변 기록을 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                analysis = createSavedAnalysis(document, practiceId, questionId, answerId)
                bindResult(analysis)
                bindButtons()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "답변 기록 불러오기 실패: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
    }

    private fun createSavedAnalysis(
        document: DocumentSnapshot,
        practiceId: String,
        questionId: String,
        answerId: String
    ): AnswerAnalysis {
        val situationStatus = document.getString("situationStatus") ?: "부족"
        val taskStatus = document.getString("taskStatus") ?: "부족"
        val actionStatus = document.getString("actionStatus") ?: "부족"
        val resultStatus = document.getString("resultStatus") ?: "부족"
        val starAnalysis = StarAnalysisResult(
            situation = savedStarItem("Situation", situationStatus),
            task = savedStarItem("Task", taskStatus),
            action = savedStarItem("Action", actionStatus),
            result = savedStarItem("Result", resultStatus)
        )
        val answer = document.getString("answer").orEmpty()
        val expectedKeywords = readStringList(document.get("expectedKeywords"))
        val includedKeywords = readStringList(document.get("includedKeywords"))
        val missingKeywords = readStringList(document.get("missingKeywords"))
        val answerLength = document.getLong("answerLength")?.toInt() ?: answer.length
        val answerSeconds = document.getLong("answerSeconds")?.toInt() ?: 0
        val featureAnalysis = AnswerFeatureAnalysis(
            answerLength = answerLength,
            answerSeconds = answerSeconds,
            includedKeywords = includedKeywords,
            missingKeywords = missingKeywords,
            keywordMatchRate = document.getDouble("keywordMatchRate")?.toFloat() ?: 0f,
            starAnalysis = starAnalysis,
            answerLengthScore = document.getDouble("answerLengthScore")?.toFloat() ?: 0f,
            answerTimeScore = document.getDouble("answerTimeScore")?.toFloat() ?: 0f,
            hasNumber = document.getDouble("hasNumber")?.toFloat() ?: 0f,
            concretenessScore = document.getDouble("concretenessScore")?.toFloat() ?: 0f
        )
        val probabilities = (document.get("mlPredictionProbabilities") as? List<*>)
            .orEmpty()
            .mapNotNull { (it as? Number)?.toFloat() }

        return AnswerAnalysis(
            answerId = answerId,
            practiceId = practiceId,
            questionId = questionId,
            question = document.getString("question").orEmpty(),
            questionType = document.getString("questionType").orEmpty(),
            expectedKeywords = expectedKeywords,
            evaluationPoints = readStringList(document.get("evaluationPoints")),
            answer = answer,
            answerLength = answerLength,
            answerSeconds = answerSeconds,
            includedKeywords = includedKeywords,
            missingKeywords = missingKeywords,
            situationStatus = situationStatus,
            taskStatus = taskStatus,
            actionStatus = actionStatus,
            resultStatus = resultStatus,
            featureAnalysis = featureAnalysis,
            mlPrediction = MlPredictionResult(
                grade = document.getString("mlPredictionGrade") ?: "정보 없음",
                confidence = document.getDouble("mlPredictionConfidence")?.toFloat() ?: 0f,
                probabilities = probabilities
            ),
            feedback = document.getString("feedback").orEmpty(),
            createdAt = document.getLong("createdAt") ?: 0L
        )
    }

    private fun savedStarItem(label: String, status: String): StarItemAnalysis {
        val score = when (status) {
            "충분", "포함" -> 25
            "일부 포함" -> 10
            else -> 0
        }
        return StarItemAnalysis(label, status, score, emptyList())
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

    private fun saveAnalysis(analysis: AnswerAnalysis) {
        if (analysis.questionId.isBlank()) {
            Toast.makeText(this, "질문 ID가 없어 기록을 저장할 수 없습니다.", Toast.LENGTH_LONG).show()
            return
        }

        val answerData = hashMapOf<String, Any>(
            "answerId" to analysis.answerId,
            "practiceId" to analysis.practiceId,
            "questionId" to analysis.questionId,
            "question" to analysis.question,
            "questionType" to analysis.questionType,
            "expectedKeywords" to analysis.expectedKeywords,
            "evaluationPoints" to analysis.evaluationPoints,
            "answer" to analysis.answer,
            "answerLength" to analysis.answerLength,
            "answerSeconds" to analysis.answerSeconds,
            "includedKeywords" to analysis.includedKeywords,
            "missingKeywords" to analysis.missingKeywords,
            "situationStatus" to analysis.situationStatus,
            "taskStatus" to analysis.taskStatus,
            "actionStatus" to analysis.actionStatus,
            "resultStatus" to analysis.resultStatus,
            "starScore" to analysis.featureAnalysis.starAnalysis.normalizedScore.toDouble(),
            "answerLengthScore" to analysis.featureAnalysis.answerLengthScore.toDouble(),
            "answerTimeScore" to analysis.featureAnalysis.answerTimeScore.toDouble(),
            "keywordMatchRate" to analysis.featureAnalysis.keywordMatchRate.toDouble(),
            "hasNumber" to analysis.featureAnalysis.hasNumber.toDouble(),
            "concretenessScore" to analysis.featureAnalysis.concretenessScore.toDouble(),
            "modelInput" to analysis.featureAnalysis.toModelInput().map { it.toDouble() },
            "mlPredictionGrade" to analysis.mlPrediction.grade,
            "mlPredictionConfidence" to analysis.mlPrediction.confidence.toDouble(),
            "mlPredictionProbabilities" to analysis.mlPrediction.probabilities.map { it.toDouble() },
            "feedback" to analysis.feedback,
            "createdAt" to analysis.createdAt,
            "dateText" to SimpleDateFormat(
                "yyyy.MM.dd HH:mm",
                Locale.getDefault()
            ).format(Date(analysis.createdAt))
        )

        val firestore = FirebaseFirestore.getInstance()
        val questionReference = firestore.collection("History")
            .document(analysis.practiceId)
            .collection("Questions")
            .document(analysis.questionId)
        val answerReference = questionReference.collection("Answers")
            .document(analysis.answerId)
        val batch = firestore.batch()

        batch.set(answerReference, answerData)
        batch.update(
            questionReference,
            mapOf(
                "userAnswer" to analysis.answer,
                "feedback" to analysis.feedback
            )
        )
        batch.commit()
            .addOnSuccessListener {
                isHistoricalResult = true
                findViewById<TextView>(R.id.saveButton).apply {
                    isEnabled = false
                    text = "저장된 기록입니다"
                }
                Toast.makeText(this, "기록을 저장했습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "기록 저장 실패: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun openQuestionActivity(analysis: AnswerAnalysis) {
        val intent = Intent().apply {
            setClassName(packageName, "$packageName.QuestionActivity")
            putExtra(AnswerActivity.EXTRA_PRACTICE_ID, analysis.practiceId)
        }

        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, "Question 화면이 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAnalysis(analysis: AnswerAnalysis) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "답변 분석 결과")
            putExtra(Intent.EXTRA_TEXT, buildShareText(analysis))
        }

        startActivity(Intent.createChooser(shareIntent, "공유하기"))
    }

    private fun buildShareText(analysis: AnswerAnalysis): String {
        return """
            [답변 분석 결과]
            질문: ${analysis.question}
            질문 유형: ${analysis.questionType.ifBlank { "정보 없음" }}
            내 답변: ${analysis.answer}

            분석 결과:
            - 답변 길이: ${analysis.answerLength}자
            - 예상 답변 시간: ${analysis.answerSeconds}초
            - 포함 키워드: ${analysis.includedKeywords.joinToString()}
            - 부족 키워드: ${analysis.missingKeywords.joinToString()}

            STAR 구조:
            - Situation: ${analysis.situationStatus}
            - Task: ${analysis.taskStatus}
            - Action: ${analysis.actionStatus}
            - Result: ${analysis.resultStatus}

            답변 품질 예측:
            - 등급: ${analysis.mlPrediction.grade}
            - 신뢰도: ${(analysis.mlPrediction.confidence * 100).roundToInt()}%

            종합 피드백:
            ${analysis.feedback}
        """.trimIndent()
    }

    private data class AnswerAnalysis(
        val answerId: String,
        val practiceId: String,
        val questionId: String,
        val question: String,
        val questionType: String,
        val expectedKeywords: List<String>,
        val evaluationPoints: List<String>,
        val answer: String,
        val answerLength: Int,
        val answerSeconds: Int,
        val includedKeywords: List<String>,
        val missingKeywords: List<String>,
        val situationStatus: String,
        val taskStatus: String,
        val actionStatus: String,
        val resultStatus: String,
        val featureAnalysis: AnswerFeatureAnalysis,
        val mlPrediction: MlPredictionResult,
        val feedback: String,
        val createdAt: Long
    )

    companion object {
        const val EXTRA_ANSWER_ID = "extra_answer_id"
        private const val TAG = "ResultActivity"
    }
}
