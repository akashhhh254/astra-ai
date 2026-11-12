package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AstraRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createConversation_authenticatedUser_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = AstraRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createConversation("Alpha Chat Session")
        }
        assertTrue(result.isSuccess)
        val convId = result.getOrThrow()
        assertNotNull(convId)
    }

    @Test
    fun addMessage_authenticatedUser_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = AstraRepository(firestore)

        val convResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createConversation("Alpha Chat Session")
        }
        val convId = convResult.getOrThrow()

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMessage(convId, "user", "Hello Astra AI!")
        }
        assertTrue(msgResult.isSuccess)
    }

    @Test
    fun observeConversations_returnsOwnerConversations() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = AstraRepository(firestore)

        val convResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createConversation("Alice's AI Chat")
        }
        val convId = convResult.getOrThrow()

        val conversations = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.observeConversations(aliceUid).first { list: List<Conversation> ->
                list.any { it.id == convId }
            }
        }
        assertTrue(conversations.any { it.id == convId })
    }

    @Test
    fun saveGeneratedContent_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = AstraRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveGeneratedContent(
                contentType = "writing",
                title = "Seed Pitch Email",
                content = "Dear Investor, ...",
                prompt = "Tone: Professional"
            )
        }
        assertTrue(result.isSuccess)
    }

    @Test
    fun saveUserProfile_authenticatedUser_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = AstraRepository(firestore)

        val profile = UserProfile(
            id = aliceUid,
            uid = aliceUid,
            userId = aliceUid,
            displayName = "Alice Explorer",
            email = ALICE_EMAIL,
            photoUrl = "https://example.com/avatar.jpg",
            photoURL = "https://example.com/avatar.jpg",
            provider = "google.com",
            authProvider = "google.com"
        )

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserProfile(profile)
        }
        if (saveResult.isFailure) {
            System.err.println("SAVE_USER_PROFILE_FAILED: " + saveResult.exceptionOrNull())
            saveResult.exceptionOrNull()?.printStackTrace()
        }
        assertTrue("Expected saveUserProfile to succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

        val retrieved = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserProfile(aliceUid)
        }
        assertNotNull(retrieved)
        assertTrue(retrieved?.displayName == "Alice Explorer")
        assertTrue(retrieved?.email == ALICE_EMAIL)
    }

    @Test
    fun unauthenticatedAccess_failsWithIllegalState() = runBlocking {
        auth.signOut()
        val repository = AstraRepository(firestore)

        try {
            repository.createConversation("Unauth Chat")
            fail("Expected IllegalStateException for unauthenticated user")
        } catch (e: IllegalStateException) {
            // Expected
            assertTrue(e.message?.contains("User must be signed in") == true)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_test@astraai.app"
        const val DEFAULT_TIMEOUT_MS = 6000L
    }
}
