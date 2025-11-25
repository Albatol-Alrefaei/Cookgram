package com.example.cookgram

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cookgram.databinding.ActivityCommentsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CommentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommentsBinding
    private val comments = mutableListOf<Comment>()
    private lateinit var adapter: CommentsAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }

    private var postId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        findViewById<ImageButton?>(R.id.backBtn)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val incoming = intent.getStringExtra("postId")
        if (incoming.isNullOrBlank()) {
            Toast.makeText(this, "Post not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        postId = incoming

        adapter = CommentsAdapter(comments, postId)
        binding.commentsRecycler.layoutManager = LinearLayoutManager(this)
        binding.commentsRecycler.adapter = adapter

        binding.sendCommentButton.setOnClickListener { addComment() }

        loadComments()
    }

    private fun addComment() {
        val text = binding.commentInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Write a comment", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Login required.", Toast.LENGTH_SHORT).show()
            return
        }

        db.reference.child("Users").child(uid).get()
            .addOnSuccessListener { u ->
                val username = u.child("username").getValue(String::class.java)
                    ?: auth.currentUser?.email?.substringBefore("@")
                    ?: "User"
                val avatarBase64 = u.child("profileImageBase64").getValue(String::class.java)
                val commentId = db.reference.child("Comments").child(postId).push().key
                    ?: System.currentTimeMillis().toString()
                val c = Comment(
                    id = commentId,
                    postId = postId,
                    userId = uid,
                    username = username,
                    userImageBase64 = avatarBase64,
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                db.reference.child("Comments").child(postId).child(commentId)
                    .setValue(c)
                    .addOnSuccessListener {
                        binding.commentInput.setText("")
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to comment: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadComments() {
        db.reference.child("Comments").child(postId).orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    comments.clear()
                    for (c in s.children) {
                        val item = c.getValue(Comment::class.java) ?: continue
                        comments.add(item)
                    }
                    comments.sortBy { it.timestamp ?: 0L }
                    adapter.notifyDataSetChanged()
                }
                override fun onCancelled(e: DatabaseError) {
                    Toast.makeText(this@CommentsActivity, "Failed to load comments.", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
