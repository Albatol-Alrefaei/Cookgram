package com.example.cookgram

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var searchRecyclerView: RecyclerView
    private lateinit var adapter: SearchAdapter

    private val results = mutableListOf<SearchResult>()
    private val database by lazy { FirebaseDatabase.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        searchInput = findViewById(R.id.searchInput)
        searchRecyclerView = findViewById(R.id.searchRecyclerView)
        adapter = SearchAdapter(results, this)
        searchRecyclerView.layoutManager = LinearLayoutManager(this)
        searchRecyclerView.adapter = adapter

        val bottomNav = findViewById<LinearLayout>(R.id.bottomNav)
        bottomNav.bringToFront()

        findViewById<ImageButton>(R.id.homeButton).setOnClickListener {
            startActivity(Intent(this, FeedActivity::class.java))
        }
        findViewById<ImageButton>(R.id.addPostButton).setOnClickListener {
            startActivity(Intent(this, AddPostActivity::class.java))
        }
        findViewById<ImageButton>(R.id.searchButton).setOnClickListener {

        }
        findViewById<ImageButton>(R.id.profileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }


        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim().orEmpty()
                if (text.length < 3) {
                    results.clear()
                    adapter.updateResults(results)
                } else {
                    searchAll(text)
                }
            }
        })
    }


    private fun searchAll(keyword: String) {
        val lower = keyword.lowercase()
        results.clear()

        database.getReference("Users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(users: DataSnapshot) {
                    for (u in users.children) {
                        val uid = u.key ?: continue
                        val username = u.child("username").getValue(String::class.java)?.trim().orEmpty()
                        if (username.lowercase().contains(lower)) {
                            val img = u.child("profileImageBase64").getValue(String::class.java)
                                ?: u.child("profileImage").getValue(String::class.java)
                            results.add(
                                SearchResult(
                                    id = uid,
                                    name = username,
                                    type = "user",
                                    imageUrl = img,
                                    postId = null
                                )
                            )
                        }
                    }

                    database.getReference("Posts")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(posts: DataSnapshot) {
                                for (p in posts.children) {
                                    val pid = p.key ?: continue
                                    val caption = p.child("caption").getValue(String::class.java)?.trim().orEmpty()
                                    if (caption.lowercase().contains(lower)) {
                                        val img = p.child("imageBase64").getValue(String::class.java)
                                            ?: p.child("postImage").getValue(String::class.java)
                                        results.add(
                                            SearchResult(
                                                id = pid,
                                                name = caption,
                                                type = "recipe",
                                                imageUrl = img,
                                                postId = pid
                                            )
                                        )
                                    }
                                }


                                val sorted = results.sortedWith(
                                    compareBy<SearchResult> { if (it.type == "user") 0 else 1 }
                                        .thenBy { it.name?.lowercase() ?: "" }
                                )
                                adapter.updateResults(sorted)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                adapter.updateResults(results)
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }
}
