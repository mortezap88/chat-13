package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.network.GeminiPoolService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

class VoicePersonaManager(
    private val context: Context,
    private val poolService: GeminiPoolService
) {
    var currentlySpeakingPersonaId by mutableStateOf<String?>(null)
        private set

    var isLoadingPersonaId by mutableStateOf<String?>(null)
        private set

    var currentlySpeakingMessageId by mutableStateOf<String?>(null)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var androidTts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var activeTempFile: File? = null

    init {
        try {
            androidTts = TextToSpeech(context) { status ->
                isTtsInitialized = (status == TextToSpeech.SUCCESS)
                if (isTtsInitialized) {
                    androidTts?.language = Locale.US
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePersonaManager", "Error init TTS engine: ${e.message}")
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            activeTempFile?.delete()
        } catch (_: Exception) {}
        activeTempFile = null

        try {
            if (isTtsInitialized) {
                androidTts?.stop()
            }
        } catch (_: Exception) {}

        currentlySpeakingPersonaId = null
        isLoadingPersonaId = null
        currentlySpeakingMessageId = null
    }

    companion object {
        const val GEMINI_TTS_MODEL = "gemini-3.8-flash-lite-tts"
    }

    fun previewPersona(
        persona: VoicePersona,
        scope: CoroutineScope
    ) {
        stop()
        isLoadingPersonaId = persona.id

        scope.launch {
            val audioResult = poolService.generateTtsAudio(
                text = persona.sampleText,
                voiceName = persona.geminiVoiceName,
                model = GEMINI_TTS_MODEL
            )

            withContext(Dispatchers.Main) {
                isLoadingPersonaId = null

                audioResult.onSuccess { audioBytes ->
                    playAudioBytes(audioBytes, persona.id)
                }.onFailure { err ->
                    Log.w("VoicePersonaManager", "Gemini TTS preview failed: ${err.message}")
                    android.widget.Toast.makeText(context, "Gemini TTS unavailable", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun speakChatMessage(
        messageId: String,
        text: String,
        persona: VoicePersona,
        scope: CoroutineScope
    ) {
        stop()
        currentlySpeakingMessageId = messageId
        isLoadingPersonaId = persona.id

        val speechText = if (text.length > 2000) {
            text.take(2000) + "… (speech truncated)"
        } else {
            text
        }

        scope.launch {
            val audioResult = poolService.generateTtsAudio(
                text = speechText,
                voiceName = persona.geminiVoiceName,
                model = GEMINI_TTS_MODEL
            )

            withContext(Dispatchers.Main) {
                isLoadingPersonaId = null

                audioResult.onSuccess { audioBytes ->
                    playAudioBytes(audioBytes, persona.id, messageId)
                }.onFailure { err ->
                    currentlySpeakingMessageId = null
                    Log.w("VoicePersonaManager", "Gemini TTS chat playback failed: ${err.message}")
                    android.widget.Toast.makeText(context, "Gemini TTS unavailable", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playAudioBytes(
        audioBytes: ByteArray,
        personaId: String,
        messageId: String? = null
    ) {
        if (audioBytes.isEmpty()) {
            currentlySpeakingPersonaId = null
            currentlySpeakingMessageId = null
            return
        }

        try {
            stop()
            val tempFile = File.createTempFile("gemini_tts_", ".wav", context.cacheDir)
            activeTempFile = tempFile
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            val player = MediaPlayer()
            FileInputStream(tempFile).use { fis ->
                player.setDataSource(fis.fd)
            }

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            player.setOnCompletionListener {
                currentlySpeakingPersonaId = null
                currentlySpeakingMessageId = null
                try { it.release() } catch (_: Exception) {}
                mediaPlayer = null
                try { tempFile.delete() } catch (_: Exception) {}
                if (activeTempFile == tempFile) activeTempFile = null
            }

            player.setOnErrorListener { mp, what, extra ->
                Log.e("VoicePersonaManager", "MediaPlayer error: what=$what, extra=$extra")
                currentlySpeakingPersonaId = null
                currentlySpeakingMessageId = null
                try { mp.release() } catch (_: Exception) {}
                mediaPlayer = null
                try { tempFile.delete() } catch (_: Exception) {}
                if (activeTempFile == tempFile) activeTempFile = null
                true
            }

            player.prepare()
            player.start()

            mediaPlayer = player
            currentlySpeakingPersonaId = personaId
            currentlySpeakingMessageId = messageId
        } catch (e: Exception) {
            Log.e("VoicePersonaManager", "Play audio error: ${e.message}", e)
            currentlySpeakingPersonaId = null
            currentlySpeakingMessageId = null
        }
    }

    private fun fallbackSpeak(
        text: String,
        pitch: Float,
        speechRate: Float,
        personaId: String,
        messageId: String? = null
    ) {
        if (!isTtsInitialized || androidTts == null) {
            currentlySpeakingPersonaId = null
            currentlySpeakingMessageId = null
            return
        }
        try {
            androidTts?.setPitch(pitch)
            androidTts?.setSpeechRate(speechRate)
            currentlySpeakingPersonaId = personaId
            currentlySpeakingMessageId = messageId

            val utteranceId = "tts_${System.currentTimeMillis()}"
            androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    currentlySpeakingPersonaId = null
                    currentlySpeakingMessageId = null
                }
                override fun onError(utteranceId: String?) {
                    currentlySpeakingPersonaId = null
                    currentlySpeakingMessageId = null
                }
            })
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e("VoicePersonaManager", "Fallback TTS error: ${e.message}")
            currentlySpeakingPersonaId = null
            currentlySpeakingMessageId = null
        }
    }
}
