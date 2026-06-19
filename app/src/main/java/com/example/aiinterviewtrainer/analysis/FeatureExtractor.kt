package com.example.aiinterviewtrainer.analysis

object FeatureExtractor {
    fun extract(
        answer: String,
        answerSeconds: Int,
        expectedKeywords: List<String>,
        evaluationPoints: List<String>
    ): AnswerFeatureAnalysis {
        val normalizedAnswer = answer.trim()
        val keywordBasis = expectedKeywords
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { DEFAULT_KEYWORDS }

        val includedKeywords = keywordBasis.filter { keyword ->
            normalizedAnswer.contains(keyword, ignoreCase = true)
        }
        val missingKeywords = keywordBasis.filterNot { keyword ->
            normalizedAnswer.contains(keyword, ignoreCase = true)
        }

        val keywordMatchRate = if (keywordBasis.isEmpty()) {
            0f
        } else {
            includedKeywords.size.toFloat() / keywordBasis.size.toFloat()
        }

        val starAnalysis = StarAnalyzer.analyze(
            answer = normalizedAnswer,
            evaluationPoints = evaluationPoints
        )

        val answerLength = normalizedAnswer.length
        val safeAnswerSeconds = answerSeconds.coerceAtLeast(1)
        val hasNumber = if (NUMBER_PATTERN.containsMatchIn(normalizedAnswer)) 1f else 0f

        return AnswerFeatureAnalysis(
            answerLength = answerLength,
            answerSeconds = safeAnswerSeconds,
            includedKeywords = includedKeywords,
            missingKeywords = missingKeywords,
            keywordMatchRate = keywordMatchRate.coerceIn(0f, 1f),
            starAnalysis = starAnalysis,
            answerLengthScore = normalize(answerLength.toFloat(), IDEAL_ANSWER_LENGTH),
            answerTimeScore = normalize(safeAnswerSeconds.toFloat(), IDEAL_ANSWER_SECONDS),
            hasNumber = hasNumber,
            concretenessScore = calculateConcretenessScore(
                answer = normalizedAnswer,
                keywordMatchRate = keywordMatchRate,
                starScore = starAnalysis.normalizedScore,
                hasNumber = hasNumber
            )
        )
    }

    private fun calculateConcretenessScore(
        answer: String,
        keywordMatchRate: Float,
        starScore: Float,
        hasNumber: Float
    ): Float {
        val concreteSignalRate = CONCRETENESS_SIGNALS.count {
            answer.contains(it, ignoreCase = true)
        }.toFloat() / CONCRETENESS_SIGNALS.size.toFloat()

        return (
            keywordMatchRate * 0.25f +
                starScore * 0.35f +
                hasNumber * 0.20f +
                concreteSignalRate * 0.20f
            ).coerceIn(0f, 1f)
    }

    private fun normalize(value: Float, idealValue: Float): Float {
        return (value / idealValue).coerceIn(0f, 1f)
    }

    private val NUMBER_PATTERN = Regex("""\d+([.,]\d+)?\s*(%|명|건|개|회|년|개월|주|일|시간|분|초|원|만원|배)?""")

    private val DEFAULT_KEYWORDS = listOf("커뮤니케이션", "문제 해결", "협업 경험", "성과")
    private val CONCRETENESS_SIGNALS = listOf(
        "프로젝트",
        "문제",
        "원인",
        "역할",
        "해결",
        "개선",
        "결과",
        "성과",
        "배운"
    )

    private const val IDEAL_ANSWER_LENGTH = 500f
    private const val IDEAL_ANSWER_SECONDS = 90f
}
