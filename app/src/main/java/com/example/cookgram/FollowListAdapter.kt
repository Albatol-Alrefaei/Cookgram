package com.example.cookgram

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class FollowListAdapter(
    private val items: MutableList<FollowListActivity.UserLite>,
    private val currentUserId: String?,
    private val onToggle: (userId: String) -> Unit
) : RecyclerView.Adapter<FollowListAdapter.Holder>() {

    inner class Holder(inflater: LayoutInflater, parent: ViewGroup)
        : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_user_row, parent, false)) {
        val avatar: ImageView = itemView.findViewById(R.id.userImage)
        val name: TextView = itemView.findViewById(R.id.userName)
        val action: Button = itemView.findViewById(R.id.actionButton)
    }

    private fun decodeBase64(b64: String): ByteArray =
        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)


    private val followingIds: MutableSet<String> = mutableSetOf()

    fun setFollowingIds(newIds: Set<String>) {
        followingIds.clear()
        followingIds.addAll(newIds)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context), parent)

    override fun onBindViewHolder(h: Holder, position: Int) {
        val u = items[position]
        val ctx = h.itemView.context

        h.name.text = u.username

        if (!u.profileImageBase64.isNullOrBlank()) {
            Glide.with(h.itemView).load(decodeBase64(u.profileImageBase64!!)).centerCrop().into(h.avatar)
        } else {
            h.avatar.setImageResource(R.drawable.profile)
        }


        h.itemView.setOnClickListener {
            val intent = Intent(ctx, ProfileActivity::class.java)
                .putExtra("userId", u.uid)
            ctx.startActivity(intent)
        }


        if (currentUserId != null && u.uid == currentUserId) {
            h.action.visibility = View.GONE
            h.action.setOnClickListener(null)
            return
        }


        if (currentUserId == null) {
            h.action.visibility = View.GONE
            h.action.setOnClickListener(null)
            return
        }

        h.action.visibility = View.VISIBLE


        val isFollowing = followingIds.contains(u.uid)
        h.action.text = if (isFollowing) "Unfollow" else "Follow"

        h.action.setOnClickListener {

            onToggle(u.uid)


            if (followingIds.contains(u.uid)) {
                followingIds.remove(u.uid)
            } else {
                followingIds.add(u.uid)
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = items.size
}
