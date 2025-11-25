package com.example.cookgram

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PostActivity : AppCompatActivity() {

    private lateinit var userImage: ImageView
    private lateinit var usernameText: TextView
    private lateinit var postImage: ImageView
    private lateinit var captionText: TextView
    private lateinit var seeMoreText: TextView
    private lateinit var likeButton: ImageButton
    private lateinit var commentButton: ImageButton
    private lateinit var likesCountText: TextView
    private lateinit var moreButton: ImageButton

    private val db by lazy { FirebaseDatabase.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private fun decodeBase64(b64: String): ByteArray =
        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)

    private var postId: String = ""
    private var postOwnerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post)

        findViewById<ImageButton?>(R.id.backBtn)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        userImage = findViewById(R.id.userImage)
        usernameText = findViewById(R.id.usernameText)
        postImage = findViewById(R.id.postImage)
        captionText = findViewById(R.id.captionText)
        seeMoreText = findViewById(R.id.seeMoreText)
        likeButton = findViewById(R.id.likeButton)
        commentButton = findViewById(R.id.commentButton)
        likesCountText = findViewById(R.id.likesCountText)
        moreButton = findViewById(R.id.moreButton)

        val incoming = intent.getStringExtra("postId")
        if (incoming.isNullOrBlank()) {
            finish(); return
        }
        postId = incoming

        loadPost()
        wireButtons()
        listenLikeCount()
    }

    private fun listenLikeCount() {
        db.getReference("Likes").child(postId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    likesCountText.text = "${s.childrenCount} likes"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun wireButtons() {
        val uid = auth.currentUser?.uid
        val likeRef = db.getReference("Likes").child(postId)


        if (uid != null) {
            likeRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    likeButton.setImageResource(
                        if (s.exists()) R.drawable.redlike else R.drawable.like
                    )
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        likeButton.setOnClickListener {
            val me = auth.currentUser?.uid ?: return@setOnClickListener
            likeRef.child(me).get().addOnSuccessListener { s ->
                if (s.exists()) {
                    likeRef.child(me).removeValue()
                    likeButton.setImageResource(R.drawable.like)
                } else {
                    likeRef.child(me).setValue(true)
                    likeButton.setImageResource(R.drawable.redlike)
                }
            }
        }

        commentButton.setOnClickListener {
            startActivity(
                Intent(this, CommentsActivity::class.java)
                    .putExtra("postId", postId)
            )
        }


        val openProfile = {
            val owner = postOwnerId
            if (!owner.isNullOrBlank()) {
                startActivity(
                    Intent(this, ProfileActivity::class.java)
                        .putExtra("userId", owner)
                )
            }
        }
        userImage.setOnClickListener { openProfile() }
        usernameText.setOnClickListener { openProfile() }


        moreButton.setOnClickListener {
            val me = auth.currentUser?.uid
            if (me != null && me == postOwnerId) {
                val options = arrayOf("Edit caption", "Delete post", "Cancel")
                AlertDialog.Builder(this)
                    .setItems(options) { dlg, which ->
                        when (which) {
                            0 -> editCaption(captionText.text.toString())
                            1 -> deletePost()
                        }
                        dlg.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun editCaption(current: String) {
        val input = EditText(this).apply {
            setText(current)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this)
            .setTitle("Edit caption")
            .setView(input)
            .setPositiveButton("Save") { dlg, _ ->
                val newText = input.text.toString().trim()
                db.getReference("Posts").child(postId).child("caption").setValue(newText)
                captionText.text = newText
                bindExpandableCaption()
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePost() {
        db.getReference("Posts").child(postId).removeValue()
        db.getReference("Likes").child(postId).removeValue()
        db.getReference("Comments").child(postId).removeValue()
        Toast.makeText(this, "Post deleted", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun loadPost() {
        db.reference.child("Posts").child(postId).get()
            .addOnSuccessListener { p ->
                postOwnerId = p.child("userId").getValue(String::class.java)

                val username = p.child("username").getValue(String::class.java) ?: "User"
                val avatarUrl = p.child("userImage").getValue(String::class.java)
                val avatarB64 = p.child("userImageBase64").getValue(String::class.java)
                val postUrl = p.child("postImage").getValue(String::class.java)
                val postB64 = p.child("imageBase64").getValue(String::class.java)
                val caption = p.child("caption").getValue(String::class.java) ?: ""

                usernameText.text = username
                captionText.text = caption


                when {
                    !avatarB64.isNullOrBlank() ->
                        Glide.with(this).load(decodeBase64(avatarB64)).centerCrop().into(userImage)
                    !avatarUrl.isNullOrBlank() ->
                        Glide.with(this).load(avatarUrl).centerCrop().into(userImage)
                    !postOwnerId.isNullOrBlank() -> {
                        db.reference.child("Users").child(postOwnerId!!).get()
                            .addOnSuccessListener { u ->
                                val b64 = u.child("profileImageBase64").getValue(String::class.java)
                                if (!b64.isNullOrBlank()) {
                                    Glide.with(this).load(decodeBase64(b64))
                                        .centerCrop().into(userImage)
                                } else {
                                    userImage.setImageResource(R.drawable.profile)
                                }
                            }
                            .addOnFailureListener {
                                userImage.setImageResource(R.drawable.profile)
                            }
                    }
                    else -> userImage.setImageResource(R.drawable.profile)
                }

                when {
                    !postB64.isNullOrBlank() ->
                        Glide.with(this).load(decodeBase64(postB64)).centerCrop().into(postImage)
                    !postUrl.isNullOrBlank() ->
                        Glide.with(this).load(postUrl).centerCrop().into(postImage)
                    else -> postImage.setImageResource(R.drawable.samplefood)
                }

                val me = auth.currentUser?.uid
                moreButton.visibility =
                    if (me != null && me == postOwnerId) ImageButton.VISIBLE else ImageButton.GONE

                bindExpandableCaption()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load post.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun bindExpandableCaption() {
        captionText.maxLines = Integer.MAX_VALUE
        captionText.ellipsize = null

        captionText.post {
            val overTwo = captionText.layout != null && captionText.layout.lineCount > 2
            if (overTwo) {
                captionText.maxLines = 2
                captionText.ellipsize = android.text.TextUtils.TruncateAt.END
                seeMoreText.visibility = TextView.VISIBLE
                seeMoreText.text = "See more"

                seeMoreText.setOnClickListener {
                    val collapsed = captionText.maxLines == 2
                    if (collapsed) {
                        captionText.maxLines = Integer.MAX_VALUE
                        captionText.ellipsize = null
                        seeMoreText.text = "See less"
                    } else {
                        captionText.maxLines = 2
                        captionText.ellipsize = android.text.TextUtils.TruncateAt.END
                        seeMoreText.text = "See more"
                    }
                }
            } else {
                seeMoreText.visibility = TextView.GONE
            }
        }
    }
}
