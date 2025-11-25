package com.example.cookgram

import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class FeedAdapter(
    private val posts: MutableList<Post>
) : RecyclerView.Adapter<FeedAdapter.Holder>() {

    inner class Holder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.item_post, parent, false)) {

        val avatar: ImageView = itemView.findViewById(R.id.userImage)
        val name: TextView = itemView.findViewById(R.id.usernameText)
        val image: ImageView = itemView.findViewById(R.id.postImage)
        val caption: TextView = itemView.findViewById(R.id.captionText)
        val seeMore: TextView = itemView.findViewById(R.id.seeMoreText)
        val likeBtn: ImageButton = itemView.findViewById(R.id.likeButton)
        val commentBtn: ImageButton = itemView.findViewById(R.id.commentButton)
        val likesCount: TextView = itemView.findViewById(R.id.likesCountText)
        val moreBtn: ImageButton = itemView.findViewById(R.id.moreButton)
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }

    private fun decodeBase64(b64: String): ByteArray =
        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)


    private val avatarCache = mutableMapOf<String, String?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context), parent)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val p = posts[position]
        val ctx = h.itemView.context

        h.name.text = p.username ?: "User"
        h.caption.text = p.caption ?: ""


        val ownerId = p.userId
        fun loadFromBase64(b64: String?) {
            if (!b64.isNullOrBlank()) {
                Glide.with(h.itemView)
                    .load(decodeBase64(b64))
                    .centerCrop()
                    .into(h.avatar)
            } else {
                h.avatar.setImageResource(R.drawable.profile)
            }
        }

        when {
            !p.userImageBase64.isNullOrBlank() -> loadFromBase64(p.userImageBase64)
            !p.userImage.isNullOrBlank() ->
                Glide.with(h.itemView).load(p.userImage).centerCrop().into(h.avatar)
            !ownerId.isNullOrBlank() -> {
                val cached = avatarCache[ownerId]
                if (cached != null) {
                    loadFromBase64(cached)
                } else {
                    h.avatar.setImageResource(R.drawable.profile)
                    db.reference.child("Users").child(ownerId).get()
                        .addOnSuccessListener { snap ->
                            val b64 = snap.child("profileImageBase64")
                                .getValue(String::class.java)
                            avatarCache[ownerId] = b64
                            if (h.adapterPosition  == position) {
                                loadFromBase64(b64)
                            }
                        }
                }
            }
            else -> h.avatar.setImageResource(R.drawable.profile)
        }


        when {
            !p.imageBase64.isNullOrBlank() ->
                Glide.with(h.itemView).load(decodeBase64(p.imageBase64!!)).centerCrop().into(h.image)
            !p.postImage.isNullOrBlank() ->
                Glide.with(h.itemView).load(p.postImage).centerCrop().into(h.image)
            else -> h.image.setImageResource(R.drawable.samplefood)
        }


        val openProfile: () -> Unit = {
            if (!ownerId.isNullOrBlank()) {
                ctx.startActivity(
                    Intent(ctx, ProfileActivity::class.java)
                        .putExtra("userId", ownerId)
                )
            }
        }
        h.avatar.setOnClickListener { openProfile() }
        h.name.setOnClickListener { openProfile() }


        bindExpandableCaption(h.caption, h.seeMore)

        val postId = p.postId ?: return
        val uid = auth.currentUser?.uid
        val likeRef = db.getReference("Likes").child(postId)


        likeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val count = s.childrenCount
                h.likesCount.text = "$count likes"
            }
            override fun onCancelled(error: DatabaseError) {}
        })


        if (uid != null) {
            likeRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    h.likeBtn.setImageResource(
                        if (s.exists()) R.drawable.redlike else R.drawable.like
                    )
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }


        h.likeBtn.setOnClickListener {
            val me = auth.currentUser?.uid ?: return@setOnClickListener
            likeRef.child(me).get().addOnSuccessListener { s ->
                if (s.exists()) {
                    likeRef.child(me).removeValue()
                    h.likeBtn.setImageResource(R.drawable.like)
                } else {
                    likeRef.child(me).setValue(true)
                    h.likeBtn.setImageResource(R.drawable.redlike)
                }
            }
        }


        h.commentBtn.setOnClickListener {
            ctx.startActivity(
                Intent(ctx, CommentsActivity::class.java)
                    .putExtra("postId", postId)
            )
        }


        h.itemView.setOnClickListener {
            ctx.startActivity(
                Intent(ctx, PostActivity::class.java)
                    .putExtra("postId", postId)
            )
        }


        val me = auth.currentUser?.uid
        if (me != null && me == ownerId) {
            h.moreBtn.visibility = View.VISIBLE
            h.moreBtn.setOnClickListener {
                val options = arrayOf("Edit caption", "Delete post", "Cancel")
                AlertDialog.Builder(ctx)
                    .setItems(options) { d, which ->
                        when (which) {
                            0 -> editCaption(ctx, db, postId, p.caption ?: "")
                            1 -> deletePost(db, postId)
                        }
                        d.dismiss()
                    }
                    .show()
            }
        } else {
            h.moreBtn.visibility = View.GONE
        }
    }


    private fun bindExpandableCaption(caption: TextView, toggle: TextView) {
        caption.maxLines = Integer.MAX_VALUE
        caption.ellipsize = null

        caption.post {
            val overTwo = caption.layout != null && caption.layout.lineCount > 2
            if (overTwo) {
                caption.maxLines = 2
                caption.ellipsize = android.text.TextUtils.TruncateAt.END
                toggle.visibility = View.VISIBLE
                toggle.text = "See more"

                toggle.setOnClickListener {
                    val collapsed = caption.maxLines == 2
                    if (collapsed) {
                        caption.maxLines = Integer.MAX_VALUE
                        caption.ellipsize = null
                        toggle.text = "See less"
                    } else {
                        caption.maxLines = 2
                        caption.ellipsize = android.text.TextUtils.TruncateAt.END
                        toggle.text = "See more"
                    }
                }
            } else {
                toggle.visibility = View.GONE
            }
        }
    }

    private fun editCaption(
        ctx: android.content.Context,
        db: FirebaseDatabase,
        postId: String,
        current: String
    ) {
        val input = EditText(ctx).apply {
            setText(current)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(ctx)
            .setTitle("Edit caption")
            .setView(input)
            .setPositiveButton("Save") { dlg, _ ->
                val newText = input.text.toString().trim()
                db.getReference("Posts").child(postId).child("caption").setValue(newText)
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePost(db: FirebaseDatabase, postId: String) {
        db.getReference("Posts").child(postId).removeValue()
        db.getReference("Likes").child(postId).removeValue()
        db.getReference("Comments").child(postId).removeValue()
    }

    override fun getItemCount(): Int = posts.size
}
