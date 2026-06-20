package com.example.aiinterviewtrainer // 본인 패키지명

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AnswerActivity : AppCompatActivity() {

    // 🌟 상대방이 올 때까지 내가 임시로 상수를 정의해 둡니다.
    companion object {
        const val EXTRA_PRACTICE_ID = "extra_practice_id"
        const val EXTRA_QUESTION = "extra_selected_question"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 텍스트뷰 하나만 있는 빈 화면을 띄우거나, 레이아웃 없이 비워두셔도 됩니다.
    }
}