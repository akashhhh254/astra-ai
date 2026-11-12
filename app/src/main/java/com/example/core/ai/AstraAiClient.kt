package com.example.core.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "AstraAiClient"
private const val DEFAULT_TIMEOUT_MS = 60_000L

class AstraAiClient {

    // General purpose flash model
    private val flashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    // Fast light model
    private val liteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite"
        )
    }

    // 1. Streaming chat response with multi-turn history
    fun streamChat(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<String> = flow {
        val chatHistory = history.map { (role: String, text: String) ->
            Content(
                role = if (role == "user") "user" else "model",
                parts = listOf(TextPart(text))
            )
        }

        val chat = flashModel.startChat(chatHistory)
        val personalization = com.example.data.local.AstraPreferences.settings.value
        val effectiveMessage = if (personalization.language != "English" || personalization.responseTone != "Professional" || personalization.responseLength != "Balanced") {
            "[Instructions: Reply in ${personalization.language}, with a ${personalization.responseTone.lowercase()} tone and ${personalization.responseLength.lowercase()} length]\n$userMessage"
        } else {
            userMessage
        }

        val responseStream = withTimeout(DEFAULT_TIMEOUT_MS) {
            chat.sendMessageStream(effectiveMessage)
        }

        responseStream.collect { chunk ->
            val text = chunk.text
            if (!text.isNullOrEmpty()) {
                emit(text)
            }
        }
    }

    // 2. Multimodal image analysis (OCR, visual question answering, document scan)
    suspend fun analyzeImageWithPrompt(
        bitmap: Bitmap,
        prompt: String
    ): String {
        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val inputContent = Content(
                    role = "user",
                    parts = listOf(ImagePart(bitmap), TextPart(prompt))
                )
                val response = flashModel.generateContent(inputContent)
                response.text ?: "No analysis could be generated for this image."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
            throw e
        }
    }

    // 3. Document Analyzer & Summarizer
    suspend fun analyzeDocument(
        documentText: String,
        analysisMode: String
    ): String {
        val promptInstruction = when (analysisMode) {
            "summary" -> "Provide a comprehensive, clear, and well-structured executive summary of the following document:"
            "key_points" -> "Extract the top 5-10 key points, critical facts, and takeaways as clean bullet points from this document:"
            "study_notes" -> "Turn the following document content into high-yield study revision notes with key definitions, concepts, and flashcard-style Q&A:"
            "explain_simple" -> "Explain the key concepts of this document simply (ELI5 - explain like I'm 5), using analogies and clear language:"
            else -> "Analyze the following document and provide deep insights, conclusions, and action items:"
        }

        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val prompt = "$promptInstruction\n\n--- DOCUMENT CONTENT ---\n$documentText"
                val response = flashModel.generateContent(prompt)
                response.text ?: "Unable to analyze document."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document analysis failed", e)
            throw e
        }
    }

    // 4. Writing & Productivity Tools
    suspend fun generateWritingToolContent(
        tool: String,
        userInput: String,
        toneOrOptions: String = "Professional"
    ): String {
        val systemPrompt = when (tool) {
            "email" -> "You are Astra AI Email Architect. Write a polished, well-formatted email with Subject line and Body. Tone: $toneOrOptions. Context: $userInput"
            "blog" -> "You are Astra AI Blog Writer. Write an engaging, SEO-optimized blog post with headline, introduction, main sections with headings, and conclusion. Tone: $toneOrOptions. Topic: $userInput"
            "caption" -> "You are Astra AI Social Media Strategist. Generate 3 viral captions with appropriate trending hashtags, emojis, and call-to-actions. Platform/Vibe: $toneOrOptions. Prompt: $userInput"
            "code" -> "You are Astra AI Senior Software Engineer. Provide clean, well-commented code, explain how it works, and list edge cases. Language/Requirement: $userInput"
            "grammar" -> "You are Astra AI Grammar & Style Editor. Fix all spelling, grammar, punctuation, and phrasing errors in the following text. Show the improved version, followed by a concise bullet list of corrections made: $userInput"
            "math" -> "You are Astra AI Math Solver. Solve the following mathematical or logical problem with step-by-step reasoning, clear formulas, and the final answer highlighted in a box: $userInput"
            "flashcards" -> "You are Astra AI Study Mentor. Generate 5-8 flashcard pairs (Question and Answer) and 3 Multiple Choice Questions (MCQs) with explanations based on: $userInput"
            "translator" -> "You are Astra AI Polyglot Translator. Accurately translate the following text into $toneOrOptions with native fluency. Output the high quality translation clearly, followed by brief vocabulary or cultural notes if relevant: $userInput"
            "essay" -> "You are Astra AI Academic & Essay Specialist. Write a well-structured, in-depth essay on: $userInput. Tone: $toneOrOptions. Include an introduction with thesis, well-reasoned body paragraphs with examples, and a strong conclusion."
            else -> "You are Astra AI Assistant. Provide helpful, expert guidance for: $userInput"
        }

        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val response = flashModel.generateContent(systemPrompt)
                response.text ?: "Generation failed."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Writing tool failed", e)
            throw e
        }
    }

    // 5. Resume Builder AI Assistant
    suspend fun generateResumeContent(
        fullName: String,
        targetRole: String,
        experienceSummary: String,
        education: String,
        skills: String,
        projects: String
    ): String {
        val prompt = """
            You are Astra AI Executive Resume Architect.
            Create an ATS-friendly, impactful, professional resume in Markdown for:
            Name: $fullName
            Target Role: $targetRole
            Experience Details: $experienceSummary
            Education: $education
            Skills: $skills
            Key Projects: $projects
            
            Format clearly with:
            - Professional Summary (3-4 sentences using high-impact action verbs)
            - Core Competencies / Skills (categorized)
            - Work Experience (bullet points highlighting quantifiable results, achievements, and impact)
            - Projects (problem solved, tech stack, outcome)
            - Education & Certifications
        """.trimIndent()

        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val response = flashModel.generateContent(prompt)
                response.text ?: "Resume generation failed."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Resume generation failed", e)
            throw e
        }
    }

    // 6. Image Studio - Enhance Prompt & Generate Art Concept
    suspend fun enhanceImagePrompt(
        userPrompt: String,
        artStyle: String,
        aspectRatio: String
    ): Pair<String, String> {
        val metaPrompt = """
            You are Astra AI Visual Concept Artist.
            Expand this user prompt into a rich, detailed, prompt for AI image synthesis:
            User Prompt: "$userPrompt"
            Style: $artStyle
            Aspect Ratio: $aspectRatio
            
            Return output in this format:
            ENHANCED_PROMPT: <Expanded rich visual prompt describing lighting, composition, texture, color palette, camera lens>
            CREATIVE_INSIGHT: <1-2 sentences on creative direction and why this style was chosen>
        """.trimIndent()

        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val response = flashModel.generateContent(metaPrompt)
                val text = response.text.orEmpty()
                val enhanced = text.substringAfter("ENHANCED_PROMPT:", "").substringBefore("CREATIVE_INSIGHT:").trim()
                val insight = text.substringAfter("CREATIVE_INSIGHT:", "").trim()
                Pair(
                    if (enhanced.isNotBlank()) enhanced else userPrompt,
                    if (insight.isNotBlank()) insight else "Generated in $artStyle style with $aspectRatio framing."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image prompt enhancement failed", e)
            Pair(userPrompt, "Styled in $artStyle.")
        }
    }
}
