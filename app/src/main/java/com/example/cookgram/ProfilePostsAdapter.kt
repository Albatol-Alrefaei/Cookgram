package com.example.cookgram

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ProfilePostsAdapter(
    private val posts: MutableList<Post>
) : RecyclerView.Adapter<ProfilePostsAdapter.Holder>() {

    inner class Holder(inflater: LayoutInflater, parent: ViewGroup)
        : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_profile_post, parent, false)) {
        val image: ImageView = itemView.findViewById(R.id.postImage)
    }

    private fun decodeBase64(base64: String): ByteArray =
        android.util.Base64.decode(base64, android.util.Base64.DEFAULT)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context), parent)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val p = posts[position]

        when {
            !p.imageBase64.isNullOrBlank() ->
                Glide.with(h.itemView).load(decodeBase64(p.imageBase64!!)).into(h.image)
            !p.postImage.isNullOrBlank() ->
                Glide.with(h.itemView).load(p.postImage).into(h.image)
            else -> h.image.setImageResource(R.drawable.samplefood)
        }

        h.itemView.setOnClickListener {
            val ctx = h.itemView.context
            ctx.startActivity(Intent(ctx, PostActivity::class.java).putExtra("postId", p.postId))
        }
    }

    override fun getItemCount(): Int = posts.size
}
