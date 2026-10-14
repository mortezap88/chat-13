package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioRecorderManager
import com.example.audio.VoicePersonaManager
import com.example.audio.VoicePersonas
import com.example.data.ApiKeyItem
import com.example.data.AppSettings
import com.example.data.ChatMessage
import com.example.data.CustomPersona
import com.example.data.ExtractedMemoryItem
import com.example.data.LinguisticCorrection
import com.example.data.SettingsRepository
import com.example.data.db.AppDatabase
import com.example.data.db.MemoryCategory
import com.example.data.db.MemoryRepository
import com.example.network.GeminiPoolService
import com.example.network.TavilyPoolService
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.MemoryVaultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScreenState {
    CHAT,
    SETTINGS,
    MEMORY_VAULT
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MainAppRoot()
        }
    }

    @Composable
    private fun MainAppRoot() {
        val coroutineScope = rememberCoroutineScope()
        val context = this@MainActivity

        val settingsRepository = remember { SettingsRepository(context) }
        val appDatabase = remember { AppDatabase.getInstance(context) }
        val memoryRepository = remember { MemoryRepository(appDatabase.memoryDao()) }

        var settings by remember { mutableStateOf(settingsRepository.loadSettings()) }
        val apiKeys = remember { mutableStateListOf<ApiKeyItem>().apply { addAll(settingsRepository.loadApiKeys()) } }
        val tavilyKeys = remember { mutableStateListOf<ApiKeyItem>().apply { addAll(settingsRepository.loadTavilyApiKeys()) } }
        val customPersonas = remember { mutableStateListOf<CustomPersona>().apply { addAll(settingsRepository.loadCustomPersonas()) } }
        val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(settingsRepository.loadChatMessages()) } }

        val allMemories by memoryRepository.allMemories.collectAsStateWithLifecycle(emptyList())

        val poolService = remember { GeminiPoolService(apiKeys) }
        val tavilyPoolService = remember { TavilyPoolService(tavilyKeys) }
        val voicePersonaManager = remember { VoicePersonaManager(context, poolService) }

        var isRecordingAudio by remember { mutableStateOf(false) }
        val audioRecorderManager = remember {
            AudioRecorderManager(context)
        }

        var currentScreen by remember { mutableStateOf(ScreenState.CHAT) }

        var isConsolidatingMemory by remember { mutableStateOf(false) }
        var showMemoryReviewDialog by remember { mutableStateOf(false) }
        val candidateMemories = remember { mutableStateListOf<ExtractedMemoryItem>() }
        val selectedMemoryIndices = remember { mutableStateListOf<Int>() }

        val audioPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                val started = audioRecorderManager.startRecording(
                    vibrationPattern = settings.vibrationPattern
                )
                if (started) isRecordingAudio = true
            }
        }

        fun submitUnifiedMessage(
            userText: String? = null,
            audioBase64: String? = null,
            replaceIndex: Int? = null
        ) {
            if (userText.isNullOrBlank() && audioBase64.isNullOrBlank()) return

            val isAudio = !audioBase64.isNullOrBlank()
            val initialDisplay = if (isAudio) "Listening & transcribing..." else (userText ?: "")
            val isWebSearchActive = tavilyKeys.isNotEmpty() && settings.tavilySearchEnabled

            val userMessage = ChatMessage(
                userText = initialDisplay,
                isAudioInput = isAudio,
                isStreaming = true,
                isAnalyzingCorrections = true,
                modelUsed = settings.primaryModel,
                isWebSearchEnhanced = false
            )

            val msgIndex = if (replaceIndex != null && replaceIndex in messages.indices) {
                messages[replaceIndex] = userMessage
                replaceIndex
            } else {
                messages.add(userMessage)
                messages.size - 1
            }
            settingsRepository.saveChatMessages(messages.toList())

            coroutineScope.launch {
                var webContext = ""
                var webSearchError: String? = null
                if (isWebSearchActive) {
                    val queryText = userText ?: "latest news and current trends"
                    tavilyPoolService.searchWeb(
                        query = queryText,
                        thoroughness = settings.tavilyThoroughness
                    ).onSuccess { results ->
                        webContext = results
                    }.onFailure { err ->
                        webSearchError = err.message
                        Log.e("WebSearch", "Tavily failed: ${err.message}")
                    }
                }
                val searchEnhanced = webContext.isNotBlank()

                if (webSearchError != null) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Web search unavailable: $webSearchError", android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                var memoryContext = ""
                if (settings.longTermMemoryEnabled && !userText.isNullOrBlank()) {
                    val matching = memoryRepository.retrieveRelevantMemories(
                        query = userText,
                        limit = settings.maxRetrievedMemories
                    )
                    android.util.Log.d("MemoryRetrieval", "Injecting ${matching.size} memories for: $userText")
                    if (matching.isNotEmpty()) {
                        memoryContext = buildString {
                            appendLine("Permanent User Facts from Memory Vault:")
                            matching.forEach { mem ->
                                appendLine("- ${mem.content} [${mem.category}]")
                            }
                        }
                    }
                }

                val activePersona = VoicePersonas.findById(settings.selectedVoicePersona, customPersonas.toList())
                val systemPrompt = buildString {
                    appendLine(activePersona.systemPromptModifier)
                    if (memoryContext.isNotBlank()) {
                        appendLine()
                        appendLine(memoryContext)
                    }
                    if (webContext.isNotBlank()) {
                        appendLine()
                        appendLine("[WEB SEARCH RESULTS ALREADY PROVIDED BELOW]. Answer from them directly. Never promise to 'run a search' later — it already happened:")
                        appendLine(webContext)
                    }
                }

                poolService.streamUnifiedTurn(
                    session = messages.toList(),
                    systemInstruction = systemPrompt,
                    settings = settings,
                    userText = userText,
                    audioBase64 = audioBase64
                ).catch { err ->
                    val curr = messages.getOrNull(msgIndex)
                    if (curr != null) {
                        messages[msgIndex] = curr.copy(
                            error = err.message ?: "Network error",
                            isStreaming = false,
                            isAnalyzingCorrections = false,
                            isWebSearchEnhanced = searchEnhanced
                        )
                        settingsRepository.saveChatMessages(messages.toList())
                    }
                }.collect { update ->
                    val curr = messages.getOrNull(msgIndex)
                    if (curr != null) {
                        messages[msgIndex] = curr.copy(
                            aiResponse = update.answer,
                            userText = update.userSentence ?: curr.userText,
                            corrections = if (update.corrections.isNotEmpty()) update.corrections else curr.corrections,
                            isStreaming = !update.isComplete,
                            isAnalyzingCorrections = !update.isComplete,
                            isWebSearchEnhanced = searchEnhanced
                        )
                    }
                }

                // Finalize turn and trigger TTS if enabled
                val curr = messages.getOrNull(msgIndex)
                if (curr != null) {
                    val finalMsg = curr.copy(
                        isStreaming = false,
                        isAnalyzingCorrections = false,
                        isWebSearchEnhanced = searchEnhanced
                    )
                    messages[msgIndex] = finalMsg
                    settingsRepository.saveChatMessages(messages.toList())

                    // Only send API request when the speaker button is ON
                    if (settings.ttsAutoSpeak && finalMsg.aiResponse.isNotBlank()) {
                        voicePersonaManager.speakChatMessage(
                            messageId = finalMsg.id,
                            text = finalMsg.aiResponse,
                            persona = activePersona,
                            scope = coroutineScope
                        )
                    }
                }
            }
        }

        fun stopVoiceRecordingAndSend() {
            if (!isRecordingAudio) return
            isRecordingAudio = false
            val result = audioRecorderManager.stopRecording(
                silenceTrimmingEnabled = settings.silenceTrimmingEnabled
            ) ?: return

            val minHoldMs = (settings.minVoiceHoldDurationSeconds * 1000).toLong()
            if (result.durationMs < minHoldMs) {
                return
            }

            submitUnifiedMessage(audioBase64 = result.fullAudioBase64)
        }

        fun initiateChatDeletionWithMemoryExtraction() {
            if (isConsolidatingMemory) return
            if (messages.isEmpty()) return
            isConsolidatingMemory = true

            coroutineScope.launch(Dispatchers.IO) {
                val extractionResult = poolService.consolidateSessionIntoMemories(
                    session = messages.toList(),
                    model = settings.primaryModel
                )
                isConsolidatingMemory = false

                candidateMemories.clear()
                selectedMemoryIndices.clear()

                extractionResult.onSuccess { extracted ->
                    val existingMemories = memoryRepository.getAllMemories()
                    val deduplicated = extracted.filter { newItem ->
                        existingMemories.none { existing ->
                            existing.content.equals(newItem.content, ignoreCase = true) ||
                            existing.keywords.split(",").map { it.trim() }.any { kw ->
                                newItem.keywords.any { it.equals(kw, ignoreCase = true) }
                            }
                        }
                    }
                    candidateMemories.addAll(deduplicated)
                    selectedMemoryIndices.addAll(0 until deduplicated.size)
                    showMemoryReviewDialog = true
                }.onFailure {
                    showMemoryReviewDialog = true
                }
            }
        }

        fun applyCorrection(msg: ChatMessage, corr: LinguisticCorrection) {
            val updatedUserText = msg.userText.replace(corr.original, corr.suggested)
            val updatedCorrections = msg.corrections.filter { it.id != corr.id }
            val index = messages.indexOfFirst { it.id == msg.id }
            if (index >= 0) {
                messages[index] = msg.copy(
                    userText = updatedUserText,
                    corrections = updatedCorrections
                )
                settingsRepository.saveChatMessages(messages.toList())
            }
        }

        val isDarkTheme = when (settings.themeMode) {
            "Light" -> false
            "Dark" -> true
            else -> isSystemInDarkTheme()
        }

        MyApplicationTheme(darkTheme = isDarkTheme) {
            when (currentScreen) {
                ScreenState.MEMORY_VAULT -> {
                    BackHandler { currentScreen = ScreenState.CHAT }
                    MemoryVaultScreen(
                        memories = allMemories,
                        onAddMemory = { content, cat, imp, kws ->
                            coroutineScope.launch(Dispatchers.IO) {
                                memoryRepository.addMemoryNote(content, cat, imp, kws, "USER_MANUAL")
                            }
                        },
                        onDeleteMemory = { id ->
                            coroutineScope.launch(Dispatchers.IO) {
                                memoryRepository.deleteMemoryNote(id)
                            }
                        },
                        onClearAll = {
                            coroutineScope.launch(Dispatchers.IO) {
                                memoryRepository.clearAll()
                            }
                        },
                        onBack = { currentScreen = ScreenState.CHAT }
                    )
                }

                ScreenState.SETTINGS -> {
                    BackHandler { currentScreen = ScreenState.CHAT }
                    SettingsScreen(
                        currentSettings = settings,
                        apiKeys = apiKeys,
                        tavilyApiKeys = tavilyKeys,
                        customPersonas = customPersonas,
                        memoryCount = allMemories.size,
                        onOpenMemoryVault = { currentScreen = ScreenState.MEMORY_VAULT },
                        onSaveSettings = { updated ->
                            settings = updated
                            settingsRepository.saveSettings(updated)
                        },
                        onSaveCustomPersona = { newPersona ->
                            val existingIndex = customPersonas.indexOfFirst { it.id == newPersona.id }
                            if (existingIndex >= 0) {
                                customPersonas[existingIndex] = newPersona
                            } else {
                                customPersonas.add(newPersona)
                            }
                            settingsRepository.saveCustomPersonas(customPersonas.toList())
                            val updated = settings.copy(selectedVoicePersona = newPersona.id)
                            settings = updated
                            settingsRepository.saveSettings(updated)
                        },
                        onDeleteCustomPersona = { personaId ->
                            customPersonas.removeAll { it.id == personaId }
                            settingsRepository.saveCustomPersonas(customPersonas.toList())
                            if (settings.selectedVoicePersona == personaId) {
                                val fallback = VoicePersonas.CURATED_PEOPLE.first().id
                                val updated = settings.copy(selectedVoicePersona = fallback)
                                settings = updated
                                settingsRepository.saveSettings(updated)
                            }
                        },
                        onAddKey = { newKey ->
                            val cleanKey = newKey.trim()
                            if (cleanKey.isNotBlank()) {
                                val newItem = ApiKeyItem(key = cleanKey, label = "Key #${apiKeys.size + 1}")
                                apiKeys.add(newItem)
                                settingsRepository.saveApiKeys(apiKeys.toList())
                                poolService.updateKeys(apiKeys.toList())
                            }
                        },
                        onBulkAddKeys = { keysList ->
                            val currentSize = apiKeys.size
                            val newItems = keysList
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .mapIndexed { idx, k ->
                                    ApiKeyItem(key = k, label = "Key #${currentSize + idx + 1}")
                                }
                            if (newItems.isNotEmpty()) {
                                apiKeys.addAll(newItems)
                                settingsRepository.saveApiKeys(apiKeys.toList())
                                poolService.updateKeys(apiKeys.toList())
                            }
                        },
                        onDeleteKey = { keyId ->
                            apiKeys.removeAll { it.id == keyId }
                            settingsRepository.saveApiKeys(apiKeys.toList())
                            poolService.updateKeys(apiKeys.toList())
                        },
                        onAddTavilyKey = { newKey ->
                            val cleanKey = newKey.trim()
                            if (cleanKey.isNotBlank()) {
                                val newItem = ApiKeyItem(key = cleanKey, label = "Tavily #${tavilyKeys.size + 1}")
                                tavilyKeys.add(newItem)
                                settingsRepository.saveTavilyApiKeys(tavilyKeys.toList())
                                tavilyPoolService.updateKeys(tavilyKeys.toList())
                            }
                        },
                        onBulkAddTavilyKeys = { keysList ->
                            val currentSize = tavilyKeys.size
                            val newItems = keysList
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .mapIndexed { idx, k ->
                                    ApiKeyItem(key = k, label = "Tavily #${currentSize + idx + 1}")
                                }
                            if (newItems.isNotEmpty()) {
                                tavilyKeys.addAll(newItems)
                                settingsRepository.saveTavilyApiKeys(tavilyKeys.toList())
                                tavilyPoolService.updateKeys(tavilyKeys.toList())
                            }
                        },
                        onDeleteTavilyKey = { keyId ->
                            tavilyKeys.removeAll { it.id == keyId }
                            settingsRepository.saveTavilyApiKeys(tavilyKeys.toList())
                            tavilyPoolService.updateKeys(tavilyKeys.toList())
                        },
                        onResetDefaults = {
                            val def = AppSettings()
                            settings = def
                            settingsRepository.saveSettings(def)
                        },
                        onRemoveChatHistory = {
                            initiateChatDeletionWithMemoryExtraction()
                        },
                        onPreviewVibration = { patternId ->
                            audioRecorderManager.previewPattern(patternId)
                        },
                        onStopPreviewVibration = {
                            audioRecorderManager.stopVibration()
                        },
                        onPreviewVoicePersona = { personaId ->
                            val persona = VoicePersonas.findById(personaId, customPersonas.toList())
                            voicePersonaManager.previewPersona(
                                persona = persona,
                                scope = coroutineScope
                            )
                        },
                        onStopPreviewVoicePersona = {
                            voicePersonaManager.stop()
                        },
                        currentlySpeakingPersonaId = voicePersonaManager.currentlySpeakingPersonaId,
                        isLoadingPersonaId = voicePersonaManager.isLoadingPersonaId,
                        onClose = {
                            audioRecorderManager.stopVibration()
                            voicePersonaManager.stop()
                            currentScreen = ScreenState.CHAT
                        }
                    )
                }

                ScreenState.CHAT -> {
                    ChatScreen(
                        messages = messages,
                        settings = settings,
                        isDarkTheme = isDarkTheme,
                        apiKeys = apiKeys,
                        activeKeyIndex = poolService.activeKeyIndex,
                        isRecordingAudio = isRecordingAudio,
                        isConsolidatingMemory = isConsolidatingMemory,
                        onRequestDeleteChat = { initiateChatDeletionWithMemoryExtraction() },
                        onStartAudioRecording = {
                            val hasRecordPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasRecordPermission) {
                                val started = audioRecorderManager.startRecording(
                                    vibrationPattern = settings.vibrationPattern
                                )
                                if (started) isRecordingAudio = true
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStopAudioRecording = { stopVoiceRecordingAndSend() },
                        isTtsEnabled = settings.ttsAutoSpeak,
                        onToggleTts = {
                            val nextState = !settings.ttsAutoSpeak
                            val updated = settings.copy(ttsAutoSpeak = nextState)
                            settings = updated
                            settingsRepository.saveSettings(updated)
                            if (!nextState) voicePersonaManager.stop()
                        },
                        isWebSearchEnabled = settings.tavilySearchEnabled,
                        onToggleWebSearch = {
                            val nextState = !settings.tavilySearchEnabled
                            if (nextState && tavilyKeys.isEmpty()) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Add a Tavily API key first: Settings → API Keys",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            val updated = settings.copy(tavilySearchEnabled = nextState)
                            settings = updated
                            settingsRepository.saveSettings(updated)
                        },
                        onSendMessage = { text -> submitUnifiedMessage(userText = text) },
                        onOpenSettings = { currentScreen = ScreenState.SETTINGS },
                        onRetryMessage = { chatMsg ->
                            val existingIndex = messages.indexOfFirst { it.id == chatMsg.id }
                            if (existingIndex >= 0) {
                                submitUnifiedMessage(userText = chatMsg.userText, replaceIndex = existingIndex)
                            } else {
                                submitUnifiedMessage(userText = chatMsg.userText)
                            }
                        },
                        onApplyCorrection = { msg, corr -> applyCorrection(msg, corr) },
                        speakingMessageId = voicePersonaManager.currentlySpeakingMessageId
                    )
                }
            }

            // Memory Review Modal
            if (showMemoryReviewDialog) {
                AlertDialog(
                    onDismissRequest = { showMemoryReviewDialog = false },
                    title = {
                        Text(
                            text = "Review Memories Before Clearing",
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF18181B),
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                        ) {
                            Text(
                                text = "The AI extracted ${candidateMemories.size} candidate facts/preferences from this conversation. Select which ones to keep in your permanent Memory Vault:",
                                color = if (isDarkTheme) Color(0xFFA1A1AA) else Color(0xFF71717A),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (candidateMemories.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No distinct long-term facts were found in this chat session.",
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(candidateMemories) { idx, item ->
                                        val isChecked = selectedMemoryIndices.contains(idx)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isChecked) (if (isDarkTheme) Color(0xFF27272A) else Color(0xFFF4F4F5)) else (if (isDarkTheme) Color(0xFF18181B) else Color.White))
                                                .border(1.dp, if (isChecked) (if (isDarkTheme) Color.White else Color(0xFF18181B)) else (if (isDarkTheme) Color(0xFF27272A) else Color(0xFFE4E4E7)), RoundedCornerShape(8.dp))
                                                .clickable {
                                                    if (isChecked) selectedMemoryIndices.remove(idx)
                                                    else selectedMemoryIndices.add(idx)
                                                }
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    if (checked) selectedMemoryIndices.add(idx)
                                                    else selectedMemoryIndices.remove(idx)
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = if (isDarkTheme) Color.White else Color(0xFF18181B),
                                                    uncheckedColor = if (isDarkTheme) Color(0xFFA1A1AA) else Color(0xFF71717A)
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.content,
                                                    color = if (isDarkTheme) Color.White else Color(0xFF18181B),
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "[${item.category}] • Importance: ${item.importance}/5",
                                                    color = if (isDarkTheme) Color(0xFFA1A1AA) else Color(0xFF71717A),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    selectedMemoryIndices.forEach { idx ->
                                        val item = candidateMemories.getOrNull(idx)
                                        if (item != null) {
                                            memoryRepository.addMemoryNote(
                                                content = item.content,
                                                category = MemoryCategory.fromString(item.category),
                                                importance = item.importance,
                                                keywords = item.keywords.joinToString(", "),
                                                source = "SESSION_CONSOLIDATED"
                                            )
                                        }
                                    }
                                    messages.clear()
                                    settingsRepository.saveChatMessages(emptyList())
                                    showMemoryReviewDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDarkTheme) Color.White else Color(0xFF18181B)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Save (${selectedMemoryIndices.size})",
                                color = if (isDarkTheme) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    selectedMemoryIndices.clear()
                                }
                            ) {
                                Text(
                                    text = "Deselect All",
                                    color = Color(0xFF71717A),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(
                                onClick = {
                                    messages.clear()
                                    settingsRepository.saveChatMessages(emptyList())
                                    showMemoryReviewDialog = false
                                }
                            ) {
                                Text(
                                    text = "Discard All & Clear",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    containerColor = if (isDarkTheme) Color(0xFF18181B) else Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}
