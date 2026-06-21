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

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupRecyclerView()

        loadHistoryDataFromFirebase()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { clickedItem ->
            // 🌟 아이템 클릭 시, 고유 ID(practiceId)만 Intent에 실어서 QuestionActivity로 이동!
            val intent = Intent(this@HistoryActivity, QuestionActivity::class.java).apply {
                putExtra(AnswerActivity.EXTRA_PRACTICE_ID, clickedItem.id)
            }
            startActivity(intent)
        }

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = historyAdapter
        }
    }

    private fun loadHistoryDataFromFirebase() {
        // (선택) 로딩바 켜기
        // binding.progressBar.visibility = View.VISIBLE

        // History 컬렉션에서 데이터 가져오기 (practiceDate 또는 practiceId 기준으로 최신순 정렬)
        firestore.collection("History")
            .orderBy("practiceId", Query.Direction.DESCENDING) // 최신 면접 기록이 위로 오게 정렬
            .get()
            .addOnSuccessListener { result ->
                val historyList = mutableListOf<HistoryItem>()

                // 파이어베이스에서 가져온 문서를 하나씩 돌면서 HistoryItem 객체로 변환
                for (document in result) {
                    val id = document.getString("practiceId") ?: document.id
                    val title = document.getString("jobTitle") ?: "제목 없음"
                    val date = document.getString("practiceDate") ?: ""

                    historyList.add(HistoryItem(id, title, date))
                }

                // 어댑터에 데이터 리스트 전달하여 화면 업데이트
                historyAdapter.submitList(historyList)

                // (선택) 로딩바 끄기
                // binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                // binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "기록을 불러오지 못했습니다: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
