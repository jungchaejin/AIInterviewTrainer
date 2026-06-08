package com.example.aiinterviewtrainer.contract

import android.content.Context
import android.content.Intent
import com.example.aiinterviewtrainer.AnswerActivity
import com.example.aiinterviewtrainer.model.InterviewQuestion
import com.example.aiinterviewtrainer.repository.InterviewQuestionRepository

object QuestionActivityContract {
    const val EXTRA_PRACTICE_ID = AnswerActivity.EXTRA_PRACTICE_ID
    const val EXTRA_QUESTIONS_JSON = "extra_questions_json"

    fun createIntent(
        context: Context,
        practiceId: String,
        questions: List<InterviewQuestion>
    ): Intent {
        return Intent().apply {
            setClassName(context.packageName, "${context.packageName}.QuestionActivity")
            putExtra(EXTRA_PRACTICE_ID, practiceId)
            putExtra(EXTRA_QUESTIONS_JSON, InterviewQuestionRepository.questionsToJson(questions))
        }
    }

    fun getQuestions(intent: Intent): List<InterviewQuestion> {
        return InterviewQuestionRepository.questionsFromJson(
            intent.getStringExtra(EXTRA_QUESTIONS_JSON)
        )
    }

    fun createAnswerIntent(
        context: Context,
        practiceId: String,
        selectedQuestion: InterviewQuestion
    ): Intent {
        return Intent(context, AnswerActivity::class.java).apply {
            putExtra(EXTRA_PRACTICE_ID, practiceId)
            putExtra(AnswerActivity.EXTRA_QUESTION, selectedQuestion.question)
            putExtra(AnswerActivity.EXTRA_QUESTION_TYPE, selectedQuestion.questionType)
            putStringArrayListExtra(
                AnswerActivity.EXTRA_EXPECTED_KEYWORDS,
                ArrayList(selectedQuestion.expectedKeywords)
            )
            putStringArrayListExtra(
                AnswerActivity.EXTRA_EVALUATION_POINTS,
                ArrayList(selectedQuestion.evaluationPoints)
            )
        }
    }
}
