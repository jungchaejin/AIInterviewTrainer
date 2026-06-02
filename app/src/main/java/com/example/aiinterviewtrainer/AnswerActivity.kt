package com.example.aiinterviewtrainer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log

class AnswerActivity : AppCompatActivity() {
    private val TAG = "GoogleSTT"

    private lateinit var questionTextView: TextView
    private lateinit var answerEditText: EditText
    private lateinit var listeningTextView: TextView
    private lateinit var speechButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val isRecording = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var audioBuffer = ByteArrayOutputStream()
    private var question: String = DEFAULT_QUESTION
    private var practiceId: String = ""
    private var answerStartedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_answer)

        question = intent.getStringExtra(EXTRA_QUESTION).orEmpty().ifBlank { DEFAULT_QUESTION }
        practiceId = intent.getStringExtra(EXTRA_PRACTICE_ID).orEmpty().ifBlank {
            System.currentTimeMillis().toString()
        }
        answerStartedAt = System.currentTimeMillis()

        questionTextView = findViewById(R.id.questionTextView)
        answerEditText = findViewById(R.id.answerEditText)
        listeningTextView = findViewById(R.id.listeningTextView)
        speechButton = findViewById(R.id.speechButton)

        questionTextView.text = question
        listeningTextView.text = "답변 준비 중"

        findViewById<TextView>(R.id.backTextView).setOnClickListener {
            finish()
        }

        speechButton.setOnClickListener {
            if (isRecording.get()) {
                stopRecordingAndTranscribe()
            } else {
                startGoogleSttRecording()
            }
        }

        findViewById<Button>(R.id.analyzeButton).setOnClickListener {
            openResult()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startGoogleSttRecording()
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            Toast.makeText(this, "음성 답변을 사용하려면 마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        stopRecordingOnly()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startGoogleSttRecording() {
        if (getGoogleSttApiKey().isBlank()) {
            Toast.makeText(this, "local.properties에 Google STT API 키를 입력해 주세요.", Toast.LENGTH_LONG).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            recorder = null
            Toast.makeText(this, "마이크를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        audioBuffer = ByteArrayOutputStream()
        isRecording.set(true)
        recorder?.startRecording()
        listeningTextView.text = "Listening..."
        speechButton.text = "답변 종료"

        executor.execute {
            val buffer = ByteArray(bufferSize)
            while (isRecording.get()) {
                val readCount = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (readCount > 0) {
                    audioBuffer.write(buffer, 0, readCount)
                }
            }
        }
    }

    private fun stopRecordingAndTranscribe() {
        stopRecordingOnly()

        val audioBytes = audioBuffer.toByteArray()
        Log.d(TAG, "audioBytes size = ${audioBytes.size}")

        if (audioBytes.isEmpty()) {
            listeningTextView.text = "답변 준비 중"
            speechButton.text = "답변하기"
            Toast.makeText(this, "녹음된 답변이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        listeningTextView.text = "답변 변환 중"
        speechButton.isEnabled = false

        executor.execute {
            val result = runCatching {
                requestGoogleSpeechToText(audioBytes)
            }

            mainHandler.post {
                speechButton.isEnabled = true
                speechButton.text = "답변하기"

                result.onSuccess { transcript ->
                    if (transcript.isBlank()) {
                        listeningTextView.text = "답변 준비 중"
                        Toast.makeText(this, "음성을 인식하지 못했습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        answerEditText.setText(transcript)
                        answerEditText.setSelection(transcript.length)
                        listeningTextView.text = "답변 입력 완료"
                    }
                }.onFailure { exception ->
                    listeningTextView.text = "답변 준비 중"
                    Toast.makeText(
                        this,
                        exception.message ?: "Google STT 변환에 실패했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun stopRecordingOnly() {
        if (!isRecording.getAndSet(false)) return

        runCatching {
            recorder?.stop()
        }
        recorder?.release()
        recorder = null
    }

    private fun requestGoogleSpeechToText(audioBytes: ByteArray): String {
        val url = URL("${GoogleSttConfig.ENDPOINT}?key=${getGoogleSttApiKey()}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val audioContent = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val requestBody = JSONObject()
            .put(
                "config",
                JSONObject()
                    .put("encoding", "LINEAR16")
                    .put("sampleRateHertz", SAMPLE_RATE)
                    .put("languageCode", "ko-KR")
                    .put("enableAutomaticPunctuation", true)
            )
            .put(
                "audio",
                JSONObject().put("content", audioContent)
            )

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestBody.toString())
        }

        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()

        Log.d(TAG, "Google STT responseCode = $responseCode")
        Log.d(TAG, "Google STT responseText = $responseText")

        if (responseCode !in 200..299) {
            throw IllegalStateException("Google STT 요청 실패: $responseText")
        }

        val results = JSONObject(responseText).optJSONArray("results") ?: return ""
        val alternatives = results
            .optJSONObject(0)
            ?.optJSONArray("alternatives")
            ?: return ""

        return alternatives
            .optJSONObject(0)
            ?.optString("transcript")
            .orEmpty()
    }

    private fun openResult() {
        val answer = answerEditText.text.toString().trim()

        if (answer.isBlank()) {
            Toast.makeText(this, "답변을 입력한 뒤 분석해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val elapsedSeconds = ((System.currentTimeMillis() - answerStartedAt) / 1000L)
            .coerceAtLeast(1L)
            .toInt()

        val resultIntent = Intent(this, ResultActivity::class.java).apply {
            putExtra(EXTRA_PRACTICE_ID, practiceId)
            putExtra(EXTRA_QUESTION, question)
            putExtra(EXTRA_ANSWER, answer)
            putExtra(EXTRA_ANSWER_SECONDS, elapsedSeconds)
        }

        startActivity(resultIntent)
    }

    private fun getGoogleSttApiKey(): String {
        return getString(R.string.google_stt_api_key).trim()
    }

    companion object {
        const val EXTRA_PRACTICE_ID = "extra_practice_id"
        const val EXTRA_QUESTION = "extra_question"
        const val EXTRA_ANSWER = "extra_answer"
        const val EXTRA_ANSWER_SECONDS = "extra_answer_seconds"

        private const val REQUEST_RECORD_AUDIO = 1001
        private const val SAMPLE_RATE = 16_000
        private const val DEFAULT_QUESTION = "HR 직무에서 가장 중요하다고 생각하는 역량은 무엇인가요?"
    }
}
