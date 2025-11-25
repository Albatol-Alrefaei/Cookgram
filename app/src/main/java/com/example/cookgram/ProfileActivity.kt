package com.example.cookgram

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var profilePicture: ImageView
    private lateinit var userName: TextView
    private lateinit var userBio: TextView
    private lateinit var followersCount: TextView
    private lateinit var followingCount: TextView
    private lateinit var actionButton: Button
    private lateinit var settingsButton: ImageButton
    private lateinit var emptyState: View
    private lateinit var postsRecycler: RecyclerView

    private val userPosts = mutableListOf<Post>()
    private lateinit var postsAdapter: ProfilePostsAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }

    private var viewingUserId: String? = null
    private var isOwnProfile = false

    companion object { private const val REQ_PICK_AVATAR = 501 }

    private fun decodeBase64(b64: String): ByteArray =
        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)

    private fun readBitmap(uri: Uri): Bitmap {
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open image" }
            return BitmapFactory.decodeStream(input)
        }
    }
    private fun scaleDown(src: Bitmap, maxDim: Int = 1024): Bitmap {
        val w = src.width; val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val s = kotlin.math.min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(src, (w*s).toInt().coerceAtLeast(1), (h*s).toInt().coerceAtLeast(1), true)
    }
    private fun bitmapToJpegBytes(bmp: Bitmap, q: Int = 85): ByteArray {
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, q, out); return out.toByteArray()
    }
    private fun bytesToBase64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        profilePicture = findViewById(R.id.profilePicture)
        userName = findViewById(R.id.userName)
        userBio = findViewById(R.id.userBio)
        followersCount = findViewById(R.id.followersCount)
        followingCount = findViewById(R.id.followingCount)
        actionButton = findViewById(R.id.followButton)
        settingsButton = findViewById(R.id.settingsButton)
        emptyState = findViewById(R.id.emptyState)
        postsRecycler = findViewById(R.id.postsRecycler)

        postsAdapter = ProfilePostsAdapter(userPosts)
        postsRecycler.layoutManager = LinearLayoutManager(this)
        postsRecycler.adapter = postsAdapter

        viewingUserId = intent.getStringExtra("userId")
        val currentUid = auth.currentUser?.uid
        isOwnProfile = viewingUserId == null || viewingUserId == currentUid
        val targetUid = viewingUserId ?: currentUid
        if (targetUid == null) { finish(); return }

        settingsButton.visibility = if (isOwnProfile) View.VISIBLE else View.GONE
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        if (isOwnProfile) {
            actionButton.text = "Edit Profile"
            actionButton.setOnClickListener { showEditProfileDialog() }
            profilePicture.isClickable = true
            profilePicture.setOnClickListener {
                val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                startActivityForResult(i, REQ_PICK_AVATAR)
            }
        } else {
            setupFollowButton(targetUid)
        }

        followersCount.setOnClickListener {
            startActivity(Intent(this, FollowListActivity::class.java)
                .putExtra("mode", "followers").putExtra("userId", targetUid))
        }
        followingCount.setOnClickListener {
            startActivity(Intent(this, FollowListActivity::class.java)
                .putExtra("mode", "following").putExtra("userId", targetUid))
        }

        loadUserHeader(targetUid)
        loadCounts(targetUid)
        loadUserPosts(targetUid)

        val bottomNav = findViewById<LinearLayout>(R.id.bottomNav)
        bottomNav.bringToFront()
        findViewById<ImageButton>(R.id.homeButton).setOnClickListener {
            startActivity(Intent(this, FeedActivity::class.java))
        }
        findViewById<ImageButton>(R.id.addPostButton).setOnClickListener {
            startActivity(Intent(this, AddPostActivity::class.java))
        }
        findViewById<ImageButton>(R.id.searchButton).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<ImageButton>(R.id.profileButton).setOnClickListener { }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_AVATAR && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            uploadAvatarBase64(uri)
        }
    }

    private fun loadUserHeader(uid: String) {
        db.reference.child("Users").child(uid).get().addOnSuccessListener { snap ->
            val name = snap.child("username").getValue(String::class.java)
                ?: auth.currentUser?.email?.substringBefore("@") ?: "User"

            val profileBase64 = snap.child("profileImageBase64").getValue(String::class.java)
            val bio = snap.child("bio").getValue(String::class.java) ?: ""

            userName.text = name
            userBio.text = bio

            when {
                !profileBase64.isNullOrBlank() -> Glide.with(this).load(decodeBase64(profileBase64)).centerCrop().into(profilePicture)
                else -> profilePicture.setImageResource(R.drawable.profile)
            }
        }
    }

    private fun loadCounts(uid: String) {
        db.reference.child("Followers").child(uid).get().addOnSuccessListener {
            followersCount.text = "${it.childrenCount} Followers"
        }
        db.reference.child("Following").child(uid).get().addOnSuccessListener {
            followingCount.text = "${it.childrenCount} Following"
        }
        if (!isOwnProfile) {
            val me = auth.currentUser?.uid
            if (me != null) {
                db.reference.child("Followers").child(uid).child(me).get().addOnSuccessListener { s ->
                    actionButton.text = if (s.exists()) "Unfollow" else "Follow"
                }
            }
        }
    }

    private fun setupFollowButton(targetUid: String) {
        if (isOwnProfile) return
        actionButton.setOnClickListener {
            val me = auth.currentUser?.uid ?: return@setOnClickListener
            val followersRef = db.reference.child("Followers").child(targetUid).child(me)
            val followingRef = db.reference.child("Following").child(me).child(targetUid)
            followersRef.get().addOnSuccessListener { snap ->
                if (snap.exists()) {
                    followersRef.removeValue(); followingRef.removeValue()
                    actionButton.text = "Follow"
                } else {
                    followersRef.setValue(true); followingRef.setValue(true)
                    actionButton.text = "Unfollow"
                }
                loadCounts(targetUid)
            }
        }
    }

    private fun loadUserPosts(uid: String) {
        db.reference.child("Posts").orderByChild("userId").equalTo(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userPosts.clear()
                    for (p in snapshot.children) {
                        val post = p.getValue(Post::class.java)?.copy(postId = p.key) ?: continue
                        userPosts.add(0, post)
                    }
                    postsAdapter.notifyDataSetChanged()
                    emptyState.visibility = if (userPosts.isEmpty()) View.VISIBLE else View.GONE
                    postsRecycler.visibility = if (userPosts.isEmpty()) View.GONE else View.VISIBLE
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun uploadAvatarBase64(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val dialog = ProgressDialog(this).apply { setCancelable(false); setMessage("Updating profile picture…"); show() }
        try {
            val bmp = readBitmap(uri)
            val scaled = scaleDown(bmp, 1024)
            val bytes = bitmapToJpegBytes(scaled, 85)
            val base64 = bytesToBase64(bytes)

            db.reference.child("Users").child(uid).child("profileImageBase64").setValue(base64)
                .addOnSuccessListener {
                    dialog.dismiss()
                    Glide.with(this).load(bytes).centerCrop().into(profilePicture)
                    Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    dialog.dismiss(); Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            dialog.dismiss(); Toast.makeText(this, "Image error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEditProfileDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 0)
        }
        val nameInput = EditText(this).apply { hint = "Username" }
        val bioInput = EditText(this).apply { hint = "Bio"; maxLines = 3 }
        container.addView(nameInput); container.addView(bioInput)

        val uid = auth.currentUser?.uid ?: return
        db.reference.child("Users").child(uid).get().addOnSuccessListener { s ->
            nameInput.setText(
                s.child("username").getValue(String::class.java)
                    ?: auth.currentUser?.email?.substringBefore("@") ?: ""
            )
            bioInput.setText(s.child("bio").getValue(String::class.java) ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(container)
            .setPositiveButton("Save") { d, _ ->
                val newName = nameInput.text.toString().trim()
                val newBio = bioInput.text.toString().trim()
                val updates = mapOf("username" to newName, "bio" to newBio)
                db.reference.child("Users").child(uid).updateChildren(updates).addOnSuccessListener {
                    userName.text = newName.ifBlank { auth.currentUser?.email?.substringBefore("@") ?: "User" }
                    userBio.text = newBio
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
