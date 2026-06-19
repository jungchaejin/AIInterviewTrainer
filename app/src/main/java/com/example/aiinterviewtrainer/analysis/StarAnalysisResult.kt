package com.example.aiinterviewtrainer.analysis

data class StarAnalysisResult(
    val situation: StarItemAnalysis,
    val task: StarItemAnalysis,
    val action: StarItemAnalysis,
    val result: StarItemAnalysis
) {
    val totalScore: Int
        get() = situation.score + task.score + action.score + result.score

    val normalizedScore: Float
        get() = (totalScore / 100f).coerceIn(0f, 1f)
}

data class StarItemAnalysis(
    val label: String,
    val status: String,
    val score: Int,
    val matchedSignals: List<String>
)
