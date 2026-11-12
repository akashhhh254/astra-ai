package com.example.ui.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ai.AstraAiClient
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.repository.AstraRepository
import com.example.ui.components.AstraLogoEmblem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserId: String,
    initialPrompt: String? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val repository = remember(currentUserId) { AstraRepository(context) }
    val aiClient = remember { AstraAiClient() }

    val conversations by repository.observeConversations(currentUserId).collectAsState(initial = emptyList())
    var activeConversationId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversations) {
        if (activeConversationId == null && conversations.isNotEmpty()) {
            activeConversationId = conversations.first().id
        }
    }

    val activeConv = conversations.find { it.id == activeConversationId }
    val messages by if (activeConversationId != null) {
        repository.observeMessages(currentUserId, activeConversationId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<Message>()) }
    }

    var messageInput by remember { mutableStateOf(initialPrompt ?: "") }
    var streamingResponse by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var currentJob by remember { mutableStateOf<Job?>(null) }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                selectedBitmap = bitmap
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val listState = rememberLazyListState()

    // Determine if user has scrolled up away from bottom
    val isScrolledToBottom by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItem >= totalItems - 2
            }
        }
    }

    // Auto-scroll to bottom only if user hasn't explicitly scrolled up
    LaunchedEffect(messages.size, streamingResponse) {
        if (isScrolledToBottom && (messages.isNotEmpty() || streamingResponse.isNotEmpty())) {
            val target = (messages.size + if (streamingResponse.isNotEmpty()) 1 else 0).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && !isGenerating) {
            val prompt = initialPrompt
            messageInput = ""
            sendMessage(
                prompt = prompt,
                bitmap = null,
                userId = currentUserId,
                activeConvId = activeConversationId,
                repository = repository,
                aiClient = aiClient,
                onConvCreated = { activeConversationId = it },
                onStreamChunk = { streamingResponse = it },
                onFinish = {
                    streamingResponse = ""
                    isGenerating = false
                },
                setJob = { currentJob = it },
                setIsGenerating = { isGenerating = it }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("chat_screen")
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Minimalist B&W Top Bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AstraLogoEmblem(size = 32.dp, animated = false)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = activeConv?.title?.ifBlank { "ASTRA AI Chat" } ?: "ASTRA AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                        Text(
                            text = "Gemini 3.8 Flash",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            actions = {
                // New Chat
                IconButton(
                    onClick = {
                        activeConversationId = null
                        streamingResponse = ""
                        isGenerating = false
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // Messages List Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && streamingResponse.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AstraLogoEmblem(size = 64.dp, animated = true)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "What would you like to explore?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Type a question, summarize text, or analyze documents.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val promptStarters = listOf(
                        "Explain quantum computing in 3 bullets",
                        "Write a clean Kotlin flow debounce example",
                        "Summarize key challenges in modern AI systems"
                    )

                    promptStarters.forEach { starter ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { messageInput = starter },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = starter,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(6.dp)) }

                    items(messages, key = { it.id }) { message ->
                        MonochromeChatMessageItem(
                            message = message,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onShare = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, message.content)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Response"))
                            },
                            onRegenerate = {
                                val lastUserMsg = messages.findLast { it.role == "user" }
                                if (lastUserMsg != null && !isGenerating) {
                                    sendMessage(
                                        prompt = lastUserMsg.content,
                                        bitmap = null,
                                        userId = currentUserId,
                                        activeConvId = activeConversationId,
                                        repository = repository,
                                        aiClient = aiClient,
                                        onConvCreated = { activeConversationId = it },
                                        onStreamChunk = { streamingResponse = it },
                                        onFinish = {
                                            streamingResponse = ""
                                            isGenerating = false
                                        },
                                        setJob = { currentJob = it },
                                        setIsGenerating = { isGenerating = it }
                                    )
                                }
                            }
                        )
                    }

                    if (streamingResponse.isNotEmpty()) {
                        item {
                            MonochromeStreamingItem(text = streamingResponse)
                        }
                    } else if (isGenerating) {
                        item {
                            MonochromeThinkingItem()
                        }
                    }

                    item { Spacer(modifier = Modifier.height(10.dp)) }
                }
            }

            // Scroll to bottom button when scrolled up
            if (!isScrolledToBottom && (messages.isNotEmpty() || streamingResponse.isNotEmpty())) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val target = (messages.size + if (streamingResponse.isNotEmpty()) 1 else 0).coerceAtLeast(0)
                            listState.animateScrollToItem(target)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .size(36.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Image Attachment Preview
        AnimatedVisibility(visible = selectedBitmap != null) {
            selectedBitmap?.let { bitmap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Image attached for visual AI analysis",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            selectedBitmap = null
                            selectedImageUri = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Bottom Input Bar (Monochrome)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach image
                IconButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.testTag("attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Input
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = {
                        Text(
                            text = if (selectedBitmap != null) "Ask about this image..." else "Message Astra AI...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Send / Stop button (White on dark, Black on light)
                if (isGenerating) {
                    IconButton(
                        onClick = {
                            currentJob?.cancel()
                            isGenerating = false
                            streamingResponse = ""
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                            .testTag("stop_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.surface
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (messageInput.isNotBlank() || selectedBitmap != null) {
                                val text = messageInput
                                val img = selectedBitmap
                                messageInput = ""
                                selectedBitmap = null
                                selectedImageUri = null
                                sendMessage(
                                    prompt = text,
                                    bitmap = img,
                                    userId = currentUserId,
                                    activeConvId = activeConversationId,
                                    repository = repository,
                                    aiClient = aiClient,
                                    onConvCreated = { activeConversationId = it },
                                    onStreamChunk = { streamingResponse = it },
                                    onFinish = {
                                        streamingResponse = ""
                                        isGenerating = false
                                    },
                                    setJob = { currentJob = it },
                                    setIsGenerating = { isGenerating = it }
                                )
                            }
                        },
                        enabled = messageInput.isNotBlank() || selectedBitmap != null,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (messageInput.isNotBlank() || selectedBitmap != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageInput.isNotBlank() || selectedBitmap != null)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun sendMessage(
    prompt: String,
    bitmap: Bitmap?,
    userId: String,
    activeConvId: String?,
    repository: AstraRepository,
    aiClient: AstraAiClient,
    onConvCreated: (String) -> Unit,
    onStreamChunk: (String) -> Unit,
    onFinish: () -> Unit,
    setJob: (Job) -> Unit,
    setIsGenerating: (Boolean) -> Unit
) {
    setIsGenerating(true)
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

    val job = scope.launch {
        var convId = activeConvId
        if (convId == null) {
            val title = if (prompt.isNotBlank()) prompt.take(30) else "Vision Analysis"
            val createRes = repository.createConversation(title)
            if (createRes.isSuccess) {
                convId = createRes.getOrThrow()
                onConvCreated(convId)
            }
        }

        if (convId == null) {
            setIsGenerating(false)
            return@launch
        }

        // Save User Message
        repository.addMessage(convId, "user", prompt.ifBlank { "[Attached Image]" })
        repository.incrementUsage(userId)

        try {
            val accumulated = StringBuilder()

            if (bitmap != null) {
                val response = aiClient.analyzeImageWithPrompt(
                    bitmap = bitmap,
                    prompt = prompt.ifBlank { "Analyze this image in detail and extract all key information." }
                )
                repository.addMessage(convId, "model", response)
            } else {
                aiClient.streamChat(prompt).collect { chunk ->
                    accumulated.append(chunk)
                    onStreamChunk(accumulated.toString())
                }

                val finalModelResponse = accumulated.toString()
                if (finalModelResponse.isNotBlank()) {
                    repository.addMessage(convId, "model", finalModelResponse)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancelled
        } catch (e: Exception) {
            repository.addMessage(convId, "model", "I encountered an issue processing your request: ${e.localizedMessage}")
        } finally {
            onFinish()
        }
    }
    setJob(job)
}

@Composable
private fun MonochromeChatMessageItem(
    message: Message,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AstraLogoEmblem(
                size = 28.dp,
                animated = false,
                modifier = Modifier.align(Alignment.Top)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .testTag("chat_message_card"),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    MonochromeMarkdownContent(text = message.content)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Message Action Row (Monochrome icons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonochromeStreamingItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        AstraLogoEmblem(
            size = 28.dp,
            animated = true,
            modifier = Modifier.align(Alignment.Top)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Card(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MonochromeMarkdownContent(text = text)
            }
        }
    }
}

@Composable
private fun MonochromeThinkingItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "Thinking")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AstraLogoEmblem(size = 28.dp, animated = true)
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Astra AI is thinking...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alphaPulse)
                )
            }
        }
    }
}

@Composable
private fun MonochromeMarkdownContent(text: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val parts = remember(text) {
        val list = mutableListOf<MarkdownBlock>()
        val regex = Regex("```([a-zA-Z0-9]*)\\n?([\\s\\S]*?)```")
        var lastIndex = 0

        regex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            if (start > lastIndex) {
                list.add(MarkdownBlock.Text(text.substring(lastIndex, start)))
            }
            val lang = matchResult.groupValues[1]
            val code = matchResult.groupValues[2]
            list.add(MarkdownBlock.Code(lang = if (lang.isBlank()) "code" else lang, code = code))
            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < text.length) {
            list.add(MarkdownBlock.Text(text.substring(lastIndex)))
        }
        list
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEach { part ->
            when (part) {
                is MarkdownBlock.Text -> {
                    Text(
                        text = part.content.trim(),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownBlock.Code -> {
                    // Monochrome Code Block
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = part.lang.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.clickable {
                                        clipboardManager.setText(AnnotatedString(part.code))
                                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = part.code.trim(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Text(val content: String) : MarkdownBlock()
    data class Code(val lang: String, val code: String) : MarkdownBlock()
}
