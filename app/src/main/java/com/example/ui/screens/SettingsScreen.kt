package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.VibrationPatterns
import com.example.audio.VoicePersonas
import com.example.data.ApiKeyItem
import com.example.data.AppSettings
import com.example.data.CustomPersona
import com.example.data.KeyStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SettingsCategory(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    THEME_MANAGEMENT(
        title = "Theme Management",
        description = "Switch between Dark, Light, or System appearance",
        icon = Icons.Default.Tune
    ),
    CHAT_DISPLAY(
        title = "Chat & Display",
        description = "Token streaming, font scaling & display options",
        icon = Icons.AutoMirrored.Filled.Chat
    ),
    MODELS_REASONING(
        title = "AI Models & Thinking",
        description = "Primary Gemini tiers & calibrated reasoning depth",
        icon = Icons.Default.Psychology
    ),
    AI_PERSONA_VOICE(
        title = "AI Persona & Voice",
        description = "Grounded human characters, 30 Gemini voices & persona studio",
        icon = Icons.Default.Person
    ),
    VOICE_INPUT(
        title = "Voice & Microphone",
        description = "Gemini audio engine, silence trimming & haptic patterns",
        icon = Icons.Default.Mic
    ),
    LONG_TERM_MEMORY(
        title = "Long-Term Memory",
        description = "Memory vault notes & Hybrid Vector RAG parameters",
        icon = Icons.Default.Psychology
    ),
    API_KEYS(
        title = "API Keys & Pool",
        description = "Failover key pool, add keys & bulk configuration",
        icon = Icons.Default.Key
    ),
    WEB_SEARCH(
        title = "Web Search & Sources",
        description = "Search thoroughness depth & sources scale",
        icon = Icons.Default.Public
    )
}

