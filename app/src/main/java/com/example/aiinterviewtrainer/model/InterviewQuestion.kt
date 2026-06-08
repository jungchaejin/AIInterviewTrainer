package com.example.aiinterviewtrainer.model

data class InterviewQuestion(
    val id: Int,
    val question: String,
    val questionType: String,
    val expectedKeywords: List<String>,
    val evaluationPoints: List<String>
)
