package com.example.aiinterviewtrainer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aiinterviewtrainer.databinding.ActivityHistoryBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyAdapter: HistoryAdapter
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindAppHomeTitle()

        binding.btnBack.setOnClickListener { finish() }
        setupRecyclerView()
        loadHistoryDataFromFirebase()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { item ->
            startActivity(Intent(this, QuestionActivity::class.java).apply {
                putExtra(AnswerActivity.EXTRA_PRACTICE_ID, item.id)
            })
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
    }

    private fun loadHistoryDataFromFirebase() {
        firestore.collection("History")
            .orderBy("practiceId", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                historyAdapter.submitList(result.map { document ->
                    HistoryItem(
                        id = document.getString("practiceId") ?: document.id,
                        title = document.getString("jobTitle")
                            ?: getString(R.string.missing_history_title),
                        date = document.getString("practiceDate").orEmpty()
                    )
                })
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    getString(R.string.history_load_failed, exception.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