@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    apiKeys: List<ApiKeyItem>,
    tavilyApiKeys: List<ApiKeyItem> = emptyList(),
    customPersonas: List<CustomPersona> = emptyList(),
    memoryCount: Int = 0,
    onOpenMemoryVault: () -> Unit = {},
    onSaveSettings: (AppSettings) -> Unit,
    onSaveCustomPersona: (CustomPersona) -> Unit = {},
    onDeleteCustomPersona: (String) -> Unit = {},
    onAddKey: (String) -> Unit,
    onBulkAddKeys: (List<String>) -> Unit,
    onDeleteKey: (String) -> Unit,
    onAddTavilyKey: (String) -> Unit = {},
    onBulkAddTavilyKeys: (List<String>) -> Unit = {},
    onDeleteTavilyKey: (String) -> Unit = {},
    onResetDefaults: () -> Unit,
    onRemoveChatHistory: () -> Unit = {},
    onPreviewVibration: ((String) -> Unit)? = null,
    onStopPreviewVibration: (() -> Unit)? = null,
    onPreviewVoicePersona: ((String) -> Unit)? = null,
    onStopPreviewVoicePersona: (() -> Unit)? = null,
    currentlySpeakingPersonaId: String? = null,
    isLoadingPersonaId: String? = null,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    androidx.activity.compose.BackHandler {
        if (selectedCategory != null) {
            selectedCategory = null
        } else {
            onClose()
        }
    }

    var themeMode by remember(currentSettings.themeMode) { mutableStateOf(currentSettings.themeMode) }
    val isDark = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val bgColor = if (isDark) Color(0xFF090A0C) else Color.White
    val cardBg = if (isDark) Color(0xFF18181B) else Color(0xFFF4F4F5)
    val cardSubBg = if (isDark) Color(0xFF27272A) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF18181B)
    val textMuted = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    val borderColor = if (isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)

    var primaryModel by remember(currentSettings.primaryModel) { mutableStateOf(currentSettings.primaryModel) }
    var tokenStreaming by remember(currentSettings.tokenStreaming) { mutableStateOf(currentSettings.tokenStreaming) }
    var minVoiceHoldDurationSeconds by remember(currentSettings.minVoiceHoldDurationSeconds) { mutableFloatStateOf(currentSettings.minVoiceHoldDurationSeconds) }
    var selectedVibrationPattern by remember(currentSettings.vibrationPattern) { mutableStateOf(currentSettings.vibrationPattern) }
    var previewingPatternId by remember { mutableStateOf<String?>(null) }
    var maxFontSizeSp by remember(currentSettings.maxFontSizeSp) { mutableIntStateOf(currentSettings.maxFontSizeSp) }
    var longTermMemoryEnabled by remember(currentSettings.longTermMemoryEnabled) { mutableStateOf(currentSettings.longTermMemoryEnabled) }
    var maxRetrievedMemories by remember(currentSettings.maxRetrievedMemories) { mutableIntStateOf(currentSettings.maxRetrievedMemories) }
    var selectedVoicePersona by remember(currentSettings.selectedVoicePersona) { mutableStateOf(currentSettings.selectedVoicePersona) }
    var tavilyThoroughness by remember(currentSettings.tavilyThoroughness) { mutableStateOf(currentSettings.tavilyThoroughness) }
    var silenceTrimmingEnabled by remember(currentSettings.silenceTrimmingEnabled) { mutableStateOf(currentSettings.silenceTrimmingEnabled) }

    androidx.compose.runtime.LaunchedEffect(currentSettings) {
        themeMode = currentSettings.themeMode
        primaryModel = currentSettings.primaryModel
        tokenStreaming = currentSettings.tokenStreaming
        minVoiceHoldDurationSeconds = currentSettings.minVoiceHoldDurationSeconds
        selectedVibrationPattern = currentSettings.vibrationPattern
        maxFontSizeSp = currentSettings.maxFontSizeSp
        longTermMemoryEnabled = currentSettings.longTermMemoryEnabled
        maxRetrievedMemories = currentSettings.maxRetrievedMemories
        selectedVoicePersona = currentSettings.selectedVoicePersona
        tavilyThoroughness = currentSettings.tavilyThoroughness
        silenceTrimmingEnabled = currentSettings.silenceTrimmingEnabled
    }

    // Persona Filter States
    var personaGenderFilter by remember { mutableStateOf("All") }
    var personaRegionFilter by remember { mutableStateOf("All") }
    var showCreatePersonaDialog by remember { mutableStateOf(false) }

    var newKeyInput by remember { mutableStateOf("") }
    var newTavilyKeyInput by remember { mutableStateOf("") }
    var apiKeyTab by remember { mutableIntStateOf(0) }

    fun syncAndSave() {
        onSaveSettings(
            currentSettings.copy(
                primaryModel = primaryModel,
                tokenStreaming = tokenStreaming,
                vibrationPattern = selectedVibrationPattern,
                maxFontSizeSp = maxFontSizeSp,
                longTermMemoryEnabled = longTermMemoryEnabled,
                maxRetrievedMemories = maxRetrievedMemories,
                minVoiceHoldDurationSeconds = minVoiceHoldDurationSeconds,
                selectedVoicePersona = selectedVoicePersona,
                tavilyThoroughness = tavilyThoroughness,
                silenceTrimmingEnabled = silenceTrimmingEnabled,
                themeMode = themeMode
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedCategory != null) {
                IconButton(onClick = { selectedCategory = null }, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedCategory?.title ?: "Settings",
                    color = textColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedCategory != null) "Customize selected options" else "Select a category to customize",
                    color = textMuted,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onClose, modifier = Modifier.size(36.dp).testTag("close_settings_button")) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = textColor)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(borderColor))

        AnimatedContent(
            targetState = selectedCategory,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "CategoryTransition",
            modifier = Modifier.weight(1f)
        ) { category ->
            if (category == null) {
                // Hub View
                val hubScroll = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(hubScroll).padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "PREFERENCES & OPTIONS", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    SettingsCategory.entries.forEach { cat ->
                        CategoryHubCard(
                            category = cat,
                            cardBg = cardBg,
                            textColor = textColor,
                            textMuted = textMuted,
                            borderColor = borderColor,
                            iconBg = cardSubBg,
                            onClick = { selectedCategory = cat }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                onResetDefaults()
                                val def = AppSettings()
                                primaryModel = def.primaryModel
                                tokenStreaming = def.tokenStreaming
                                minVoiceHoldDurationSeconds = def.minVoiceHoldDurationSeconds
                                selectedVibrationPattern = def.vibrationPattern
                                maxFontSizeSp = def.maxFontSizeSp
                                longTermMemoryEnabled = def.longTermMemoryEnabled
                                maxRetrievedMemories = def.maxRetrievedMemories
                                selectedVoicePersona = def.selectedVoicePersona
                                tavilyThoroughness = def.tavilyThoroughness
                                silenceTrimmingEnabled = def.silenceTrimmingEnabled
                                themeMode = def.themeMode
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = textMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset All Settings to Defaults", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(borderColor)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { onRemoveChatHistory() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = textMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Current Chat History", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                val detailScroll = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(detailScroll).padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (category) {
                        SettingsCategory.THEME_MANAGEMENT -> {
                            Text("App Appearance", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val themes = listOf(
                                Triple("Dark", "Dark Mode", "Deep OLED-friendly black background with high contrast"),
                                Triple("Light", "Light Mode", "Crisp, clean white interfaces and high readability"),
                                Triple("System", "System Default", "Follows your Android device's system theme")
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                themes.forEach { (modeKey, title, desc) ->
                                    val isSelected = themeMode == modeKey
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else cardBg)
                                            .border(1.dp, if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor, RoundedCornerShape(12.dp))
                                            .clickable {
                                                themeMode = modeKey
                                                onSaveSettings(currentSettings.copy(themeMode = modeKey))
                                            }
                                            .padding(14.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    title,
                                                    color = if (isSelected) (if (isDark) Color.Black else Color.White) else textColor,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    desc,
                                                    color = if (isSelected) (if (isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D8)) else textMuted,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = if (isDark) Color.Black else Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        SettingsCategory.CHAT_DISPLAY -> {
                            MonochromeToggle(
                                title = "Token Streaming",
                                description = "Stream response tokens in real-time as they generate for fast visual emergence.",
                                checked = tokenStreaming,
                                cardBg = cardBg,
                                textColor = textColor,
                                textMuted = textMuted,
                                borderColor = borderColor,
                                isDark = isDark,
                                onCheckedChange = { tokenStreaming = it; syncAndSave() },
                                testTag = "toggle_token_streaming"
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardBg).border(1.dp, borderColor, RoundedCornerShape(14.dp)).padding(16.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Maximum Font Scale", color = textColor, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text("${maxFontSizeSp}sp", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Auto-shrinks dynamic typography smoothly to keep complete thoughts in view.", color = textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                                Slider(
                                    value = maxFontSizeSp.toFloat(),
                                    onValueChange = { maxFontSizeSp = it.toInt(); syncAndSave() },
                                    valueRange = 18f..48f,
                                    steps = 14,
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (isDark) Color.White else Color(0xFF18181B),
                                        activeTrackColor = if (isDark) Color.White else Color(0xFF18181B),
                                        inactiveTrackColor = borderColor
                                    )
                                )
                            }
                        }

                        SettingsCategory.MODELS_REASONING -> {
                            Text("Primary Chat Model", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val modelChoices = listOf(
                                Pair("Gemini Flash Latest (Gemini 3.8 Flash)", "gemini-flash-latest"),
                                Pair("Gemini Pro Latest (Gemini 3.1 Pro)", "gemini-pro-latest"),
                                Pair("Gemini Flash-Lite Latest (Gemini 3.5 Flash-Lite)", "gemini-flash-lite-latest")
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                modelChoices.forEach { (label, modelKey) ->
                                    val isSelected = primaryModel == modelKey
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else cardBg).border(1.dp, if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor, RoundedCornerShape(10.dp)).clickable { primaryModel = modelKey; syncAndSave() }.padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(label, color = if (isSelected) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            if (isSelected) Text("ACTIVE", color = (if (isDark) Color.Black else Color.White).copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                        }

                        SettingsCategory.AI_PERSONA_VOICE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("AI Persona & Voice", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Choosing a persona dictates both their acoustic voice and conversational worldview.", color = textMuted, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { showCreatePersonaDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color.White else Color(0xFF18181B)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("create_person_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = if (isDark) Color.Black else Color.White, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create", color = if (isDark) Color.Black else Color.White, fontSize = 12.5.sp)
                                }
                            }

                            // Active TTS Engine Info Card (Flash-Lite TTS gemini-3.8-flash-lite-tts)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardBg)
                                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color.White else Color(0xFF18181B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = if (isDark) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Active TTS Engine",
                                            color = textColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Flash-Lite TTS (gemini-3.8-flash-lite-tts)",
                                            color = textMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(cardSubBg)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = textColor,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Filter Chips Row
                            val scrollRow = rememberScrollState()
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(scrollRow),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("All", "Male", "Female").forEach { g ->
                                    val isSel = personaGenderFilter == g
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSel) (if (isDark) Color.White else Color(0xFF18181B)) else cardBg).border(1.dp, borderColor, RoundedCornerShape(8.dp)).clickable { personaGenderFilter = g }.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(g, color = if (isSel) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(borderColor))
                                listOf("All", "British", "American", "Australian", "European").forEach { r ->
                                    val isSel = personaRegionFilter == r
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSel) (if (isDark) Color.White else Color(0xFF18181B)) else cardBg).border(1.dp, borderColor, RoundedCornerShape(8.dp)).clickable { personaRegionFilter = r }.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(r, color = if (isSel) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // Custom Created Personas First
                            if (customPersonas.isNotEmpty()) {
                                Text("MY CREATED CHARACTERS", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                customPersonas.forEach { custom ->
                                    val isSelected = selectedVoicePersona == custom.id
                                    val isPreviewingThis = currentlySpeakingPersonaId == custom.id
                                    val isLoadingThis = isLoadingPersonaId == custom.id

                                    PersonaCardItem(
                                        name = custom.name,
                                        title = "${custom.accentRegion} (Custom)",
                                        style = custom.baseVoiceName,
                                        explanation = "Custom persona created in Studio.",
                                        sampleText = "Hello, I am ${custom.name}. Let's get straight to work.",
                                        isSelected = isSelected,
                                        isPreviewing = isPreviewingThis,
                                        isLoading = isLoadingThis,
                                        isDark = isDark,
                                        cardBg = cardBg,
                                        textColor = textColor,
                                        textMuted = textMuted,
                                        borderColor = borderColor,
                                        onSelect = { selectedVoicePersona = custom.id; syncAndSave() },
                                        onPreviewToggle = {
                                            if (isPreviewingThis) onStopPreviewVoicePersona?.invoke()
                                            else onPreviewVoicePersona?.invoke(custom.id)
                                        },
                                        onDelete = { onDeleteCustomPersona(custom.id) }
                                    )
                                }
                            }

                            Text("CURATED REAL CHARACTERS", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                            val filteredCurated = VoicePersonas.CURATED_PEOPLE.filter { p ->
                                (personaGenderFilter == "All" || p.gender == personaGenderFilter) &&
                                (personaRegionFilter == "All" || p.region == personaRegionFilter)
                            }

                            filteredCurated.forEach { p ->
                                val isSelected = selectedVoicePersona == p.id
                                val isPreviewingThis = currentlySpeakingPersonaId == p.id
                                val isLoadingThis = isLoadingPersonaId == p.id

                                PersonaCardItem(
                                    name = p.name,
                                    title = p.title,
                                    style = p.style,
                                    explanation = p.explanation,
                                    sampleText = p.sampleText,
                                    isSelected = isSelected,
                                    isPreviewing = isPreviewingThis,
                                    isLoading = isLoadingThis,
                                    isDark = isDark,
                                    cardBg = cardBg,
                                    textColor = textColor,
                                    textMuted = textMuted,
                                    borderColor = borderColor,
                                    onSelect = { selectedVoicePersona = p.id; syncAndSave() },
                                    onPreviewToggle = {
                                        if (isPreviewingThis) onStopPreviewVoicePersona?.invoke()
                                        else onPreviewVoicePersona?.invoke(p.id)
                                    }
                                )
                            }
                        }

                        SettingsCategory.VOICE_INPUT -> {
                            MonochromeToggle(
                                title = "Silence Trimming",
                                description = "Trims leading, trailing, and excessive pauses from audio recordings before sending.",
                                checked = silenceTrimmingEnabled,
                                cardBg = cardBg,
                                textColor = textColor,
                                textMuted = textMuted,
                                borderColor = borderColor,
                                isDark = isDark,
                                onCheckedChange = { silenceTrimmingEnabled = it; syncAndSave() },
                                testTag = "toggle_silence_trimming"
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardBg).border(1.dp, borderColor, RoundedCornerShape(14.dp)).padding(16.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Minimum Voice Hold Filter", color = textColor, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text("${minVoiceHoldDurationSeconds.toInt()}s", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Touches shorter than this duration are rejected as unintended touches.", color = textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1f, 2f, 3f, 4f, 5f).forEach { sec ->
                                        val isSel = (minVoiceHoldDurationSeconds.toInt() == sec.toInt())
                                        Box(
                                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSel) (if (isDark) Color.White else Color(0xFF18181B)) else cardSubBg).border(1.dp, if (isSel) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor, RoundedCornerShape(8.dp)).clickable { minVoiceHoldDurationSeconds = sec; syncAndSave() }.padding(vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${sec.toInt()}s", color = if (isSel) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Text("HAPTIC WAVE PATTERNS (10 CADENCES)", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                            VibrationPatterns.ALL.forEach { pattern ->
                                val isSelected = selectedVibrationPattern == pattern.id
                                val isPreviewingThis = previewingPatternId == pattern.id
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else cardBg).border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor, RoundedCornerShape(14.dp)).clickable { selectedVibrationPattern = pattern.id; syncAndSave() }.padding(14.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pattern.name, color = if (isSelected) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text(pattern.persona, color = if (isSelected) (if (isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D8)) else textMuted, fontSize = 11.5.sp)
                                        }
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) (if (isDark) Color(0xFFE4E4E7) else Color(0xFF27272A)) else cardSubBg).border(1.dp, borderColor, RoundedCornerShape(8.dp)).clickable {
                                                if (isPreviewingThis) {
                                                    onStopPreviewVibration?.invoke()
                                                    previewingPatternId = null
                                                } else {
                                                    previewingPatternId = pattern.id
                                                    onPreviewVibration?.invoke(pattern.id)
                                                    coroutineScope.launch {
                                                        delay(2600L)
                                                        if (previewingPatternId == pattern.id) previewingPatternId = null
                                                    }
                                                }
                                            }.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(if (isPreviewingThis) "Stop" else "Try", color = if (isSelected) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        SettingsCategory.LONG_TERM_MEMORY -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardBg).border(1.dp, borderColor, RoundedCornerShape(14.dp)).clickable { onOpenMemoryVault() }.padding(16.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Open Memory Vault", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("View and search $memoryCount long-term notes", color = textMuted, fontSize = 12.sp)
                                    }
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
                                }
                            }

                            MonochromeToggle(
                                title = "Enable Long-Term Memory",
                                description = "Injects retrieved high-relevance memories into context window for deep personalization.",
                                checked = longTermMemoryEnabled,
                                cardBg = cardBg,
                                textColor = textColor,
                                textMuted = textMuted,
                                borderColor = borderColor,
                                isDark = isDark,
                                onCheckedChange = { longTermMemoryEnabled = it; syncAndSave() },
                                testTag = "toggle_long_term_memory"
                            )
                        }

                        SettingsCategory.API_KEYS -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cardBg).padding(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (apiKeyTab == 0) (if (isDark) Color.White else Color(0xFF18181B)) else Color.Transparent).clickable { apiKeyTab = 0 }.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("AI Studio APIs (${apiKeys.size})", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (apiKeyTab == 0) (if (isDark) Color.Black else Color.White) else textMuted)
                                }
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (apiKeyTab == 1) (if (isDark) Color.White else Color(0xFF18181B)) else Color.Transparent).clickable { apiKeyTab = 1 }.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Tavily APIs (${tavilyApiKeys.size})", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (apiKeyTab == 1) (if (isDark) Color.Black else Color.White) else textMuted)
                                }
                            }

                            if (apiKeyTab == 0) {
                                apiKeys.forEachIndexed { idx, item ->
                                    MonochromeKeyCard(item = item, index = idx + 1, cardBg = cardBg, textColor = textColor, textMuted = textMuted, borderColor = borderColor, onDelete = { onDeleteKey(item.id) })
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = newKeyInput,
                                        onValueChange = { newKeyInput = it },
                                        placeholder = { Text("Paste Gemini Key (AIza...)", fontSize = 13.sp, color = textMuted) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = textColor,
                                            unfocusedBorderColor = borderColor,
                                            focusedTextColor = textColor,
                                            unfocusedTextColor = textColor
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { if (newKeyInput.isNotBlank()) { onAddKey(newKeyInput.trim()); newKeyInput = "" } },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color.White else Color(0xFF18181B)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Add", color = if (isDark) Color.Black else Color.White) }
                                }
                            } else {
                                tavilyApiKeys.forEachIndexed { idx, item ->
                                    MonochromeKeyCard(item = item, index = idx + 1, cardBg = cardBg, textColor = textColor, textMuted = textMuted, borderColor = borderColor, onDelete = { onDeleteTavilyKey(item.id) })
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = newTavilyKeyInput,
                                        onValueChange = { newTavilyKeyInput = it },
                                        placeholder = { Text("Paste Tavily Key (tvly-...)", fontSize = 13.sp, color = textMuted) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = textColor,
                                            unfocusedBorderColor = borderColor,
                                            focusedTextColor = textColor,
                                            unfocusedTextColor = textColor
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { if (newTavilyKeyInput.isNotBlank()) { onAddTavilyKey(newTavilyKeyInput.trim()); newTavilyKeyInput = "" } },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color.White else Color(0xFF18181B)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Add", color = if (isDark) Color.Black else Color.White) }
                                }
                            }
                        }

                        SettingsCategory.WEB_SEARCH -> {
                            Text("Web Search Thoroughness", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Configures search depth and live sources retrieved when online search is toggled in chat.",
                                color = textMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )
                            val levels = listOf(
                                Triple("Fast", "Quick Glance", "2 sources • lowest latency"),
                                Triple("Balanced", "Balanced", "5 sources • optimal depth"),
                                Triple("Comprehensive", "Comprehensive", "8 sources • deep web research")
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                levels.forEach { (key, label, desc) ->
                                    val isSelected = tavilyThoroughness == key
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else cardBg)
                                            .border(1.dp, if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor, RoundedCornerShape(10.dp))
                                            .clickable {
                                                tavilyThoroughness = key
                                                syncAndSave()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                label,
                                                color = if (isSelected) (if (isDark) Color.Black else Color.White) else textColor,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                desc,
                                                color = if (isSelected) (if (isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D8)) else textMuted,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (isDark) Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Persona Studio Modal
    if (showCreatePersonaDialog) {
        PersonaStudioDialog(
            isDark = isDark,
            bgColor = cardBg,
            textColor = textColor,
            textMuted = textMuted,
            borderColor = borderColor,
            onDismiss = { showCreatePersonaDialog = false },
            onSave = { created ->
                onSaveCustomPersona(created)
                showCreatePersonaDialog = false
            }
        )
    }
}

@Composable
private fun PersonaCardItem(
    name: String,
    title: String,
    style: String,
    explanation: String,
    sampleText: String,
    isSelected: Boolean,
    isPreviewing: Boolean,
    isLoading: Boolean,
    isDark: Boolean,
    cardBg: Color,
    textColor: Color,
    textMuted: Color,
    borderColor: Color,
    onSelect: () -> Unit,
    onPreviewToggle: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else cardBg)
            .border(1.dp, if (isSelected) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) (if (isDark) Color.Black else Color.White) else Color.Transparent)
                    .border(1.5.dp, if (isSelected) (if (isDark) Color.Black else Color.White) else textMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = if (isDark) Color.White else Color(0xFF18181B), modifier = Modifier.size(13.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("$name • $title", color = if (isSelected) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text(style, color = if (isSelected) (if (isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D8)) else textMuted, fontSize = 11.5.sp)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPreviewing) (if (isSelected) (if (isDark) Color.Black else Color.White) else (if (isDark) Color.White else Color(0xFF18181B))) else (if (isSelected) (if (isDark) Color(0xFFE4E4E7) else Color(0xFF27272A)) else (if (isDark) Color(0xFF27272A) else Color.White)))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable(onClick = onPreviewToggle)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = if (isSelected) (if (isDark) Color.Black else Color.White) else textColor, strokeWidth = 1.5.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val iconTint = if (isPreviewing) {
                            if (isSelected) (if (isDark) Color.White else Color.Black) else (if (isDark) Color.Black else Color.White)
                        } else {
                            if (isSelected) (if (isDark) Color.Black else Color.White) else textColor
                        }
                        Icon(imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, tint = iconTint, modifier = Modifier.size(13.dp))
                        Text(if (isPreviewing) "Stop" else "Try", color = iconTint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(explanation, color = if (isSelected) (if (isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D8)) else textMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 30.dp))
        Text("\"$sampleText\"", color = if (isSelected) (if (isDark) Color(0xFF52525B) else Color(0xFFA1A1AA)) else textMuted, fontSize = 11.5.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(start = 30.dp, top = 2.dp))
    }
}

@Composable
fun PersonaStudioDialog(
    isDark: Boolean,
    bgColor: Color,
    textColor: Color,
    textMuted: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onSave: (CustomPersona) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var baseVoiceName by remember { mutableStateOf("Charon") }
    var accentRegion by remember { mutableStateOf("British (Northern / Yorkshire)") }

    var slangLevel by remember { mutableFloatStateOf(0.4f) }
    var warmthLevel by remember { mutableFloatStateOf(0.5f) }
    var outlookLevel by remember { mutableFloatStateOf(0.6f) }
    var directnessLevel by remember { mutableFloatStateOf(0.7f) }
    var verbosityLevel by remember { mutableFloatStateOf(0.3f) }

    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Persona Studio • Create Person", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(480.dp).verticalScroll(scroll)) {
                Text("Stage 1: Identity & Accent", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Person's Name (e.g. David)", color = textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = textColor,
                        unfocusedBorderColor = borderColor,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Male", "Female", "Neutral").forEach { g ->
                        val isSel = gender == g
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSel) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor).clickable { gender = g }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(g, color = if (isSel) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Region & Accent:", fontSize = 12.sp, color = textMuted)
                val accentScroll = rememberScrollState()
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(accentScroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoicePersonas.REGIONAL_ACCENTS.forEach { acc ->
                        val isSel = accentRegion == acc
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSel) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor).clickable { accentRegion = acc }.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(acc, color = if (isSel) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Stage 2: Acoustic Baseline Voice", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                val matchingVoices = VoicePersonas.BASELINE_VOICES.filter { it.gender == gender }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    matchingVoices.forEach { v ->
                        val isSel = baseVoiceName == v.name
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (isSel) (if (isDark) Color.White else Color(0xFF18181B)) else borderColor).clickable { baseVoiceName = v.name }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(v.name, color = if (isSel) (if (isDark) Color.Black else Color.White) else textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(v.acousticTexture, color = if (isSel) (if (isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D8)) else textMuted, fontSize = 10.5.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Stage 3: Personality Spectrum Sliders", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)

                SpectrumSliderItem(title = "Register", leftLabel = "Street Slang", rightLabel = "Corporate", value = slangLevel, textColor = textColor, textMuted = textMuted, borderColor = borderColor, isDark = isDark, onValueChange = { slangLevel = it })
                SpectrumSliderItem(title = "Social Warmth", leftLabel = "Compassionate", rightLabel = "Cold / Cynical", value = warmthLevel, textColor = textColor, textMuted = textMuted, borderColor = borderColor, isDark = isDark, onValueChange = { warmthLevel = it })
                SpectrumSliderItem(title = "Worldview", leftLabel = "Optimist", rightLabel = "Pessimist", value = outlookLevel, textColor = textColor, textMuted = textMuted, borderColor = borderColor, isDark = isDark, onValueChange = { outlookLevel = it })
                SpectrumSliderItem(title = "Directness", leftLabel = "Diplomatic", rightLabel = "Brutally Blunt", value = directnessLevel, textColor = textColor, textMuted = textMuted, borderColor = borderColor, isDark = isDark, onValueChange = { directnessLevel = it })
                SpectrumSliderItem(title = "Verbosity", leftLabel = "Terse (Few Words)", rightLabel = "Expansive", value = verbosityLevel, textColor = textColor, textMuted = textMuted, borderColor = borderColor, isDark = isDark, onValueChange = { verbosityLevel = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            CustomPersona(
                                name = name.trim(),
                                gender = gender,
                                baseVoiceName = baseVoiceName,
                                accentRegion = accentRegion,
                                slangLevel = slangLevel,
                                warmthLevel = warmthLevel,
                                outlookLevel = outlookLevel,
                                directnessLevel = directnessLevel,
                                verbosityLevel = verbosityLevel
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color.White else Color(0xFF18181B))
            ) { Text("Save Person", color = if (isDark) Color.Black else Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = textMuted) }
        },
        containerColor = bgColor
    )
}

@Composable
private fun SpectrumSliderItem(
    title: String,
    leftLabel: String,
    rightLabel: String,
    value: Float,
    textColor: Color,
    textMuted: Color,
    borderColor: Color,
    isDark: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leftLabel, fontSize = 10.5.sp, color = textMuted)
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(rightLabel, fontSize = 10.5.sp, color = textMuted)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = if (isDark) Color.White else Color(0xFF18181B),
                activeTrackColor = if (isDark) Color.White else Color(0xFF18181B),
                inactiveTrackColor = borderColor
            )
        )
    }
}

