package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class GeneratedContent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val contentType: String = "writing", // image, writing, resume, summary, code, scan
    val title: String = "",
    val content: String = "",
    val prompt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
