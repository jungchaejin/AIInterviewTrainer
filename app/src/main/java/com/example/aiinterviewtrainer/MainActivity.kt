package com.example.aiinterviewtrainer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aiinterviewtrainer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindAppHomeTitle()

        binding.btnJobSelect.setOnClickListener {
            val intent = Intent(this, JobSelectActivity::class.java)
            startActivity(intent)
        }

        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }
}