@Composable
private fun CategoryHubCard(
    category: SettingsCategory,
    cardBg: Color,
    textColor: Color,
    textMuted: Color,
    borderColor: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardBg).border(1.dp, borderColor, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(imageVector = category.icon, contentDescription = null, tint = textColor, modifier = Modifier.size(19.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(category.title, color = textColor, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                Text(category.description, color = textMuted, fontSize = 12.sp)
            }
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = textMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun MonochromeToggle(
    title: String,
    description: String,
    checked: Boolean,
    cardBg: Color,
    textColor: Color,
    textMuted: Color,
    borderColor: Color,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardBg).border(1.dp, borderColor, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = textMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (isDark) Color.Black else Color.White,
                checkedTrackColor = if (isDark) Color.White else Color(0xFF18181B),
                uncheckedThumbColor = textMuted,
                uncheckedTrackColor = borderColor,
                uncheckedBorderColor = borderColor
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun MonochromeKeyCard(
    item: ApiKeyItem,
    index: Int,
    cardBg: Color,
    textColor: Color,
    textMuted: Color,
    borderColor: Color,
    onDelete: () -> Unit
) {
    val isFailed = item.status == KeyStatus.FAILED
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (isFailed) Color(0xFF7F1D1D).copy(alpha = 0.2f) else cardBg).border(1.dp, if (isFailed) Color(0xFFEF4444) else borderColor, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isFailed) Color(0xFFEF4444) else Color(0xFF10B981)))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(item.maskedKey, color = textMuted, fontSize = 11.5.sp)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = textMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
