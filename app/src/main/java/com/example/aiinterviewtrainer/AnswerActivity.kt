package com.example.aiinterviewtrainer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AnswerActivity : AppCompatActivity() {
    private lateinit var questionTextView: TextView
    private lateinit var answerEditText: EditText
    private lateinit var listeningTextView: TextView
    private lateinit var speechButton: Button
    private lateinit var interviewerImageView: ImageView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val isRecording = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var audioBuffer = ByteArrayOutputStream()
    private val audioBufferLock = Any()
    private var question: String = DEFAULT_QUESTION
    private var questionType: String = ""
    private var expectedKeywords: List<String> = emptyList()
    private var evaluationPoints: List<String> = emptyList()
    private var practiceId: String = ""
    private var questionId: String = ""
    private var answerStartedAt: Long = 0L

    private val interviewerImagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) saveAndDisplayInterviewerImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_answer)
        bindAppHomeTitle()

        question = intent.getStringExtra(EXTRA_QUESTION).orEmpty().ifBlank {
            DEFAULT_QUESTION
        }
        practiceId = intent.getStringExtra(EXTRA_PRACTICE_ID).orEmpty().ifBlank {
            System.currentTimeMillis().toString()
        }
        questionId = intent.getStringExtra(EXTRA_QUESTION_ID).orEmpty()
        questionType = intent.getStringExtra(EXTRA_QUESTION_TYPE).orEmpty()
        expectedKeywords = intent.getStringArrayListExtra(EXTRA_EXPECTED_KEYWORDS).orEmpty()
        evaluationPoints = intent.getStringArrayListExtra(EXTRA_EVALUATION_POINTS).orEmpty()
        answerStartedAt = System.currentTimeMillis()

        questionTextView = findViewById(R.id.questionTextView)
        answerEditText = findViewById(R.id.answerEditText)
        listeningTextView = findViewById(R.id.listeningTextView)
        speechButton = findViewById(R.id.speechButton)
        interviewerImageView = findViewById(R.id.interviewerImageView)

        questionTextView.text = question
        listeningTextView.text = "답변 준비 중"
        loadSavedInterviewerImage()

        val openImagePicker = {
            interviewerImagePicker.launch(arrayOf("image/*"))
        }
        interviewerImageView.setOnClickListener { openImagePicker() }
        findViewById<ImageView>(R.id.changeInterviewerImageButton).setOnClickListener {
            openImagePicker()
        }

        findViewById<android.widget.ImageView>(R.id.backTextView).setOnClickListener {
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

        synchronized(audioBufferLock) {
            audioBuffer = ByteArrayOutputStream()
        }
        isRecording.set(true)
        recorder?.startRecording()
        listeningTextView.text = "Listening..."
        speechButton.text = "답변 종료"

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording.get()) {
                val readCount = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (readCount > 0) {
                    synchronized(audioBufferLock) {
                        audioBuffer.write(buffer, 0, readCount)
                    }
                }
            }
        }.apply {
            name = "GoogleSttRecorder"
            start()
        }
    }

    private fun saveAndDisplayInterviewerImage(uri: Uri) {
        val preferences = getSharedPreferences(PREFS_INTERVIEWER, MODE_PRIVATE)
        val previousUri = preferences.getString(KEY_INTERVIEWER_URI, null)

        if (!previousUri.isNullOrBlank() && previousUri != uri.toString()) {
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    Uri.parse(previousUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        preferences.edit().putString(KEY_INTERVIEWER_URI, uri.toString()).apply()
        displayInterviewerImage(uri, showFailureMessage = true)
    }

    private fun loadSavedInterviewerImage() {
        val uriText = getSharedPreferences(PREFS_INTERVIEWER, MODE_PRIVATE)
            .getString(KEY_INTERVIEWER_URI, null)
            .orEmpty()
        if (uriText.isNotBlank()) {
            displayInterviewerImage(Uri.parse(uriText), showFailureMessage = false)
        }
    }

    private fun displayInterviewerImage(uri: Uri, showFailureMessage: Boolean) {
        lifecycleScope.launch {
            val drawableResult = runCatching {
                withContext(Dispatchers.IO) {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                        val width = info.size.width
                        val height = info.size.height
                        val longestEdge = maxOf(width, height)
                        if (longestEdge > MAX_INTERVIEWER_IMAGE_SIZE) {
                            val scale = MAX_INTERVIEWER_IMAGE_SIZE.toFloat() / longestEdge.toFloat()
                            decoder.setTargetSize(
                                (width * scale).toInt().coerceAtLeast(1),
                                (height * scale).toInt().coerceAtLeast(1)
                            )
                        }
                    }
                }
            }

            drawableResult.onSuccess { drawable ->
                interviewerImageView.setImageDrawable(drawable)
            }.onFailure {
                getSharedPreferences(PREFS_INTERVIEWER, MODE_PRIVATE)
                    .edit()
                    .remove(KEY_INTERVIEWER_URI)
                    .apply()
                interviewerImageView.setImageResource(R.drawable.interviewer)
                if (showFailureMessage) {
                    Toast.makeText(
                        this@AnswerActivity,
                        "선택한 사진을 불러올 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun stopRecordingAndTranscribe() {
        stopRecordingOnly()

        val audioBytes = synchronized(audioBufferLock) {
            audioBuffer.toByteArray()
        }
        val audioStats = analyzePcmAudio(audioBytes)
        Log.d(TAG, "audioBytes size = ${audioBytes.size}")
        Log.d(
            TAG,
            "audio duration=${audioStats.durationSeconds}s rms=${audioStats.rms} peak=${audioStats.peak}"
        )

        if (audioBytes.isEmpty()) {
            listeningTextView.text = "답변 준비 중"
            speechButton.text = "답변하기"
            Toast.makeText(this, "녹음된 답변이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (audioStats.durationSeconds < MIN_AUDIO_SECONDS) {
            listeningTextView.text = "답변 준비 중"
            speechButton.text = "답변하기"
            Toast.makeText(this, "답변을 조금 더 길게 녹음해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (audioStats.peak < MIN_PEAK_AMPLITUDE) {
            listeningTextView.text = "답변 준비 중"
            speechButton.text = "답변하기"
            Toast.makeText(this, "소리가 너무 작게 녹음됐습니다. 마이크 가까이에서 말해 주세요.", Toast.LENGTH_SHORT).show()
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
                        exception.message ?: "텍스트 변환에 실패했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun stopRecordingOnly() {
        if (!isRecording.getAndSet(false)) return

        val activeRecorder = recorder
        runCatching {
            activeRecorder?.stop()
        }

        runCatching {
            recordingThread?.join(RECORDING_THREAD_JOIN_TIMEOUT_MS)
        }

        activeRecorder?.release()
        recorder = null
        recordingThread = null
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
                    .put("audioChannelCount", 1)
                    .put("languageCode", "ko-KR")
                    .put("model", "latest_long")
                    .put("maxAlternatives", 1)
                    .put("enableAutomaticPunctuation", true)
            )
            .put("audio", JSONObject().put("content", audioContent))

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

    private fun analyzePcmAudio(audioBytes: ByteArray): AudioStats {
        if (audioBytes.size < BYTES_PER_SAMPLE) {
            return AudioStats(durationSeconds = 0f, rms = 0.0, peak = 0)
        }

        var sumSquares = 0.0
        var peak = 0
        var sampleCount = 0
        var index = 0

        while (index + 1 < audioBytes.size) {
            val low = audioBytes[index].toInt() and 0xFF
            val high = audioBytes[index + 1].toInt()
            val sample = (high shl 8) or low
            val amplitude = kotlin.math.abs(sample)

            if (amplitude > peak) {
                peak = amplitude
            }

            sumSquares += sample.toDouble() * sample.toDouble()
            sampleCount++
            index += BYTES_PER_SAMPLE
        }

        val rms = if (sampleCount == 0) {
            0.0
        } else {
            sqrt(sumSquares / sampleCount.toDouble())
        }
        val durationSeconds = sampleCount.toFloat() / SAMPLE_RATE.toFloat()

        return AudioStats(
            durationSeconds = durationSeconds,
            rms = rms,
            peak = peak
        )
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
            putExtra(EXTRA_QUESTION_ID, questionId)
            putExtra(EXTRA_QUESTION, question)
            putExtra(EXTRA_QUESTION_TYPE, questionType)
            putStringArrayListExtra(EXTRA_EXPECTED_KEYWORDS, ArrayList(expectedKeywords))
            putStringArrayListExtra(EXTRA_EVALUATION_POINTS, ArrayList(evaluationPoints))
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
        const val EXTRA_QUESTION_ID = "extra_question_id"
        const val EXTRA_QUESTION = "extra_question"
        const val EXTRA_QUESTION_TYPE = "extra_question_type"
        const val EXTRA_EXPECTED_KEYWORDS = "extra_expected_keywords"
        const val EXTRA_EVALUATION_POINTS = "extra_evaluation_points"
        const val EXTRA_ANSWER = "extra_answer"
        const val EXTRA_ANSWER_SECONDS = "extra_answer_seconds"

        private const val TAG = "GoogleSTT"
        private const val REQUEST_RECORD_AUDIO = 1001
        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val MIN_AUDIO_SECONDS = 1.0f
        private const val MIN_PEAK_AMPLITUDE = 500
        private const val RECORDING_THREAD_JOIN_TIMEOUT_MS = 1_000L
        private const val PREFS_INTERVIEWER = "interviewer_image_prefs"
        private const val KEY_INTERVIEWER_URI = "interviewer_image_uri"
        private const val MAX_INTERVIEWER_IMAGE_SIZE = 1_024
        private const val DEFAULT_QUESTION = "HR 직무에서 가장 중요하다고 생각하는 역량은 무엇인가요?"
    }

    private data class AudioStats(
        val durationSeconds: Float,
        val rms: Double,
        val peak: Int
    )
}
