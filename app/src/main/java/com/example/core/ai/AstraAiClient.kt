package com.example.core.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.core.search.MultiSourceSearchEngine
import com.example.core.search.SearchResult
import com.example.data.local.AstraPreferences
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

data class SearchChatResponse(
    val answer: String,
    val sources: List<SearchResult>
)

class AstraAiClient(
    private val searchEngine: MultiSourceSearchEngine = MultiSourceSearchEngine()
) {

    // General purpose flash model (frontier speed + multimodal reasoning)
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

    // Powerful reasoning model
    private val proModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon"
        )
    }

    private fun getModel(modelName: String) = when (modelName) {
        "gemini-3.5-flash-lite" -> liteModel
        "gemini-4-argon" -> proModel
        else -> flashModel
    }

    // 1. Streaming chat response with multi-turn history and response controls
    fun streamChat(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        modelName: String = "gemini-3.8-flash",
        thinkingLevel: String? = null,
        answerLength: String? = null,
        tone: String? = null,
        language: String? = null
    ): Flow<String> = flow {
        val chatHistory = history.map { (role: String, text: String) ->
            Content(
                role = if (role == "user") "user" else "model",
                parts = listOf(TextPart(text))
            )
        }

        val targetModel = getModel(modelName)
        val chat = targetModel.startChat(chatHistory)
        val pref = AstraPreferences.settings.value

        val effectiveTone = tone ?: pref.responseTone
        val effectiveLength = answerLength ?: pref.responseLength
        val effectiveLang = language ?: pref.language
        val effectiveThinking = thinkingLevel ?: pref.thinkingLevel

        val thinkingInstruction = when (effectiveThinking) {
            "Low" -> "Answer directly, concisely, and immediately with key points."
            "High" -> "Use deep, step-by-step reasoning. Break down complex aspects, provide comprehensive context, consider nuances, and explain logical deductions thoroughly."
            else -> "Provide a balanced, clear, and well-reasoned answer."
        }

        val lengthInstruction = when (effectiveLength) {
            "Short" -> "Keep the response concise (1-2 short paragraphs or brief bullets)."
            "Long", "Detailed" -> "Provide an in-depth, comprehensive explanation with detailed breakdowns."
            else -> "Provide a balanced response with sufficient depth."
        }

        val promptBuilder = StringBuilder()
        promptBuilder.append("[System Instructions: Answer in $effectiveLang, with a ${effectiveTone.lowercase()} tone. $lengthInstruction $thinkingInstruction]\n\n")
        promptBuilder.append(userMessage)

        val responseStream = withTimeout(DEFAULT_TIMEOUT_MS) {
            chat.sendMessageStream(promptBuilder.toString())
        }

        responseStream.collect { chunk ->
            val text = chunk.text
            if (!text.isNullOrEmpty()) {
                emit(text)
            }
        }
    }

    // 2. Real Web Search & Grounded General Question Answering (e.g. "Who is Rohit Sharma?")
    suspend fun searchAndAnswer(
        query: String,
        modelName: String = "gemini-3.8-flash",
        thinkingLevel: String? = null,
        answerLength: String? = null
    ): SearchChatResponse {
        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                // Step 1: Query live search engine
                val searchResults = searchEngine.executeSearch(query)

                val pref = AstraPreferences.settings.value
                val effectiveThinking = thinkingLevel ?: pref.thinkingLevel
                val effectiveLength = answerLength ?: pref.responseLength
                val effectiveLang = pref.language
                val effectiveTone = pref.responseTone

                val reasoningPrompt = buildSearchPrompt(
                    query = query,
                    searchResults = searchResults,
                    thinkingLevel = effectiveThinking,
                    answerLength = effectiveLength,
                    language = effectiveLang,
                    tone = effectiveTone
                )

                val response = getModel(modelName).generateContent(reasoningPrompt)
                val text = response.text ?: "No answer could be synthesized for '$query'."

                SearchChatResponse(
                    answer = text,
                    sources = searchResults
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search and answer failed for query '$query'", e)
            SearchChatResponse(
                answer = "I encountered an error querying live search. However, based on general knowledge: ${e.localizedMessage}",
                sources = emptyList()
            )
        }
    }

    // Streaming version of Search & Answer
    fun streamSearchAndAnswer(
        query: String,
        modelName: String = "gemini-3.8-flash",
        thinkingLevel: String? = null,
        answerLength: String? = null,
        onSourcesFound: (List<SearchResult>) -> Unit = {}
    ): Flow<String> = flow {
        // Step 1: Query live search engine
        val searchResults = try {
            searchEngine.executeSearch(query)
        } catch (e: Exception) {
            Log.w(TAG, "Live search exception: ${e.message}")
            emptyList()
        }

        onSourcesFound(searchResults)

        val pref = AstraPreferences.settings.value
        val effectiveThinking = thinkingLevel ?: pref.thinkingLevel
        val effectiveLength = answerLength ?: pref.responseLength
        val effectiveLang = pref.language
        val effectiveTone = pref.responseTone

        val reasoningPrompt = buildSearchPrompt(
            query = query,
            searchResults = searchResults,
            thinkingLevel = effectiveThinking,
            answerLength = effectiveLength,
            language = effectiveLang,
            tone = effectiveTone
        )

        val responseStream = withTimeout(DEFAULT_TIMEOUT_MS) {
            getModel(modelName).generateContentStream(reasoningPrompt)
        }

        responseStream.collect { chunk ->
            val text = chunk.text
            if (!text.isNullOrEmpty()) {
                emit(text)
            }
        }
    }

    private fun buildSearchPrompt(
        query: String,
        searchResults: List<SearchResult>,
        thinkingLevel: String,
        answerLength: String,
        language: String,
        tone: String
    ): String {
        val thinkingGuide = when (thinkingLevel) {
            "Low" -> "Provide a direct, high-level summary."
            "High" -> "Conduct deep step-by-step reasoning. Structure clearly with background, career milestones, stats/achievements, and impact."
            else -> "Provide a well-rounded, informative, structured answer."
        }

        val lengthGuide = when (answerLength) {
            "Short" -> "Answer in 1-2 concise paragraphs."
            "Long", "Detailed" -> "Provide an extensive, comprehensive guide with distinct subsections."
            else -> "Provide a balanced, complete explanation."
        }

        val sb = StringBuilder()
        sb.append("You are Astra AI, an intelligent, objective, factual assistant with real-time web grounding.\n")
        sb.append("Language: $language | Tone: $tone\n")
        sb.append("Reasoning Level: $thinkingGuide\n")
        sb.append("Length: $lengthGuide\n\n")

        if (searchResults.isNotEmpty()) {
            sb.append("--- VERIFIED LIVE WEB CONTEXT ---\n")
            searchResults.forEachIndexed { index, res ->
                sb.append("[Source ${index + 1}]: ${res.title} (${res.url})\n")
                sb.append("Snippet: ${res.snippet}\n\n")
            }
            sb.append("--- INSTRUCTIONS ---\n")
            sb.append("Answer the user question accurately using the live web context and your knowledge base.\n")
            sb.append("When referencing facts, include markdown links in the format [Source Title](URL).\n")
            sb.append("Never invent facts, dates, or records.\n\n")
        } else {
            sb.append("Note: Live search retrieved no external snippets. Provide an accurate, comprehensive answer from verified knowledge.\n\n")
        }

        sb.append("USER QUESTION: $query")
        return sb.toString()
    }

    // 3. Multimodal image analysis (OCR, visual question answering, document scan)
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

    // 4. Document & PDF Analyzer & Summarizer
    suspend fun analyzeDocument(
        documentText: String,
        analysisMode: String
    ): String {
        val promptInstruction = when (analysisMode) {
            "summary" -> "Provide a comprehensive, clear, and well-structured executive summary of the following document:"
            "key_points" -> "Extract the top 5-10 key points, critical facts, and takeaways as clean bullet points from this document:"
            "study_notes" -> "Turn the following document content into high-yield study revision notes with key definitions, concepts, and flashcard-style Q&A:"
            "explain_simple" -> "Explain the key concepts of this document simply (ELI5 - explain like I'm 5), using analogies and clear language:"
            "tables" -> "Extract all structured data, figures, tables, and statistics from the following document into clean Markdown tables:"
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

    // 5. Document comparison
    suspend fun compareDocuments(
        docA: String,
        docB: String,
        titleA: String = "Document A",
        titleB: String = "Document B"
    ): String {
        val prompt = """
            Compare the following two documents in detail:
            
            --- $titleA ---
            $docA
            
            --- $titleB ---
            $docB
            
            Provide:
            1. Executive Overview of similarities and differences.
            2. Key Content Discrepancies (additions, deletions, modified terms).
            3. Detailed Comparison Table in Markdown.
            4. Summary and Recommendation.
        """.trimIndent()

        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val response = flashModel.generateContent(prompt)
                response.text ?: "Document comparison failed."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document comparison failed", e)
            throw e
        }
    }

    // 6. Writing & Productivity Tools
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

    // 7. Resume Builder AI Assistant
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

    // 8. Image Studio - Enhance Prompt & Generate Art Concept
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

    // 9. Video Studio - Concept & Motion Director
    suspend fun planVideoScene(
        sceneDescription: String,
        videoStyle: String,
        cameraMotion: String,
        durationSeconds: Int = 4
    ): String {
        val prompt = """
            You are Astra AI Cinematic Video Director.
            Design a professional, detailed video production storyboard and shot specification for:
            Concept: $sceneDescription
            Visual Style: $videoStyle
            Camera Motion: $cameraMotion
            Target Duration: $durationSeconds seconds
            
            Output a clear, production-grade shot breakdown in Markdown:
            - Shot Composition & Camera Setup (Lens, FPS, Angle, Motion path)
            - Lighting & Atmosphere
            - Action & Subject Dynamics (second-by-second breakdown: 00:00 to 00:0$durationSeconds)
            - Sound & Ambient Audio Design
            - Prompt for generative video diffusion models (Sora / Kling / Runway format)
        """.trimIndent()

        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val response = flashModel.generateContent(prompt)
                response.text ?: "Video scene planning failed."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video scene planning failed", e)
            throw e
        }
    }

    // 10. General text generation with frontier Flash model
    suspend fun generateContent(prompt: String): String {
        return try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val response = flashModel.generateContent(prompt)
                response.text ?: ""
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Content generation failed", e)
            throw e
        }
    }
}
