package com.example.aiinterviewtrainer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class ResultActivity : AppCompatActivity() {
    private lateinit var analysis: AnswerAnalysis

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val practiceId = intent.getStringExtra(AnswerActivity.EXTRA_PRACTICE_ID).orEmpty().ifBlank {
            System.currentTimeMillis().toString()
        }
        val question = intent.getStringExtra(AnswerActivity.EXTRA_QUESTION).orEmpty()
        val answer = intent.getStringExtra(AnswerActivity.EXTRA_ANSWER).orEmpty()
        val answerSeconds = intent.getIntExtra(AnswerActivity.EXTRA_ANSWER_SECONDS, 0)

        analysis = createTemporaryAnalysis(practiceId, question, answer, answerSeconds)
        bindResult(analysis)
        bindButtons(analysis)
    }

    private fun bindResult(analysis: AnswerAnalysis) {
        findViewById<TextView>(R.id.backTextView).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.questionResultTextView).text = analysis.question
        findViewById<TextView>(R.id.answerResultTextView).text = analysis.answer
        findViewById<TextView>(R.id.lengthTextView).text = analysis.answerLength.toString()
        findViewById<TextView>(R.id.timeTextView).text = analysis.answerSeconds.toString()

        findViewById<TextView>(R.id.keywordCommunication).text =
            analysis.includedKeywords.getOrNull(0) ?: "커뮤니케이션"
        findViewById<TextView>(R.id.keywordProblemSolving).text =
            analysis.includedKeywords.getOrNull(1) ?: "문제 해결"
        findViewById<TextView>(R.id.keywordJobUnderstanding).text =
            analysis.missingKeywords.getOrNull(0) ?: "직무 이해도"
        findViewById<TextView>(R.id.keywordCooperation).text =
            analysis.missingKeywords.getOrNull(1) ?: "협업 경험"
        findViewById<TextView>(R.id.keywordResult).text =
            analysis.missingKeywords.getOrNull(2) ?: "성과"

        findViewById<TextView>(R.id.situationStatusTextView).text = analysis.situationStatus
        findViewById<TextView>(R.id.taskStatusTextView).text = analysis.taskStatus
        findViewById<TextView>(R.id.actionStatusTextView).text = analysis.actionStatus
        findViewById<TextView>(R.id.resultStatusTextView).text = analysis.resultStatus
        findViewById<TextView>(R.id.feedbackTextView).text = analysis.feedback
    }

    private fun bindButtons(analysis: AnswerAnalysis) {
        findViewById<TextView>(R.id.saveButton).setOnClickListener {
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

    private fun createTemporaryAnalysis(
        practiceId: String,
        question: String,
        answer: String,
        answerSeconds: Int
    ): AnswerAnalysis {
        val includedKeywords = mutableListOf<String>()
        val missingKeywords = mutableListOf<String>()

        collectKeyword(
            answer = answer,
            keyword = "커뮤니케이션",
            triggers = listOf("소통", "커뮤니케이션", "의견", "대화"),
            includedKeywords = includedKeywords,
            missingKeywords = missingKeywords
        )
        collectKeyword(
            answer = answer,
            keyword = "문제 해결",
            triggers = listOf("문제", "해결", "개선"),
            includedKeywords = includedKeywords,
            missingKeywords = missingKeywords
        )
        collectKeyword(
            answer = answer,
            keyword = "협업 경험",
            triggers = listOf("협업", "팀", "동료"),
            includedKeywords = includedKeywords,
            missingKeywords = missingKeywords
        )
        collectKeyword(
            answer = answer,
            keyword = "성과",
            triggers = listOf("성과", "결과", "%", "증가", "감소"),
            includedKeywords = includedKeywords,
            missingKeywords = missingKeywords
        )

        val situation = if (answer.contains("상황") || answer.contains("때")) "일부 포함" else "부족"
        val task = if (answer.contains("목표") || answer.contains("과제") || answer.contains("역할")) "일부 포함" else "부족"
        val action = if (answer.contains("했습니다") || answer.contains("진행") || answer.contains("노력")) "일부 포함" else "부족"
        val result = if (answer.contains("결과") || answer.contains("성과") || answer.contains("%")) "일부 포함" else "부족"

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
            answer = answer.ifBlank { "답변 정보가 없습니다." },
            answerLength = answer.length,
            answerSeconds = answerSeconds.coerceAtLeast(1),
            includedKeywords = includedKeywords.ifEmpty { listOf("핵심 의도") },
            missingKeywords = missingKeywords.ifEmpty { listOf("구체적인 수치") },
            situationStatus = situation,
            taskStatus = task,
            actionStatus = action,
            resultStatus = result,
            feedback = feedback,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun collectKeyword(
        answer: String,
        keyword: String,
        triggers: List<String>,
        includedKeywords: MutableList<String>,
        missingKeywords: MutableList<String>
    ) {
        if (triggers.any { answer.contains(it) }) {
            includedKeywords.add(keyword)
        } else {
            missingKeywords.add(keyword)
        }
    }

    private fun saveAnalysis(analysis: AnswerAnalysis) {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val history = JSONArray(preferences.getString(KEY_HISTORY, "[]"))

        history.put(
            JSONObject()
                .put("practiceId", analysis.practiceId)
                .put("question", analysis.question)
                .put("answer", analysis.answer)
                .put("answerLength", analysis.answerLength)
                .put("answerSeconds", analysis.answerSeconds)
                .put("includedKeywords", JSONArray(analysis.includedKeywords))
                .put("missingKeywords", JSONArray(analysis.missingKeywords))
                .put("situationStatus", analysis.situationStatus)
                .put("taskStatus", analysis.taskStatus)
                .put("actionStatus", analysis.actionStatus)
                .put("resultStatus", analysis.resultStatus)
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

            종합 피드백:
            ${analysis.feedback}
        """.trimIndent()
    }

    private data class AnswerAnalysis(
        val practiceId: String,
        val question: String,
        val answer: String,
        val answerLength: Int,
        val answerSeconds: Int,
        val includedKeywords: List<String>,
        val missingKeywords: List<String>,
        val situationStatus: String,
        val taskStatus: String,
        val actionStatus: String,
        val resultStatus: String,
        val feedback: String,
        val createdAt: Long
    )

    companion object {
        private const val PREFS_NAME = "interview_history_prefs"
        private const val KEY_HISTORY = "interview_history"
    }
}
