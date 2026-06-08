package com.example.aiinterviewtrainer.network

data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.95,
    val topP: Double = 0.95,
    val candidateCount: Int = 1,
    val responseMimeType: String = "application/json"
)

data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null
) {
    fun firstText(): String {
        return candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.mapNotNull { it.text }
            ?.joinToString(separator = "")
            .orEmpty()
    }
}

data class GeminiCandidate(
    val content: GeminiContent? = null
)
