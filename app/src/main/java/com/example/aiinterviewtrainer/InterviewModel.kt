package com.example.aiinterviewtrainer

data class HistoryItem(
    val id: String,
    val title: String,
    val date: String
)

data class HistoryData(
    val practiceId: String,
    val questionId: String,
    val answerId: String,
    val dateText: String
)
