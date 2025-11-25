package com.example.cookgram

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CommentsAdapter(
    private val comments: MutableList<Comment>,
    private val postId: String
) : RecyclerView.Adapter<CommentsAdapter.Holder>() {

    inner class Holder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.item_comment, parent, false)) {

        val avatar: ImageView = itemView.findViewById(R.id.commentUserImage)
        val username: TextView = itemView.findViewById(R.id.commentUsername)
        val text: TextView = itemView.findViewById(R.id.commentText)
        val more: ImageButton = itemView.findViewById(R.id.commentMoreButton)
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }


    private val avatarCache = mutableMapOf<String, String?>()

    private fun decodeBase64(b64: String): ByteArray =
        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context), parent)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val c = comments[position]
        val ctx = h.itemView.context

        h.username.text = c.username ?: "User"
        h.text.text = c.text ?: ""


        val userId = c.userId
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

            !c.userImageBase64.isNullOrBlank() -> loadFromBase64(c.userImageBase64)

            !userId.isNullOrBlank() -> {
                val cached = avatarCache[userId]
                if (cached != null) {
                    loadFromBase64(cached)
                } else {
                    h.avatar.setImageResource(R.drawable.profile)
                    db.reference.child("Users").child(userId).get()
                        .addOnSuccessListener { snap ->
                            val b64 = snap.child("profileImageBase64")
                                .getValue(String::class.java)
                            avatarCache[userId] = b64
                            // make sure we’re still binding the same row
                            if (h.adapterPosition  == position) {
                                loadFromBase64(b64)
                            }
                        }
                }
            }

            else -> h.avatar.setImageResource(R.drawable.profile)
        }


        val openProfile: () -> Unit = {
            if (!userId.isNullOrBlank()) {
                ctx.startActivity(
                    Intent(ctx, ProfileActivity::class.java)
                        .putExtra("userId", userId)
                )
            }
        }
        h.avatar.setOnClickListener { openProfile() }
        h.username.setOnClickListener { openProfile() }


        val me = auth.currentUser?.uid
        if (me != null && me == userId) {
            h.more.visibility = View.VISIBLE
            h.more.setOnClickListener { showMoreMenu(ctx, c) }
        } else {
            h.more.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = comments.size

    private fun showMoreMenu(ctx: Context, comment: Comment) {
        val options = arrayOf("Edit", "Delete", "Cancel")
        AlertDialog.Builder(ctx)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> editComment(ctx, comment)
                    1 -> deleteComment(comment)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun editComment(ctx: Context, comment: Comment) {
        val input = EditText(ctx).apply {
            setText(comment.text ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        AlertDialog.Builder(ctx)
            .setTitle("Edit comment")
            .setView(input)
            .setPositiveButton("Save") { dlg, _ ->
                val newText = input.text.toString().trim()
                val id = comment.id ?: return@setPositiveButton
                db.reference.child("Comments")
                    .child(postId)
                    .child(id)
                    .child("text")
                    .setValue(newText)
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteComment(comment: Comment) {
        val id = comment.id ?: return
        db.reference.child("Comments")
            .child(postId)
            .child(id)
            .removeValue()
    }
}
