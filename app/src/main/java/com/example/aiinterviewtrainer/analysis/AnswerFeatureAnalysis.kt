package com.example.aiinterviewtrainer.analysis

data class AnswerFeatureAnalysis(
    val answerLength: Int,
    val answerSeconds: Int,
    val includedKeywords: List<String>,
    val missingKeywords: List<String>,
    val keywordMatchRate: Float,
    val starAnalysis: StarAnalysisResult,
    val answerLengthScore: Float,
    val answerTimeScore: Float,
    val hasNumber: Float,
    val concretenessScore: Float
) {
    fun toModelInput(): FloatArray {
        return floatArrayOf(
            answerLengthScore,
            answerTimeScore,
            keywordMatchRate,
            starAnalysis.normalizedScore,
            hasNumber,
            concretenessScore
        )
    }
}
