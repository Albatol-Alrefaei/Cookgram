package com.example.cookgram

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class FeedActivity : AppCompatActivity() {

    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var adapter: FeedAdapter
    private val posts = mutableListOf<Post>()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)

        feedRecyclerView = findViewById(R.id.feedRecyclerView)
        feedRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FeedAdapter(posts)
        feedRecyclerView.adapter = adapter

        setupBottomNav()
        loadFeed()
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<LinearLayout>(R.id.bottomNav)
        bottomNav.bringToFront()

        findViewById<ImageButton>(R.id.homeButton).setOnClickListener {
            loadFeed()
        }
        findViewById<ImageButton>(R.id.addPostButton).setOnClickListener {
            startActivity(Intent(this, AddPostActivity::class.java))
        }
        findViewById<ImageButton>(R.id.searchButton).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<ImageButton>(R.id.profileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun loadFeed() {
        val currentUid = auth.currentUser?.uid ?: return
        db.reference.child("Following").child(currentUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(followSnap: DataSnapshot) {
                    val visibleUserIds = mutableSetOf<String>()
                    visibleUserIds.add(currentUid)
                    for (child in followSnap.children) {
                        child.key?.let { visibleUserIds.add(it) }
                    }
                    loadPostsForUsers(visibleUserIds)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadPostsForUsers(userIds: Set<String>) {
        db.reference.child("Posts")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    posts.clear()
                    for (p in snapshot.children) {
                        val post = p.getValue(Post::class.java)?.copy(postId = p.key) ?: continue
                        if (post.userId != null && userIds.contains(post.userId)) {
                            posts.add(0, post)
                        }
                    }
                    adapter.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
