package com.example.cookgram

data class Post(
    val postId: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val userImage: String? = null,
    val userImageBase64: String? = null,

    val postImage: String? = null,
    val imageBase64: String? = null,

    val caption: String? = null,
    val timestamp: Long? = null
)
