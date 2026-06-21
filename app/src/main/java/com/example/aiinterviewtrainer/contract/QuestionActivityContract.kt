package com.example.aiinterviewtrainer.contract

import android.content.Context
import android.content.Intent
import com.example.aiinterviewtrainer.AnswerActivity
import com.example.aiinterviewtrainer.model.InterviewQuestion

object QuestionActivityContract {
    const val EXTRA_PRACTICE_ID = AnswerActivity.EXTRA_PRACTICE_ID
    fun createAnswerIntent(
        context: Context,
        practiceId: String,
        questionId: String,
        selectedQuestion: InterviewQuestion
    ): Intent {
        return Intent(context, AnswerActivity::class.java).apply {
            putExtra(EXTRA_PRACTICE_ID, practiceId)
            putExtra(AnswerActivity.EXTRA_QUESTION_ID, questionId)
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
