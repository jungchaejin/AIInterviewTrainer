package com.example.aiinterviewtrainer

import com.example.aiinterviewtrainer.model.InterviewQuestion

data class HistoryItem(
    val id: String,
    val title: String,
    val date: String
)

data class QuestionData(
    val practiceId: String,
    val questionInfo: InterviewQuestion,
    val historyList: List<HistoryData>
)

data class HistoryData(
    val practiceId: String,
    val dateText: String
)