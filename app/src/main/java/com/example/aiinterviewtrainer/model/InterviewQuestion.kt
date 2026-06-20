package com.example.aiinterviewtrainer.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class InterviewQuestion(
    val id: Int,
    val question: String,
    val questionType: String,
    val expectedKeywords: List<String>,
    val evaluationPoints: List<String>
) : Parcelable