package com.example.network

import android.util.Base64
import android.util.Log
import com.example.data.ApiKeyItem
import com.example.data.AppSettings
import com.example.data.ChatMessage
import com.example.data.CorrectionType
import com.example.data.ExtractedMemoryItem
import com.example.data.KeyStatus
import com.example.data.LinguisticCorrection
import com.example.data.UnifiedTurnUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiPoolService(
    private var apiKeys: List<ApiKeyItem> = emptyList(),
    var onKeyStatusChanged: ((Int, KeyStatus, String?) -> Unit)? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var activeKeyIndex: Int = 0
        private set

    fun updateKeys(newKeys: List<ApiKeyItem>) {
        apiKeys = newKeys
        if (activeKeyIndex >= apiKeys.size) activeKeyIndex = 0
    }

    private fun markKeyFailed(index: Int, reason: String) {
        if (index in apiKeys.indices) {
            onKeyStatusChanged?.invoke(index, KeyStatus.FAILED, reason)
        }
        if (apiKeys.isNotEmpty()) {
            activeKeyIndex = (activeKeyIndex + 1) % apiKeys.size
        }
    }

    private fun markKeySuccess(index: Int) {
        if (index in apiKeys.indices) {
            onKeyStatusChanged?.invoke(index, KeyStatus.WORKING, null)
        }
    }

    private fun resolveModelName(raw: String): String {
        val clean = raw.trim().removePrefix("models/")
        return if (clean.isNotBlank()) clean else "gemini-flash-latest"
    }

    fun streamUnifiedTurn(
        session: List<ChatMessage>,
        systemInstruction: String,
        settings: AppSettings,
        userText: String? = null,
        audioBase64: String? = null
    ): Flow<UnifiedTurnUpdate> = callbackFlow {
        if (apiKeys.isEmpty()) {
            close(Exception("No Gemini API keys configured. Open Settings to add your key."))
            return@callbackFlow
        }

        val targetModel = resolveModelName(settings.primaryModel)
        var attempts = 0
        var success = false
        var lastErrorMessage = "All available Gemini API keys failed"

        while (attempts < apiKeys.size && !success) {
            val keyIndex = activeKeyIndex
            val key = apiKeys[keyIndex].key
            try {
                val jsonBody = buildUnifiedRequestBody(
                    session = session,
                    systemInstruction = systemInstruction,
                    settings = settings,
                    targetModel = targetModel,
                    userText = userText,
                    audioBase64 = audioBase64
                )
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:streamGenerateContent?alt=sse&key=$key"

                val request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", key)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response: Response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    val parsedMsg = try {
                        JSONObject(err).optJSONObject("error")?.optString("message", "")
                    } catch (_: Exception) { null }

                    val detail = if (!parsedMsg.isNullOrBlank()) parsedMsg else "HTTP ${response.code}"
                    lastErrorMessage = detail
                    Log.w("GeminiPool", "Unified model $targetModel returned ${response.code}: $err")
                    markKeyFailed(keyIndex, detail)
                    attempts++
                    continue
                }

                markKeySuccess(keyIndex)
                success = true

                val source = response.body?.byteStream()
                if (source != null) {
                    val reader = BufferedReader(InputStreamReader(source))
                    var line: String?
                    val rawAccumulated = StringBuilder()
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            try {
                                val root = JSONObject(data)
                                val candidates = root.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val candidate = candidates.getJSONObject(0)
                                    val content = candidate.optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    if (parts != null) {
                                        for (p in 0 until parts.length()) {
                                            val part = parts.getJSONObject(p)
                                            val chunk = part.optString("text", "")
                                            if (chunk.isNotEmpty()) {
                                                rawAccumulated.append(chunk)
                                                val update = parseUnifiedResponse(rawAccumulated.toString(), isFinished = false)
                                                trySend(update)
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    val finalUpdate = parseUnifiedResponse(rawAccumulated.toString(), isFinished = true)
                    trySend(finalUpdate)
                }
                close()
            } catch (e: Exception) {
                val errDetail = when (e) {
                    is UnknownHostException -> "Unable to reach Google servers. Check connection."
                    is SocketTimeoutException -> "Connection timed out during streaming. Please retry."
                    else -> e.message ?: "Connection error"
                }
                lastErrorMessage = errDetail
                Log.e("GeminiPool", "Unified streaming exception: $errDetail", e)
                markKeyFailed(keyIndex, lastErrorMessage)
                attempts++
            }
        }

        if (!success) {
            close(Exception(lastErrorMessage))
        }

        awaitClose {}
    }.flowOn(Dispatchers.IO)

    private fun cleanStreamingAnswer(text: String): String {
        val idx = text.indexOf("<<<")
        return if (idx >= 0) text.substring(0, idx).trimEnd() else text
    }

    private fun parseCorrectionsJson(raw: String): List<LinguisticCorrection> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start >= 0 && end > start) {
            val jsonSubstring = raw.substring(start, end + 1)
            try {
                val arr = JSONArray(jsonSubstring)
                val list = mutableListOf<LinguisticCorrection>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val typeStr = obj.optString("type", "GRAMMAR").uppercase()
                    val type = if (typeStr.contains("WORD")) CorrectionType.WORD_CHOICE else CorrectionType.GRAMMAR
                    list.add(
                        LinguisticCorrection(
                            id = UUID.randomUUID().toString(),
                            type = type,
                            original = obj.optString("original", ""),
                            suggested = obj.optString("suggested", ""),
                            reason = obj.optString("reason", "")
                        )
                    )
                }
                return list
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun parseUnifiedResponse(rawText: String, isFinished: Boolean): UnifiedTurnUpdate {
        var currentAnswer = ""
        var currentUserSentence: String? = null
        var currentCorrections = emptyList<LinguisticCorrection>()

        val afterAnswer = if (rawText.contains("<<<ANSWER>>>")) {
            rawText.substringAfter("<<<ANSWER>>>")
        } else {
            rawText
        }

        if (afterAnswer.contains("<<<USER_TRANSCRIPT>>>")) {
            currentAnswer = afterAnswer.substringBefore("<<<USER_TRANSCRIPT>>>").trim()
            val afterTranscript = afterAnswer.substringAfter("<<<USER_TRANSCRIPT>>>")

            val userTextSection = if (afterTranscript.contains("<<<CORRECTIONS>>>")) {
                afterTranscript.substringBefore("<<<CORRECTIONS>>>")
            } else {
                afterTranscript
            }.trim()

            if (userTextSection.isNotBlank()) {
                currentUserSentence = if (isFinished) userTextSection else cleanStreamingAnswer(userTextSection).trim()
            }

            if (afterTranscript.contains("<<<CORRECTIONS>>>")) {
                val corrRaw = afterTranscript.substringAfter("<<<CORRECTIONS>>>").trim()
                val parsed = parseCorrectionsJson(corrRaw)
                if (parsed.isNotEmpty()) {
                    currentCorrections = parsed
                }
            }
        } else {
            currentAnswer = if (isFinished) afterAnswer.trim() else cleanStreamingAnswer(afterAnswer)
        }

        return UnifiedTurnUpdate(
            answer = currentAnswer,
            userSentence = currentUserSentence,
            corrections = currentCorrections,
            isComplete = isFinished
        )
    }

    private fun buildUnifiedRequestBody(
        session: List<ChatMessage>,
        systemInstruction: String,
        settings: AppSettings,
        targetModel: String,
        userText: String?,
        audioBase64: String?
    ): JSONObject {
        val root = JSONObject()

        val formattingGuidance = """
Respond strictly adhering to this 3-part layout:
<<<ANSWER>>>
[Your direct conversational answer to the user]
<<<USER_TRANSCRIPT>>>
[The exact user sentence uttered in the audio or typed by user]
<<<CORRECTIONS>>>
[{"type":"GRAMMAR"|"WORD_CHOICE","original":"faulty substring","suggested":"correction","reason":"explanation"}]
(Note: If there are no grammar or word choice issues, output [] for CORRECTIONS)
""".trimIndent()

        val fullSystemInstruction = if (systemInstruction.isNotBlank()) {
            "$systemInstruction\n\n$formattingGuidance"
        } else {
            formattingGuidance
        }

        val sysObj = JSONObject()
        val sysParts = JSONArray()
        sysParts.put(JSONObject().apply { put("text", fullSystemInstruction) })
        sysObj.put("parts", sysParts)
        root.put("systemInstruction", sysObj)

        val contentsArr = JSONArray()

        val pastCompletedTurns = session.dropLast(1)
            .filter { it.userText.isNotBlank() && it.aiResponse.isNotBlank() }
            .takeLast(12)

        for (turn in pastCompletedTurns) {
            contentsArr.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", turn.userText)))
            })
            contentsArr.put(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().put("text", turn.aiResponse)))
            })
        }

        val currentParts = JSONArray()
        if (!audioBase64.isNullOrBlank()) {
            currentParts.put(JSONObject().apply {
                val inline = JSONObject().apply {
                    put("mimeType", "audio/wav")
                    put("data", audioBase64)
                }
                put("inlineData", inline)
            })
            val audioPrompt = if (!userText.isNullOrBlank()) {
                "User attached audio and notes: \"$userText\". Listen carefully, provide your answer in ANSWER, transcribe verbatim in USER_TRANSCRIPT, and inspect grammar in CORRECTIONS."
            } else {
                "Listen carefully to the audio, provide your answer in ANSWER, transcribe verbatim in USER_TRANSCRIPT, and inspect grammar in CORRECTIONS."
            }
            currentParts.put(JSONObject().apply { put("text", audioPrompt) })
        } else {
            val textPrompt = (userText ?: "").trim()
            currentParts.put(JSONObject().apply { put("text", textPrompt) })
        }

        contentsArr.put(JSONObject().apply {
            put("role", "user")
            put("parts", currentParts)
        })

        root.put("contents", contentsArr)

        val generationConfig = JSONObject()
        if (settings.thinkingEnabled && !targetModel.contains("lite", ignoreCase = true)) {
            val thinkingLevelValue = when (settings.thinkingLevel.lowercase().trim()) {
                "low" -> "low"
                "high" -> "high"
                else -> "medium"
            }
            val thinkingConfig = JSONObject()
            thinkingConfig.put("thinkingLevel", thinkingLevelValue)
            generationConfig.put("thinkingConfig", thinkingConfig)
        }
        root.put("generationConfig", generationConfig)

        return root
    }

    suspend fun consolidateSessionIntoMemories(
        session: List<ChatMessage>,
        model: String = "gemini-flash-latest"
    ): Result<List<ExtractedMemoryItem>> = withContext(Dispatchers.IO) {
        if (apiKeys.isEmpty() || session.isEmpty()) return@withContext Result.success(emptyList())

        val targetModel = resolveModelName(model)
        val prompt = buildString {
            appendLine("Analyze the following conversation context and extract 2 to 8 atomic, permanent memories about the user.")
            appendLine("Format strictly as a JSON array with: content, category (FACT, PREFERENCE, PROJECT, LINGUISTIC, EPISODIC, GENERAL), importance (1-5), keywords (array of strings).")
            session.forEach {
                appendLine("User: ${it.userText}")
                appendLine("AI: ${it.aiResponse}")
            }
        }

        try {
            val key = apiKeys[activeKeyIndex].key
            val root = JSONObject()
            val contents = JSONArray()
            val content = JSONObject().apply {
                put("role", "user")
                val parts = JSONArray()
                parts.put(JSONObject().apply { put("text", prompt) })
                put("parts", parts)
            }
            contents.put(content)
            root.put("contents", contents)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$key"
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", key)
                .post(root.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(request).execute()
            val raw = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val json = JSONObject(raw)
                val rawText = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    .orEmpty()

                val start = rawText.indexOf('[')
                val end = rawText.lastIndexOf(']')
                if (start >= 0 && end > start) {
                    val jsonArr = JSONArray(rawText.substring(start, end + 1))
                    val items = mutableListOf<ExtractedMemoryItem>()
                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        val kwArr = obj.optJSONArray("keywords")
                        val kws = mutableListOf<String>()
                        if (kwArr != null) {
                            for (k in 0 until kwArr.length()) kws.add(kwArr.getString(k))
                        }
                        items.add(
                            ExtractedMemoryItem(
                                content = obj.optString("content", ""),
                                category = obj.optString("category", "GENERAL"),
                                importance = obj.optInt("importance", 3),
                                keywords = kws
                            )
                        )
                    }
                    return@withContext Result.success(items)
                }
            } else {
                Log.w("GeminiPool", "Memory extraction HTTP ${resp.code}: $raw")
                return@withContext Result.failure(Exception("Extraction failed with HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Log.w("GeminiPool", "Consolidation extraction error: ${e.message}")
            return@withContext Result.failure(e)
        }
        Result.failure(Exception("No distinct long-term facts were found in this conversation."))
    }

    /**
     * High-fidelity TTS with multi-tier model fallbacks and WAV header wrapping.
     */
    suspend fun generateTtsAudio(
        text: String,
        voiceName: String,
        model: String = "gemini-3.8-flash-lite-tts"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (apiKeys.isEmpty()) return@withContext Result.failure(Exception("No API key available"))

        val cleanSpeechText = text
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("<[^>]*>"), "")
            .trim()

        if (cleanSpeechText.isBlank()) return@withContext Result.failure(Exception("Empty speech text"))

        // Cascade: Requested model first, then compatible TTS models
        val candidateModels = listOf(
            resolveModelName(model),
            "gemini-3.8-flash-lite-tts",
            "gemini-3.8-flash-tts"
        ).distinct()

        for (targetModel in candidateModels) {
            var attempts = 0
            while (attempts < apiKeys.size) {
                val keyIndex = activeKeyIndex
                val key = apiKeys[keyIndex].key
                try {
                    val root = JSONObject()
                    val contents = JSONArray()
                    val content = JSONObject().apply {
                        put("role", "user")
                        val parts = JSONArray()
                        parts.put(JSONObject().apply { put("text", cleanSpeechText) })
                        put("parts", parts)
                    }
                    contents.put(content)
                    root.put("contents", contents)

                    val genConfig = JSONObject()
                    genConfig.put("responseModalities", JSONArray().put("AUDIO"))
                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuilt = JSONObject().apply { put("voiceName", voiceName) }
                            put("prebuiltVoiceConfig", prebuilt)
                        }
                        put("voiceConfig", voiceConfig)
                    }
                    genConfig.put("speechConfig", speechConfig)
                    root.put("generationConfig", genConfig)

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$key"
                    val request = Request.Builder()
                        .url(url)
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", key)
                        .post(root.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val resp = client.newCall(request).execute()
                    val body = resp.body?.string().orEmpty()

                    if (resp.isSuccessful) {
                        markKeySuccess(keyIndex)
                        val json = JSONObject(body)
                        val parts = json.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")

                        var audioFound = false
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                val inlineData = part.optJSONObject("inlineData")
                                if (inlineData != null) {
                                    val base64Data = inlineData.optString("data", "")
                                    val mimeType = inlineData.optString("mimeType", "audio/pcm")
                                    if (base64Data.isNotEmpty()) {
                                        audioFound = true
                                        val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        val formattedWav = formatAsPlayableWav(rawBytes, mimeType)
                                        return@withContext Result.success(formattedWav)
                                    }
                                }
                            }
                        }
                        if (!audioFound) {
                            Log.w("GeminiPool", "TTS response on $targetModel contained no inline audio data")
                            markKeyFailed(keyIndex, "No audio data in response")
                            attempts++
                        }
                    } else {
                        Log.w("GeminiPool", "TTS call to $targetModel returned HTTP ${resp.code}: $body")
                        if (resp.code == 404) break
                        markKeyFailed(keyIndex, "HTTP ${resp.code}")
                        attempts++
                    }
                } catch (e: Exception) {
                    Log.w("GeminiPool", "TTS exception on $targetModel: ${e.message}")
                    markKeyFailed(keyIndex, e.message ?: "Connection error")
                    attempts++
                }
            }
        }
        Result.failure(Exception("Failed to generate Gemini TTS audio across all available models and keys"))
    }

    private fun formatAsPlayableWav(rawAudio: ByteArray, mimeType: String): ByteArray {
        // If already RIFF WAV, return directly
        if (rawAudio.size >= 4 &&
            rawAudio[0] == 'R'.code.toByte() &&
            rawAudio[1] == 'I'.code.toByte() &&
            rawAudio[2] == 'F'.code.toByte() &&
            rawAudio[3] == 'F'.code.toByte()
        ) {
            return rawAudio
        }

        val sampleRateMatch = Regex("rate=(\\d+)").find(mimeType.lowercase())
        val sampleRate = sampleRateMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24000
        val channels = 1
        val bitDepth = 16

        val header = ByteArray(44)
        val totalDataLen = rawAudio.size + 36
        val byteRate = sampleRate * channels * bitDepth / 8

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = ((channels * bitDepth / 8)).toByte(); header[33] = 0
        header[34] = bitDepth.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (rawAudio.size and 0xff).toByte()
        header[41] = ((rawAudio.size shr 8) and 0xff).toByte()
        header[42] = ((rawAudio.size shr 16) and 0xff).toByte()
        header[43] = ((rawAudio.size shr 24) and 0xff).toByte()

        val out = java.io.ByteArrayOutputStream(44 + rawAudio.size)
        out.write(header)
        out.write(rawAudio)
        return out.toByteArray()
    }
}
