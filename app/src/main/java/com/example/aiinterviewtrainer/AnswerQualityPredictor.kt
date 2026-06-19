package com.example.aiinterviewtrainer

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class AnswerQualityPredictor(private val context: Context) {
    private val interpreter by lazy {
        Interpreter(loadModelFile())
    }

    fun predict(features: FloatArray): MlPredictionResult {
        require(features.size == MODEL_INPUT_SIZE) {
            "TFLite 모델 입력값은 ${MODEL_INPUT_SIZE}개여야 합니다."
        }

        val input = arrayOf(features)
        val output = Array(1) { FloatArray(MODEL_OUTPUT_SIZE) }

        interpreter.run(input, output)

        val probabilities = output[0].toList()
        val maxIndex = probabilities.indices.maxBy { probabilities[it] }
        val grade = when (maxIndex) {
            0 -> "부족"
            1 -> "보통"
            else -> "좋음"
        }

        return MlPredictionResult(
            grade = grade,
            confidence = probabilities[maxIndex],
            probabilities = probabilities
        )
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = context.assets.openFd("answer_quality_model.tflite")
        return FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel

            fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    companion object {
        private const val MODEL_INPUT_SIZE = 6
        private const val MODEL_OUTPUT_SIZE = 3
    }
}
