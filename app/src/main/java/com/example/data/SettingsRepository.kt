package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stream_chat_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PRIMARY_MODEL = "primary_model"
        private const val KEY_THINKING_ENABLED = "thinking_enabled"
        private const val KEY_THINKING_LEVEL = "thinking_level"
        private const val KEY_TOKEN_STREAMING = "token_streaming"
        private const val KEY_FONT_SIZE = "max_font_size"
        private const val KEY_MEMORY_ENABLED = "long_term_memory_enabled"
        private const val KEY_MAX_MEMORIES = "max_retrieved_memories"
        private const val KEY_VIBRATION_PATTERN = "vibration_pattern"
        private const val KEY_MIN_VOICE_HOLD = "min_voice_hold_duration"
        private const val KEY_SELECTED_VOICE_PERSONA = "selected_voice_persona"
        private const val KEY_TTS_AUTO_SPEAK = "tts_auto_speak"
        private const val KEY_TAVILY_SEARCH_ENABLED = "tavily_search_enabled"
        private const val KEY_TAVILY_THOROUGHNESS = "tavily_thoroughness"
        private const val KEY_SILENCE_TRIMMING = "silence_trimming"
        private const val KEY_API_KEYS_JSON = "api_keys_json"
        private const val KEY_TAVILY_API_KEYS_JSON = "tavily_api_keys_json"
        private const val KEY_CHAT_MESSAGES_JSON = "chat_messages_json"
        private const val KEY_CUSTOM_PERSONAS_JSON = "custom_personas_json"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    init {
        // Purge deprecated legacy preference keys
        prefs.edit()
            .remove("is_mic_enabled")
            .remove("mic_sensitivity")
            .remove("silence_pause_duration")
            .remove("transcription_model")
            .remove("smoothing_model")
            .remove("tts_model")
            .remove("dedicated_tts_model_active")
            .remove("contextual_smoothing")
            .remove("grammar_inspector")
            .remove("tavily_max_results")
            .apply()
    }

    fun loadSettings(): AppSettings {
        return AppSettings(
            primaryModel = prefs.getString(KEY_PRIMARY_MODEL, "gemini-flash-latest") ?: "gemini-flash-latest",
            thinkingEnabled = prefs.getBoolean(KEY_THINKING_ENABLED, false),
            thinkingLevel = prefs.getString(KEY_THINKING_LEVEL, "Low") ?: "Low",
            tokenStreaming = prefs.getBoolean(KEY_TOKEN_STREAMING, true),
            maxFontSizeSp = prefs.getInt(KEY_FONT_SIZE, 24),
            longTermMemoryEnabled = prefs.getBoolean(KEY_MEMORY_ENABLED, true),
            maxRetrievedMemories = prefs.getInt(KEY_MAX_MEMORIES, 10),
            vibrationPattern = prefs.getString(KEY_VIBRATION_PATTERN, "ocean_surge") ?: "ocean_surge",
            minVoiceHoldDurationSeconds = prefs.getFloat(KEY_MIN_VOICE_HOLD, 1.0f),
            selectedVoicePersona = prefs.getString(KEY_SELECTED_VOICE_PERSONA, "david_manchester") ?: "david_manchester",
            ttsAutoSpeak = prefs.getBoolean(KEY_TTS_AUTO_SPEAK, false),
            tavilySearchEnabled = prefs.getBoolean(KEY_TAVILY_SEARCH_ENABLED, false),
            tavilyThoroughness = prefs.getString(KEY_TAVILY_THOROUGHNESS, "Balanced") ?: "Balanced",
            silenceTrimmingEnabled = prefs.getBoolean(KEY_SILENCE_TRIMMING, true),
            themeMode = prefs.getString(KEY_THEME_MODE, "Dark") ?: "Dark"
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_PRIMARY_MODEL, settings.primaryModel)
            .putBoolean(KEY_THINKING_ENABLED, settings.thinkingEnabled)
            .putString(KEY_THINKING_LEVEL, settings.thinkingLevel)
            .putBoolean(KEY_TOKEN_STREAMING, settings.tokenStreaming)
            .putInt(KEY_FONT_SIZE, settings.maxFontSizeSp)
            .putBoolean(KEY_MEMORY_ENABLED, settings.longTermMemoryEnabled)
            .putInt(KEY_MAX_MEMORIES, settings.maxRetrievedMemories)
            .putString(KEY_VIBRATION_PATTERN, settings.vibrationPattern)
            .putFloat(KEY_MIN_VOICE_HOLD, settings.minVoiceHoldDurationSeconds)
            .putString(KEY_SELECTED_VOICE_PERSONA, settings.selectedVoicePersona)
            .putBoolean(KEY_TTS_AUTO_SPEAK, settings.ttsAutoSpeak)
            .putBoolean(KEY_TAVILY_SEARCH_ENABLED, settings.tavilySearchEnabled)
            .putString(KEY_TAVILY_THOROUGHNESS, settings.tavilyThoroughness)
            .putBoolean(KEY_SILENCE_TRIMMING, settings.silenceTrimmingEnabled)
            .putString(KEY_THEME_MODE, settings.themeMode)
            .apply()
    }

    fun loadCustomPersonas(): List<CustomPersona> {
        val jsonStr = prefs.getString(KEY_CUSTOM_PERSONAS_JSON, null) ?: return emptyList()
        val list = mutableListOf<CustomPersona>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomPersona(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "David"),
                        gender = obj.optString("gender", "Male"),
                        baseVoiceName = obj.optString("baseVoiceName", "Charon"),
                        accentRegion = obj.optString("accentRegion", "British (Northern / Yorkshire)"),
                        slangLevel = obj.optDouble("slangLevel", 0.5).toFloat(),
                        warmthLevel = obj.optDouble("warmthLevel", 0.5).toFloat(),
                        outlookLevel = obj.optDouble("outlookLevel", 0.5).toFloat(),
                        directnessLevel = obj.optDouble("directnessLevel", 0.5).toFloat(),
                        verbosityLevel = obj.optDouble("verbosityLevel", 0.5).toFloat(),
                        customSystemPrompt = obj.optString("customSystemPrompt", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveCustomPersonas(personas: List<CustomPersona>) {
        val array = JSONArray()
        personas.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("gender", p.gender)
                put("baseVoiceName", p.baseVoiceName)
                put("accentRegion", p.accentRegion)
                put("slangLevel", p.slangLevel)
                put("warmthLevel", p.warmthLevel)
                put("outlookLevel", p.outlookLevel)
                put("directnessLevel", p.directnessLevel)
                put("verbosityLevel", p.verbosityLevel)
                put("customSystemPrompt", p.customSystemPrompt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_PERSONAS_JSON, array.toString()).apply()
    }

    fun loadApiKeys(): List<ApiKeyItem> {
        val jsonStr = prefs.getString(KEY_API_KEYS_JSON, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<ApiKeyItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ApiKeyItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            key = obj.optString("key", ""),
                            label = obj.optString("label", "Key #${i + 1}"),
                            status = try {
                                KeyStatus.valueOf(obj.optString("status", "IDLE"))
                            } catch (_: Exception) {
                                KeyStatus.IDLE
                            },
                            errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) {
                                obj.getString("errorMessage")
                            } else null,
                            failCount = obj.optInt("failCount", 0)
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        val initialList = mutableListOf<ApiKeyItem>()
        val defaultKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String) ?: ""
        } catch (_: Throwable) { "" }

        if (defaultKey.isNotBlank() && defaultKey != "MY_GEMINI_API_KEY") {
            initialList.add(ApiKeyItem(key = defaultKey, label = "Key #1", status = KeyStatus.WORKING))
            saveApiKeys(initialList)
        }
        return initialList
    }

    fun saveApiKeys(keys: List<ApiKeyItem>) {
        val array = JSONArray()
        keys.forEachIndexed { index, item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("key", item.key)
                put("label", "Key #${index + 1}")
                put("status", item.status.name)
                put("errorMessage", item.errorMessage ?: JSONObject.NULL)
                put("failCount", item.failCount)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_API_KEYS_JSON, array.toString()).apply()
    }

    fun loadTavilyApiKeys(): List<ApiKeyItem> {
        val jsonStr = prefs.getString(KEY_TAVILY_API_KEYS_JSON, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<ApiKeyItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ApiKeyItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            key = obj.optString("key", ""),
                            label = obj.optString("label", "Tavily Key #${i + 1}"),
                            status = try {
                                KeyStatus.valueOf(obj.optString("status", "IDLE"))
                            } catch (_: Exception) {
                                KeyStatus.IDLE
                            },
                            errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) {
                                obj.getString("errorMessage")
                            } else null,
                            failCount = obj.optInt("failCount", 0)
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun saveTavilyApiKeys(keys: List<ApiKeyItem>) {
        val array = JSONArray()
        keys.forEachIndexed { index, item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("key", item.key)
                put("label", "Tavily Key #${index + 1}")
                put("status", item.status.name)
                put("errorMessage", item.errorMessage ?: JSONObject.NULL)
                put("failCount", item.failCount)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_TAVILY_API_KEYS_JSON, array.toString()).apply()
    }

    fun loadChatMessages(): List<ChatMessage> {
        val jsonStr = prefs.getString(KEY_CHAT_MESSAGES_JSON, null) ?: return emptyList()
        val list = mutableListOf<ChatMessage>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val corrList = mutableListOf<LinguisticCorrection>()
                val corrArray = obj.optJSONArray("corrections")
                if (corrArray != null) {
                    for (j in 0 until corrArray.length()) {
                        val cObj = corrArray.getJSONObject(j)
                        corrList.add(
                            LinguisticCorrection(
                                id = cObj.optString("id", UUID.randomUUID().toString()),
                                type = try {
                                    CorrectionType.valueOf(cObj.optString("type", "GRAMMAR"))
                                } catch (_: Exception) { CorrectionType.GRAMMAR },
                                original = cObj.optString("original", ""),
                                suggested = cObj.optString("suggested", ""),
                                reason = cObj.optString("reason", "")
                            )
                        )
                    }
                }
                list.add(
                    ChatMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        userText = obj.optString("userText", ""),
                        isAudioInput = obj.optBoolean("isAudioInput", false),
                        aiResponse = obj.optString("aiResponse", ""),
                        isStreaming = false,
                        error = if (obj.has("error") && !obj.isNull("error")) obj.getString("error") else null,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        modelUsed = obj.optString("modelUsed", "gemini-flash-latest"),
                        corrections = corrList,
                        isAnalyzingCorrections = false,
                        isWebSearchEnhanced = obj.optBoolean("isWebSearchEnhanced", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveChatMessages(messages: List<ChatMessage>) {
        val array = JSONArray()
        val toSave = if (messages.size > 50) messages.takeLast(50) else messages
        for (msg in toSave) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("userText", msg.userText)
                put("isAudioInput", msg.isAudioInput)
                put("aiResponse", msg.aiResponse)
                put("error", msg.error ?: JSONObject.NULL)
                put("timestamp", msg.timestamp)
                put("modelUsed", msg.modelUsed)
                put("isWebSearchEnhanced", msg.isWebSearchEnhanced)
                val corrArr = JSONArray()
                for (c in msg.corrections) {
                    corrArr.put(JSONObject().apply {
                        put("id", c.id)
                        put("type", c.type.name)
                        put("original", c.original)
                        put("suggested", c.suggested)
                        put("reason", c.reason)
                    })
                }
                put("corrections", corrArr)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CHAT_MESSAGES_JSON, array.toString()).apply()
    }
}
