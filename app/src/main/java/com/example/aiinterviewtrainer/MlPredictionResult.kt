package com.example.aiinterviewtrainer

data class MlPredictionResult(
    val grade: String,
    val confidence: Float,
    val probabilities: List<Float>
)