package com.example.aiinterviewtrainer.network

import android.content.Context
import android.icu.text.MessagePattern
import com.example.aiinterviewtrainer.model.InterviewQuestion
import com.google.ai.client.generativeai.common.shared.Content
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InterviewQuestionRepository {
    private const val GEMINI_API_KEY = "YOUR_ACTUAL_GEMINI_API_KEY_HERE"
    private const val MODEL_NAME = "gemini-1.5-flash"

    suspend fun getQuestions(context: Context, jdText: String): List<InterviewQuestion> {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    너는 베테랑 인사담당자이자 면접관이야. 
                    다음 제공되는 채용공고(JD) 텍스트를 정밀 분석해서, 지원자에게 던질 핵심 면접 질문 5개와 각 질문에 대한 예상 키워드를 추출해줘.
                    
                    [채용공고 내용]
                    $jdText
                    
                    ⚠️ 주의사항: 다른 부연 설명이나 인사말은 절대 하지 말고, 오직 아래의 JSON 배열 형식으로만 응답해줘. 반드시 이 형식을 지켜야 해.
                    [
                      {
                        "question": "질문 내용 1",
                        "expectedKeywords": "키워드1, 키워드2, 키워드3"
                      },
                      ...
                    ]
                """.trimIndent()

                val request = GeminiGenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(MessagePattern.Part(text = prompt)))
                    )
                )

                val response = NetworkClient.geminiApiService.generateContent(
                    model = MODEL_NAME,
                    apiKey = GEMINI_API_KEY,
                    request = request
                )

                val resultJsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("AI가 질문을 생성하지 못했습니다.")

                val cleanJson = resultJsonText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val gson = Gson()
                val listType = object : TypeToken<List<InterviewQuestion>>() {}.type
                val questionsList: List<InterviewQuestion> = gson.fromJson(cleanJson, listType)

                questionsList

            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("질문 생성 중 오류 발생: ${e.localizedMessage}")
            }
        }
    }
}