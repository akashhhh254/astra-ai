package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val userId: String = "",
    val role: String = "user", // "user" or "model"
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
