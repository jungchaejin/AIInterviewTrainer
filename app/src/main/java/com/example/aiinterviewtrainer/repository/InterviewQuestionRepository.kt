package com.example.aiinterviewtrainer.repository

import android.content.Context
import android.util.Log
import com.example.aiinterviewtrainer.R
import com.example.aiinterviewtrainer.model.GeneratedInterview
import com.example.aiinterviewtrainer.model.InterviewQuestion
import com.example.aiinterviewtrainer.network.GeminiContent
import com.example.aiinterviewtrainer.network.GeminiGenerateContentRequest
import com.example.aiinterviewtrainer.network.GeminiGenerationConfig
import com.example.aiinterviewtrainer.network.GeminiPart
import com.example.aiinterviewtrainer.network.NetworkClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException

object InterviewQuestionRepository {
    private val gson = Gson()

    suspend fun generateInterview(context: Context, jdText: String): GeneratedInterview {
        require(jdText.isNotBlank()) {
            "채용공고 텍스트가 비어 있습니다."
        }

        return try {
            generateInterviewFromGemini(context, jdText)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Gemini question generation failed. Using fallback questions.", exception)
            createFallbackInterview()
        }
    }

    private suspend fun generateInterviewFromGemini(
        context: Context,
        jdText: String
    ): GeneratedInterview {
        val apiKey = context.getString(R.string.gemini_api_key).trim()
        val modelName = context.getString(R.string.gemini_model_name).trim()

        require(apiKey.isNotBlank()) {
            "local.properties에 gemini.api.key를 입력해 주세요."
        }
        require(jdText.isNotBlank()) {
            "채용공고 텍스트가 비어 있습니다."
        }

        val normalizedJdText = jdText
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(MAX_JD_TEXT_LENGTH)
        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = buildPrompt(normalizedJdText)))
                )
            ),
            generationConfig = GeminiGenerationConfig()
        )

        val response = requestQuestionsWithRetry(
            modelName = modelName.ifBlank { DEFAULT_MODEL_NAME },
            apiKey = apiKey,
            request = request
        )

        val rawText = response.firstText()
        val parsedResponse = parseGeneratedInterview(rawText)
        val questions = parsedResponse.questions

        require(questions.size == QUESTION_COUNT) {
            "Gemini가 질문 ${QUESTION_COUNT}개를 반환하지 않았습니다."
        }

        val practiceTitle = parsedResponse.practiceTitle
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_PRACTICE_TITLE_LENGTH)
            .ifBlank { DEFAULT_PRACTICE_TITLE }

        return GeneratedInterview(
            practiceTitle = practiceTitle,
            questions = questions,
            isFallback = false
        )
    }

    suspend fun getQuestions(context: Context, jdText: String): List<InterviewQuestion> {
        return generateInterview(context, jdText).questions
    }

    private fun createFallbackInterview(): GeneratedInterview {
        return GeneratedInterview(
            practiceTitle = "기본 직무 면접 연습",
            isFallback = true,
            questions = listOf(
                InterviewQuestion(
                    id = 1,
                    question = "최근 수행한 프로젝트에서 가장 어려웠던 문제와 이를 해결한 과정을 설명해 주세요.",
                    questionType = "문제 해결",
                    expectedKeywords = listOf("문제 상황", "원인 분석", "본인 역할", "해결 과정", "성과"),
                    evaluationPoints = listOf("문제 상황의 구체성", "본인의 역할", "해결 행동의 논리성", "결과와 배운 점")
                ),
                InterviewQuestion(
                    id = 2,
                    question = "지원하신 직무에서 가장 중요하다고 생각하는 역량은 무엇이며, 그 역량을 발휘한 경험이 있나요?",
                    questionType = "직무 전문성",
                    expectedKeywords = listOf("직무 이해", "핵심 역량", "업무 경험", "판단 근거", "성과"),
                    evaluationPoints = listOf("직무에 대한 이해도", "역량 선택의 근거", "경험의 구체성", "업무 기여 가능성")
                ),
                InterviewQuestion(
                    id = 3,
                    question = "협업 과정에서 의견 충돌이 발생했을 때 어떻게 조율했는지 말씀해 주세요.",
                    questionType = "상황 대처",
                    expectedKeywords = listOf("갈등 상황", "의사소통", "의견 조율", "협업", "결과"),
                    evaluationPoints = listOf("갈등 원인의 이해", "본인의 중재 역할", "의사소통 방식", "관계 및 결과의 변화")
                ),
                InterviewQuestion(
                    id = 4,
                    question = "목표한 결과를 얻지 못했던 경험과 이후 어떤 방식으로 개선했는지 설명해 주세요.",
                    questionType = "경험 검증",
                    expectedKeywords = listOf("목표", "실패 원인", "피드백", "개선 행동", "재도전"),
                    evaluationPoints = listOf("실패 원인의 객관적 분석", "책임감 있는 태도", "개선 행동의 구체성", "학습과 성장")
                ),
                InterviewQuestion(
                    id = 5,
                    question = "입사 후 새로운 업무나 기술을 빠르게 익혀야 한다면 어떤 방식으로 학습하고 적용하시겠어요?",
                    questionType = "성장 가능성",
                    expectedKeywords = listOf("학습 계획", "우선순위", "정보 탐색", "실무 적용", "피드백"),
                    evaluationPoints = listOf("학습 계획의 현실성", "자기주도성", "업무 적용 방법", "피드백 활용 태도")
                )
            )
        )
    }

    private suspend fun requestQuestionsWithRetry(
        modelName: String,
        apiKey: String,
        request: GeminiGenerateContentRequest
    ) = try {
        NetworkClient.geminiApiService.generateContent(
            model = modelName,
            apiKey = apiKey,
            request = request
        )
    } catch (exception: SocketTimeoutException) {
        delay(RETRY_DELAY_MILLIS)
        NetworkClient.geminiApiService.generateContent(
            model = modelName,
            apiKey = apiKey,
            request = request
        )
    }

    fun questionsToJson(questions: List<InterviewQuestion>): String {
        return gson.toJson(questions)
    }

    fun questionsFromJson(json: String?): List<InterviewQuestion> {
        if (json.isNullOrBlank()) return emptyList()

        return runCatching {
            val type = object : TypeToken<List<InterviewQuestion>>() {}.type
            gson.fromJson<List<InterviewQuestion>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun parseGeneratedInterview(rawText: String): GeminiQuestionResponse {
        val jsonObjectText = extractJsonObject(rawText)
        val parsed = gson.fromJson(jsonObjectText, GeminiQuestionResponse::class.java)
        return GeminiQuestionResponse(
            practiceTitle = parsed?.practiceTitle,
            questions = parsed?.questions
                .orEmpty()
                .filter { it.question.isNotBlank() }
                .take(QUESTION_COUNT)
        )
    }

    private fun extractJsonObject(text: String): String {
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')

        require(startIndex >= 0 && endIndex > startIndex) {
            "Gemini 응답에서 JSON 객체를 찾지 못했습니다."
        }

        return text.substring(startIndex, endIndex + 1)
    }

    private fun buildPrompt(jdText: String): String {
        return """
            당신은 10년 경력의 최고 채용 전문가이자 실전 면접 코치입니다.
            당신의 역할은 사용자가 입력한 [채용공고 웹페이지 텍스트]를 분석하여, 실전 면접에서 마주칠 가능성이 높은 핵심 질문 5가지와 각 질문별 상세 평가 기준을 생성하는 것입니다.

            [분석 및 질문 생성 원칙]
            1. 노이즈 필터링: 입력된 텍스트에는 웹페이지의 메뉴, 하단 정보 등 불필요한 내용이 섞여 있을 수 있습니다. 오직 직무 설명, 자격 요건, 우대 사항, 기업 정보 등 '채용공고의 핵심 내용'만 추출하여 분석하십시오.
            2. 질문 다각화 및 랜덤성: 사용자가 동일한 공고로 여러 번 연습할 수 있으므로, 범용적인 질문(자기소개, 지원동기 등)은 배제하고 매번 다양한 각도에서 새로운 질문이 나오도록 무작위성을 부여하십시오.
            3. 해당 JD의 직무·산업·요구 역량에 완벽히 특화된 뾰족하고 구체적인 질문만 생성하십시오.
            4. 각 질문은 실제 면접관이 구두로 질문하는 것처럼 자연스럽고 정중한 구어체로 작성하십시오.

            [평가 기준 정의 원칙]
            질문 생성 시, 후속 답변 평가를 위해 아래 3가지 요소를 반드시 함께 정의해야 합니다.
            1. questionType (질문 유형): 질문의 본질에 따라 아래 유형 중 하나를 선택합니다.
               - 직무 전문성 / 문제 해결 / 경험 검증 / 상황 대처 / 성장 가능성 등
            2. expectedKeywords (기대 키워드): 해당 직무와 질문을 고려했을 때, 역량이 뛰어난 지원자라면 답변에 반드시 포함했을 법한 핵심 기술/업무/역량 키워드를 5~6개 선정합니다. (예: 웹 개발 JD라면 '디버깅, API, 성능 개선', 마케팅 JD라면 '전환율, 코호트 분석, 성과 지표' 등)
            3. evaluationPoints (평가 포인트): 지원자의 답변에서 중점적으로 평가해야 하는 구체적인 체크리스트를 3~4개 정의합니다. (예: 문제 상황의 구체성, 본인의 구체적인 역할, 행동의 논리성, 성과 및 배운 점 등)

            [면접 연습 이름 생성 원칙]
            - 채용공고에서 회사명, 직무명, 채용 형태(인턴/신입/경력)를 파악하여 practiceTitle을 생성하십시오.
            - "회사명 + 직무명 + 채용 형태 + 면접 연습" 순서의 자연스러운 한국어 제목으로 작성하십시오.
            - 회사명이나 채용 형태를 확인할 수 없으면 확인 가능한 정보만 사용하십시오.
            - 등급, 점수, Rank 표현은 포함하지 마십시오.
            - 20자 이내로 간결하게 작성하십시오.

            [출력 형식 제한] - 시스템 연동을 위해 반드시 지켜야 할 철칙
            - 어떠한 인사말, 부연 설명, 맺음말도 작성하지 마십시오.
            - 마크다운 코드 블록(```json 등)을 포함하지 말고, 순수한 JSON 문자열(String)만 출력하십시오.
            - 반드시 아래 제시된 JSON 구조와 Key 명칭을 정확하게 일치시켜 출력하십시오.

            [출력 형식]
            {
              "practiceTitle": "네이버웹툰 HR 인턴 면접 연습",
              "questions": [
                {
                  "id": 1,
                  "question": "실제 질문 내용 (구어체)",
                  "questionType": "질문 유형",
                  "expectedKeywords": [
                    "기대 키워드 1",
                    "기대 키워드 2",
                    "기대 키워드 3",
                    "기대 키워드 4",
                    "기대 키워드 5"
                  ],
                  "evaluationPoints": [
                    "평가 포인트 1",
                    "평가 포인트 2",
                    "평가 포인트 3"
                  ]
                }
              ]
            }

            [채용공고 웹페이지 텍스트]
            ${jdText.trim()}
        """.trimIndent()
    }

    private data class GeminiQuestionResponse(
        val practiceTitle: String? = null,
        val questions: List<InterviewQuestion> = emptyList()
    )

    private const val QUESTION_COUNT = 5
    private const val TAG = "InterviewQuestionRepo"
    private const val DEFAULT_MODEL_NAME = "gemini-2.5-flash"
    private const val DEFAULT_PRACTICE_TITLE = "직무 맞춤 면접 연습"
    private const val MAX_PRACTICE_TITLE_LENGTH = 30
    private const val MAX_JD_TEXT_LENGTH = 50_000
    private const val RETRY_DELAY_MILLIS = 1_500L
}
