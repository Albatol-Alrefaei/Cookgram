package com.example.cookgram

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SearchAdapter(
    private val items: MutableList<SearchResult>,
    private val activity: android.app.Activity
) : RecyclerView.Adapter<SearchAdapter.Holder>() {

    inner class Holder(inflater: LayoutInflater, parent: ViewGroup)
        : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_search_result, parent, false)) {
        val image: ImageView = itemView.findViewById(R.id.resultImage)
        val name: TextView = itemView.findViewById(R.id.resultName)
        val type: TextView = itemView.findViewById(R.id.resultType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context), parent)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val item = items[position]

        h.name.text = item.name ?: ""
        h.type.text = when (item.type?.lowercase()) {
            "user" -> "User"
            "recipe" -> "Recipe"
            else -> item.type ?: ""
        }

        val img = item.imageUrl
        if (!img.isNullOrBlank()) {
            val bytes = tryDecodeBase64(img)
            if (bytes != null) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                Glide.with(h.itemView).load(bmp).centerCrop().into(h.image)
            } else {
                Glide.with(h.itemView).load(img).centerCrop().into(h.image)
            }
        } else {
            if (item.type.equals("user", true)) {
                h.image.setImageResource(R.drawable.profile)
            } else {
                h.image.setImageResource(R.drawable.samplefood)
            }
        }

        h.itemView.setOnClickListener {
            when (item.type?.lowercase()) {
                "user" -> {
                    val intent = Intent(activity, ProfileActivity::class.java)
                        .putExtra("userId", item.id)
                    activity.startActivity(intent)
                }
                "recipe" -> {
                    val intent = Intent(activity, PostActivity::class.java)
                        .putExtra("postId", item.postId ?: item.id)
                    activity.startActivity(intent)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateResults(newItems: List<SearchResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun tryDecodeBase64(input: String): ByteArray? {
        return try { Base64.decode(input, Base64.DEFAULT) } catch (_: IllegalArgumentException) { null }
    }
}
