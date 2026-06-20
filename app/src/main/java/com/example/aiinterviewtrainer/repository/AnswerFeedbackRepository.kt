package com.example.aiinterviewtrainer.repository

import android.content.Context
import com.example.aiinterviewtrainer.R
import com.example.aiinterviewtrainer.network.GeminiContent
import com.example.aiinterviewtrainer.network.GeminiGenerateContentRequest
import com.example.aiinterviewtrainer.network.GeminiGenerationConfig
import com.example.aiinterviewtrainer.network.GeminiPart
import com.example.aiinterviewtrainer.network.NetworkClient
import com.google.gson.Gson

object AnswerFeedbackRepository {
    private val gson = Gson()

    suspend fun getFeedback(
        context: Context,
        question: String,
        questionType: String,
        answer: String,
        expectedKeywords: List<String>,
        evaluationPoints: List<String>,
        includedKeywords: List<String>,
        missingKeywords: List<String>,
        situationStatus: String,
        taskStatus: String,
        actionStatus: String,
        resultStatus: String,
        qualityGrade: String
    ): String {
        val apiKey = context.getString(R.string.gemini_api_key).trim()
        val modelName = context.getString(R.string.gemini_model_name).trim()

        require(apiKey.isNotBlank()) {
            "local.properties에 gemini.api.key를 입력해 주세요."
        }
        require(answer.isNotBlank()) {
            "분석할 답변이 비어 있습니다."
        }

        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = buildPrompt(
                                question = question,
                                questionType = questionType,
                                answer = answer,
                                expectedKeywords = expectedKeywords,
                                evaluationPoints = evaluationPoints,
                                includedKeywords = includedKeywords,
                                missingKeywords = missingKeywords,
                                situationStatus = situationStatus,
                                taskStatus = taskStatus,
                                actionStatus = actionStatus,
                                resultStatus = resultStatus,
                                qualityGrade = qualityGrade
                            )
                        )
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.35,
                topP = 0.9
            )
        )

        val response = NetworkClient.geminiApiService.generateContent(
            model = modelName.ifBlank { DEFAULT_MODEL_NAME },
            apiKey = apiKey,
            request = request
        )
        val parsed = parseFeedback(response.firstText())
        val recommendations = parsed.recommendations
            .orEmpty()
            .filter { it.isNotBlank() }
            .take(MAX_RECOMMENDATIONS)

        require(!parsed.summary.isNullOrBlank() && recommendations.isNotEmpty()) {
            "Gemini가 유효한 종합 피드백을 반환하지 않았습니다."
        }

        return buildString {
            append(parsed.summary.trim())
            append("\n\n추천 보완 사항:")
            recommendations.forEachIndexed { index, recommendation ->
                append("\n${index + 1}. ${recommendation.trim()}")
            }
        }
    }

    private fun buildPrompt(
        question: String,
        questionType: String,
        answer: String,
        expectedKeywords: List<String>,
        evaluationPoints: List<String>,
        includedKeywords: List<String>,
        missingKeywords: List<String>,
        situationStatus: String,
        taskStatus: String,
        actionStatus: String,
        resultStatus: String,
        qualityGrade: String
    ): String {
        return """
            당신은 10년 경력의 채용 전문가이자 실전 면접 코치입니다.
            아래 질문과 지원자의 실제 답변을 평가하여, 답변 내용에 맞춘 구체적인 종합 피드백을 작성하십시오.

            [평가 원칙]
            1. 제공된 답변에 없는 경험이나 성과를 임의로 만들어 내지 마십시오.
            2. 질문별 평가 포인트와 기대 키워드를 우선 기준으로 사용하십시오.
            3. STAR 분석 결과를 참고하되, 단순히 상태를 반복하지 말고 어떤 부분을 어떻게 보완할지 설명하십시오.
            4. 잘한 점을 한 가지 이상 짚고, 실제 답변 문맥에 맞는 실행 가능한 보완 방법을 제시하십시오.
            5. 추천 보완 사항은 서로 중복되지 않게 3개 작성하십시오.

            [분석 데이터]
            질문: ${gson.toJson(question)}
            질문 유형: ${gson.toJson(questionType)}
            사용자 답변: ${gson.toJson(answer)}
            기대 키워드: ${gson.toJson(expectedKeywords)}
            포함 키워드: ${gson.toJson(includedKeywords)}
            부족 키워드: ${gson.toJson(missingKeywords)}
            평가 포인트: ${gson.toJson(evaluationPoints)}
            STAR 상태: ${gson.toJson(mapOf(
                "situation" to situationStatus,
                "task" to taskStatus,
                "action" to actionStatus,
                "result" to resultStatus
            ))}
            답변 품질 예측 등급: ${gson.toJson(qualityGrade)}

            [출력 제한]
            인사말, 맺음말, 마크다운 코드 블록 없이 아래 JSON 객체만 출력하십시오.
            {
              "summary": "답변에 맞춘 2~3문장의 종합 평가",
              "recommendations": [
                "구체적인 보완 사항 1",
                "구체적인 보완 사항 2",
                "구체적인 보완 사항 3"
              ]
            }
        """.trimIndent()
    }

    private fun parseFeedback(rawText: String): GeminiFeedbackResponse {
        val startIndex = rawText.indexOf('{')
        val endIndex = rawText.lastIndexOf('}')

        require(startIndex >= 0 && endIndex > startIndex) {
            "Gemini 응답에서 피드백 JSON을 찾지 못했습니다."
        }

        return gson.fromJson(
            rawText.substring(startIndex, endIndex + 1),
            GeminiFeedbackResponse::class.java
        )
    }

    private data class GeminiFeedbackResponse(
        val summary: String? = null,
        val recommendations: List<String>? = null
    )

    private const val MAX_RECOMMENDATIONS = 3
    private const val DEFAULT_MODEL_NAME = "gemini-2.5-flash"
}
