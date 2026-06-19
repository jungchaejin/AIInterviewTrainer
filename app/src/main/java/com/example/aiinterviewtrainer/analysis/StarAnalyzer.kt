package com.example.aiinterviewtrainer.analysis

object StarAnalyzer {
    fun analyze(answer: String, evaluationPoints: List<String>): StarAnalysisResult {
        val normalizedAnswer = answer.trim()
        val pointHints = buildEvaluationPointHints(evaluationPoints)

        return StarAnalysisResult(
            situation = analyzeItem(
                label = "Situation",
                answer = normalizedAnswer,
                baseSignals = SITUATION_SIGNALS,
                evaluationSignals = pointHints.situation
            ),
            task = analyzeItem(
                label = "Task",
                answer = normalizedAnswer,
                baseSignals = TASK_SIGNALS,
                evaluationSignals = pointHints.task
            ),
            action = analyzeItem(
                label = "Action",
                answer = normalizedAnswer,
                baseSignals = ACTION_SIGNALS,
                evaluationSignals = pointHints.action
            ),
            result = analyzeItem(
                label = "Result",
                answer = normalizedAnswer,
                baseSignals = RESULT_SIGNALS,
                evaluationSignals = pointHints.result
            )
        )
    }

    private fun analyzeItem(
        label: String,
        answer: String,
        baseSignals: List<String>,
        evaluationSignals: List<String>
    ): StarItemAnalysis {
        val matchedBaseSignals = baseSignals.filter {
            answer.contains(it, ignoreCase = true)
        }
        val matchedEvaluationSignals = evaluationSignals.filter {
            answer.contains(it, ignoreCase = true)
        }
        val matchedSignals = (matchedBaseSignals + matchedEvaluationSignals).distinct()
        val score = when {
            matchedSignals.size >= 2 -> SCORE_SUFFICIENT
            matchedSignals.size == 1 -> SCORE_PARTIAL
            else -> SCORE_MISSING
        }

        return StarItemAnalysis(
            label = label,
            status = scoreToStatus(score),
            score = score,
            matchedSignals = matchedSignals
        )
    }

    private fun buildEvaluationPointHints(evaluationPoints: List<String>): EvaluationPointHints {
        val situation = mutableSetOf<String>()
        val task = mutableSetOf<String>()
        val action = mutableSetOf<String>()
        val result = mutableSetOf<String>()

        evaluationPoints.forEach { point ->
            val tokens = point
                .split(" ", "/", ",", ".", "·", "및")
                .map { it.trim() }
                .filter { it.length >= 2 }

            when {
                SITUATION_HINTS.any { point.contains(it, ignoreCase = true) } -> situation.addAll(tokens)
                TASK_HINTS.any { point.contains(it, ignoreCase = true) } -> task.addAll(tokens)
                ACTION_HINTS.any { point.contains(it, ignoreCase = true) } -> action.addAll(tokens)
                RESULT_HINTS.any { point.contains(it, ignoreCase = true) } -> result.addAll(tokens)
            }
        }

        return EvaluationPointHints(
            situation = situation.toList(),
            task = task.toList(),
            action = action.toList(),
            result = result.toList()
        )
    }

    private fun scoreToStatus(score: Int): String {
        return when (score) {
            SCORE_SUFFICIENT -> "충분"
            SCORE_PARTIAL -> "일부 포함"
            else -> "부족"
        }
    }

    private data class EvaluationPointHints(
        val situation: List<String>,
        val task: List<String>,
        val action: List<String>,
        val result: List<String>
    )

    private val SITUATION_SIGNALS = listOf("상황", "당시", "프로젝트", "문제 발생", "문제", "배경", "갈등")
    private val TASK_SIGNALS = listOf("담당", "역할", "목표", "책임", "과제", "맡", "해야")
    private val ACTION_SIGNALS = listOf("분석", "해결", "수정", "개선", "구현", "진행", "시도", "조치")
    private val RESULT_SIGNALS = listOf("결과", "성과", "증가", "감소", "달성", "완료", "효과", "%")

    private val SITUATION_HINTS = listOf("상황", "문제 상황", "배경", "구체성", "맥락")
    private val TASK_HINTS = listOf("역할", "담당", "목표", "책임", "과제")
    private val ACTION_HINTS = listOf("행동", "해결", "과정", "논리", "개선", "실행")
    private val RESULT_HINTS = listOf("결과", "성과", "효과", "배운", "수치", "개선 효과")

    private const val SCORE_MISSING = 0
    private const val SCORE_PARTIAL = 10
    private const val SCORE_SUFFICIENT = 25
}
