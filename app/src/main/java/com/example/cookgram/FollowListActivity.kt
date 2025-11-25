package com.example.cookgram

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cookgram.databinding.ActivityFollowListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class FollowListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFollowListBinding
    private val users = mutableListOf<UserLite>()
    private lateinit var adapter: FollowListAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }

    private var mode: String? = null
    private var targetUid: String? = null
    private var currentUserId: String? = null

    data class UserLite(
        val uid: String = "",
        val username: String = "",
        val profileImageBase64: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFollowListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        findViewById<ImageButton?>(R.id.backBtn)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        currentUserId = auth.currentUser?.uid
        mode = intent.getStringExtra("mode")
        targetUid = intent.getStringExtra("userId") ?: currentUserId

        binding.followRecycler.layoutManager = LinearLayoutManager(this)
        adapter = FollowListAdapter(users, currentUserId, ::toggleFollow)
        binding.followRecycler.adapter = adapter


        binding.followTitle?.text = if (mode == "followers") "Followers" else "Following"

        loadList()
    }

    private fun loadList() {
        val uid = targetUid ?: return
        val ref = if (mode == "followers")
            db.reference.child("Followers").child(uid)
        else
            db.reference.child("Following").child(uid)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val ids = s.children.mapNotNull { it.key }
                if (ids.isEmpty()) {
                    users.clear()
                    adapter.notifyDataSetChanged()
                    adapter.setFollowingIds(emptySet())
                    return
                }

                db.reference.child("Users").get().addOnSuccessListener { all ->
                    users.clear()
                    for (id in ids) {
                        val u = all.child(id)
                        val name = u.child("username").getValue(String::class.java)
                            ?: u.child("fullName").getValue(String::class.java)
                            ?: "User"
                        val b64 = u.child("profileImageBase64").getValue(String::class.java)
                        users.add(UserLite(uid = id, username = name, profileImageBase64 = b64))
                    }
                    adapter.notifyDataSetChanged()
                    loadCurrentFollowing()
                }
            }

            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun loadCurrentFollowing() {
        val me = currentUserId ?: return
        db.reference.child("Following").child(me)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val set = s.children.mapNotNull { it.key }.toSet()
                    adapter.setFollowingIds(set)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun toggleFollow(userId: String) {
        val me = auth.currentUser?.uid ?: return
        if (userId == me) return
        val followersRef = db.reference.child("Followers").child(userId).child(me)
        val followingRef = db.reference.child("Following").child(me).child(userId)
        followersRef.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                followersRef.removeValue()
                followingRef.removeValue()
            } else {
                followersRef.setValue(true)
                followingRef.setValue(true)
            }
        }
    }
}
