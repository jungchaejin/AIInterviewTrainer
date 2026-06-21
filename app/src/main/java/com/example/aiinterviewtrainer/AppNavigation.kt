package com.example.aiinterviewtrainer

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.bindAppHomeTitle() {
    findViewById<View>(R.id.appHomeTextView).setOnClickListener {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }
}
