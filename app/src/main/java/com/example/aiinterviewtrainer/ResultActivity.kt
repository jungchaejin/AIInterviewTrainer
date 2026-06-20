package com.example.aiinterviewtrainer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aiinterviewtrainer.analysis.AnswerFeatureAnalysis
import com.example.aiinterviewtrainer.analysis.FeatureExtractor
import com.example.aiinterviewtrainer.repository.AnswerFeedbackRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class ResultActivity : AppCompatActivity() {
    private lateinit var analysis: AnswerAnalysis
    private var isFeedbackLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val practiceId = intent.getStringExtra(AnswerActivity.EXTRA_PRACTICE_ID).orEmpty().ifBlank {
            System.currentTimeMillis().toString()
        }
        val question = intent.getStringExtra(AnswerActivity.EXTRA_QUESTION).orEmpty()
        val questionType = intent.getStringExtra(AnswerActivity.EXTRA_QUESTION_TYPE).orEmpty()
        val expectedKeywords = intent.getStringArrayListExtra(AnswerActivity.EXTRA_EXPECTED_KEYWORDS).orEmpty()
        val evaluationPoints = intent.getStringArrayListExtra(AnswerActivity.EXTRA_EVALUATION_POINTS).orEmpty()
        val answer = intent.getStringExtra(AnswerActivity.EXTRA_ANSWER).orEmpty()
        val answerSeconds = intent.getIntExtra(AnswerActivity.EXTRA_ANSWER_SECONDS, 0)

        analysis = createTemporaryAnalysis(
            practiceId = practiceId,
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
        findViewById<TextView>(R.id.backTextView).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.questionResultTextView).text = analysis.question
        findViewById<TextView>(R.id.answerResultTextView).text = analysis.answer
        findViewById<TextView>(R.id.lengthTextView).text = analysis.answerLength.toString()
        findViewById<TextView>(R.id.timeTextView).text = analysis.answerSeconds.toString()

        bindKeywords(analysis)
        bindStarStatus(R.id.situationStatusTextView, R.id.situationBadge, analysis.situationStatus)
        bindStarStatus(R.id.taskStatusTextView, R.id.taskBadge, analysis.taskStatus)
        bindStarStatus(R.id.actionStatusTextView, R.id.actionBadge, analysis.actionStatus)
        bindStarStatus(R.id.resultStatusTextView, R.id.resultBadge, analysis.resultStatus)
        findViewById<TextView>(R.id.qualityGradeTextView).text = analysis.mlPrediction.grade
        findViewById<TextView>(R.id.qualityConfidenceTextView).text =
            "예측 신뢰도 ${(analysis.mlPrediction.confidence * 100).roundToInt()}%"
        findViewById<TextView>(R.id.feedbackTextView).text = analysis.feedback
    }

    private fun bindKeywords(analysis: AnswerAnalysis) {
        val firstRow = findViewById<LinearLayout>(R.id.keywordRow1)
        val secondRow = findViewById<LinearLayout>(R.id.keywordRow2)
        firstRow.removeAllViews()
        secondRow.removeAllViews()

        val includedKeywords = analysis.includedKeywords.map { it.trim().lowercase() }.toSet()
        val keywords = (
            analysis.expectedKeywords + analysis.includedKeywords + analysis.missingKeywords
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        keywords.forEachIndexed { index, keyword ->
            val targetRow = if (index < KEYWORDS_PER_ROW) firstRow else secondRow
            val isIncluded = keyword.lowercase() in includedKeywords
            targetRow.addView(createKeywordChip(keyword, isIncluded, targetRow.childCount > 0))
        }
    }

    private fun createKeywordChip(
        keyword: String,
        isIncluded: Boolean,
        hasStartMargin: Boolean
    ): TextView {
        return TextView(this).apply {
            text = keyword
            textSize = 12f
            maxLines = 1
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setBackgroundResource(if (isIncluded) R.drawable.bg_chip_blue else R.drawable.bg_chip_gray)
            setTextColor(
                ContextCompat.getColor(
                    this@ResultActivity,
                    if (isIncluded) R.color.main_blue else R.color.keyword_missing_text
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (hasStartMargin) marginStart = dp(8)
            }
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
        findViewById<TextView>(R.id.saveButton).setOnClickListener {
            if (isFeedbackLoading) {
                Toast.makeText(this, "종합 피드백을 생성하고 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveAnalysis(analysis)
            Toast.makeText(this, "기록을 저장했습니다.", Toast.LENGTH_SHORT).show()
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
        feedbackTextView.text = "잠시만 기다려 주세요! Gemini가 종합 피드백을 생성하고 있습니다."

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
                    "Gemini 피드백을 불러오지 못해 기본 분석을 표시합니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
            isFeedbackLoading = false
        }
    }

    private fun createTemporaryAnalysis(
        practiceId: String,
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
            practiceId = practiceId,
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

    private fun saveAnalysis(analysis: AnswerAnalysis) {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val history = JSONArray(preferences.getString(KEY_HISTORY, "[]"))

        history.put(
            JSONObject()
                .put("practiceId", analysis.practiceId)
                .put("question", analysis.question)
                .put("questionType", analysis.questionType)
                .put("expectedKeywords", JSONArray(analysis.expectedKeywords))
                .put("evaluationPoints", JSONArray(analysis.evaluationPoints))
                .put("answer", analysis.answer)
                .put("answerLength", analysis.answerLength)
                .put("answerSeconds", analysis.answerSeconds)
                .put("includedKeywords", JSONArray(analysis.includedKeywords))
                .put("missingKeywords", JSONArray(analysis.missingKeywords))
                .put("situationStatus", analysis.situationStatus)
                .put("taskStatus", analysis.taskStatus)
                .put("actionStatus", analysis.actionStatus)
                .put("resultStatus", analysis.resultStatus)
                .put("starScore", analysis.featureAnalysis.starAnalysis.normalizedScore.toDouble())
                .put("answerLengthScore", analysis.featureAnalysis.answerLengthScore.toDouble())
                .put("answerTimeScore", analysis.featureAnalysis.answerTimeScore.toDouble())
                .put("keywordMatchRate", analysis.featureAnalysis.keywordMatchRate.toDouble())
                .put("hasNumber", analysis.featureAnalysis.hasNumber.toDouble())
                .put("concretenessScore", analysis.featureAnalysis.concretenessScore.toDouble())
                .put("modelInput", JSONArray(analysis.featureAnalysis.toModelInput().toList()))
                .put("mlPredictionGrade", analysis.mlPrediction.grade)
                .put("mlPredictionConfidence", analysis.mlPrediction.confidence.toDouble())
                .put("mlPredictionProbabilities", JSONArray(analysis.mlPrediction.probabilities))
                .put("feedback", analysis.feedback)
                .put("createdAt", analysis.createdAt)
        )

        preferences.edit()
            .putString(KEY_HISTORY, history.toString())
            .apply()
    }

    private fun openQuestionActivity(analysis: AnswerAnalysis) {
        val intent = Intent().apply {
            setClassName(packageName, "$packageName.QuestionActivity")
            putExtra(AnswerActivity.EXTRA_PRACTICE_ID, analysis.practiceId)
        }

        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, "QuestionActivity가 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
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
        val practiceId: String,
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
        private const val TAG = "ResultActivity"
        private const val KEYWORDS_PER_ROW = 3
        private const val PREFS_NAME = "interview_history_prefs"
        private const val KEY_HISTORY = "interview_history"
    }
}
