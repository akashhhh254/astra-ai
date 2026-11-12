package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.error.OperationType
import com.example.core.error.handleFirestoreError
import com.example.data.model.Conversation
import com.example.data.model.GeneratedContent
import com.example.data.model.Message
import com.example.data.model.UserProfile
import com.example.data.model.UsageStat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AstraRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- User Profile ---

    suspend fun getUserProfile(userId: String): UserProfile? {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)

        return try {
            val existing = docRef.get().await()
            val photo = profile.photoUrl ?: profile.photoURL

            if (!existing.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "userId" to uid,
                    "displayName" to profile.displayName,
                    "email" to profile.email,
                    "provider" to profile.authProvider,
                    "authProvider" to profile.authProvider,
                    "createdAt" to (profile.createdAt ?: FieldValue.serverTimestamp()),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photo != null) {
                    payload["photoUrl"] = photo
                    payload["photoURL"] = photo
                }
                profile.bio?.let { payload["bio"] = it }

                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to profile.displayName,
                    "provider" to profile.authProvider,
                    "authProvider" to profile.authProvider,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photo != null) {
                    updates["photoUrl"] = photo
                    updates["photoURL"] = photo
                }
                profile.bio?.let { updates["bio"] = it }

                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Conversations ---

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        return db.collection("conversations")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Conversation::class.java).sortedByDescending { it.createdAt ?: it.updatedAt }
            }
    }

    suspend fun createConversation(title: String, modelUsed: String = "gemini-3.8-flash"): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("conversations").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title,
            "modelUsed" to modelUsed,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        val docRef = db.collection("conversations").document(conversationId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationTitle(conversationId: String, newTitle: String): Result<Unit> {
        val docRef = db.collection("conversations").document(conversationId)
        return try {
            docRef.update(
                mapOf(
                    "title" to newTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Messages ---

    fun observeMessages(userId: String, conversationId: String): Flow<List<Message>> {
        return db.collection("messages")
            .whereEqualTo("userId", userId)
            .whereEqualTo("conversationId", conversationId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Message::class.java).sortedBy { it.createdAt }
            }
    }

    suspend fun addMessage(conversationId: String, role: String, content: String): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("messages").document()
        val payload = mapOf(
            "conversationId" to conversationId,
            "userId" to uid,
            "role" to role,
            "content" to content,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Generated Content ---

    fun observeGeneratedContent(userId: String): Flow<List<GeneratedContent>> {
        return db.collection("generated_content")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(GeneratedContent::class.java).sortedByDescending { it.createdAt }
            }
    }

    suspend fun saveGeneratedContent(
        contentType: String,
        title: String,
        content: String,
        prompt: String
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("generated_content").document()
        val payload = mapOf(
            "userId" to uid,
            "contentType" to contentType,
            "title" to title,
            "content" to content,
            "prompt" to prompt,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            incrementUsage(uid)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteGeneratedContent(contentId: String): Result<Unit> {
        val docRef = db.collection("generated_content").document(contentId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Usage Stats ---

    fun observeUsageStat(userId: String): Flow<UsageStat?> {
        return db.collection("usage_stats").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toObject(UsageStat::class.java) else null
            }
    }

    suspend fun getUsageCount(userId: String): Long {
        val docRef = db.collection("usage_stats").document(userId)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) snapshot.getLong("totalRequests") ?: 0L else 0L
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun canPerformRequest(userId: String): Boolean {
        return getUsageCount(userId) < 2L
    }

    suspend fun incrementUsage(userId: String) {
        val docRef = db.collection("usage_stats").document(userId)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "totalRequests" to FieldValue.increment(1),
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "totalRequests" to 1L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
        }
    }
}
