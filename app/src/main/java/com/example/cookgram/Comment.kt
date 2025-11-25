package com.example.cookgram

data class Comment(
    val id: String? = null,
    val postId: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val userImageBase64: String? = null,
    val text: String? = null,
    val timestamp: Long? = null
)
