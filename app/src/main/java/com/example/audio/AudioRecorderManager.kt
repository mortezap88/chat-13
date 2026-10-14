package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import com.example.data.AudioRecordingResult
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioRecorderManager(
    private val context: Context
) {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    private var recordStartTime: Long = 0
    private val totalPcmBytes = ByteArrayOutputStream()

    private var waveVibrationThread: Thread? = null
    private var previewThread: Thread? = null
    private var isPreviewing = false

    private fun getVibrator(): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    fun startPatternVibration(patternId: String = "ocean_surge") {
        try {
            stopVibration()
            val vibrator = getVibrator() ?: return
            val pattern = VibrationPatterns.findById(patternId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                val waveEffect = VibrationEffect.createWaveform(
                    pattern.timings,
                    pattern.amplitudes,
                    pattern.repeatIndex
                )
                vibrator.vibrate(waveEffect)
            } else {
                waveVibrationThread?.interrupt()
                waveVibrationThread = thread(start = true, isDaemon = true) {
                    try {
                        var timingIdx = 0
                        while (isRecording && !Thread.currentThread().isInterrupted) {
                            val dur = pattern.timings.getOrNull(timingIdx)?.coerceAtLeast(10L) ?: 50L
                            val amp = pattern.amplitudes.getOrNull(timingIdx) ?: 100
                            if (amp > 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(dur, amp.coerceIn(1, 255)))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(dur)
                                }
                            }
                            Thread.sleep(dur)
                            timingIdx++
                            if (timingIdx >= pattern.timings.size) {
                                timingIdx = pattern.repeatIndex.coerceIn(0, pattern.timings.size - 1)
                            }
                        }
                    } catch (_: InterruptedException) {}
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Pattern vibration error: ${e.message}")
        }
    }

    fun previewPattern(patternId: String, onFinished: (() -> Unit)? = null) {
        stopVibration()
        isPreviewing = true
        val vibrator = getVibrator() ?: run {
            isPreviewing = false
            onFinished?.invoke()
            return
        }
        val pattern = VibrationPatterns.findById(patternId)

        previewThread?.interrupt()
        previewThread = thread(start = true, isDaemon = true) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                    val waveEffect = VibrationEffect.createWaveform(
                        pattern.timings,
                        pattern.amplitudes,
                        pattern.repeatIndex
                    )
                    vibrator.vibrate(waveEffect)
                    Thread.sleep(2500L)
                    vibrator.cancel()
                } else {
                    var timingIdx = 0
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 2500L && isPreviewing && !Thread.currentThread().isInterrupted) {
                        val dur = pattern.timings.getOrNull(timingIdx)?.coerceAtLeast(10L) ?: 50L
                        val amp = pattern.amplitudes.getOrNull(timingIdx) ?: 100
                        if (amp > 0) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(dur, amp.coerceIn(1, 255)))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(dur)
                            }
                        }
                        Thread.sleep(dur)
                        timingIdx++
                        if (timingIdx >= pattern.timings.size) {
                            timingIdx = pattern.repeatIndex.coerceIn(0, pattern.timings.size - 1)
                        }
                    }
                    vibrator.cancel()
                }
            } catch (_: InterruptedException) {
                try { vibrator.cancel() } catch (_: Exception) {}
            } finally {
                isPreviewing = false
                onFinished?.invoke()
            }
        }
    }

    fun stopVibration() {
        try {
            isPreviewing = false
            previewThread?.interrupt()
            previewThread = null
            waveVibrationThread?.interrupt()
            waveVibrationThread = null
            val vibrator = getVibrator()
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Stop vibration error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        vibrationPattern: String = "ocean_surge"
    ): Boolean {
        if (isRecording) return false
        try {
            isRecording = true
            startPatternVibration(vibrationPattern)

            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufSize.coerceAtLeast(sampleRate * 2)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecorder", "AudioRecord not initialized")
                return false
            }

            record.startRecording()
            audioRecord = record
            recordStartTime = System.currentTimeMillis()

            totalPcmBytes.reset()

            recordingThread = thread(start = true, name = "AudioRecordThread") {
                val readBuffer = ShortArray(1024)
                val byteBuffer = ByteBuffer.allocate(readBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)

                while (isRecording) {
                    val readCount = record.read(readBuffer, 0, readBuffer.size)
                    if (readCount > 0) {
                        byteBuffer.clear()
                        for (i in 0 until readCount) {
                            byteBuffer.putShort(readBuffer[i])
                        }
                        val bytesRead = byteBuffer.array().copyOf(readCount * 2)

                        synchronized(totalPcmBytes) {
                            totalPcmBytes.write(bytesRead)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "startRecording error: ${e.message}")
            stopRecording()
            return false
        }
    }

    fun stopRecording(silenceTrimmingEnabled: Boolean = true): AudioRecordingResult? {
        if (!isRecording) return null
        stopVibration()
        isRecording = false

        try {
            audioRecord?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Release error: ${e.message}")
        } finally {
            audioRecord = null
            recordingThread?.interrupt()
            recordingThread = null
        }

        val durationMs = System.currentTimeMillis() - recordStartTime
        if (durationMs < 300) {
            totalPcmBytes.reset()
            return null
        }

        val fullBytes = synchronized(totalPcmBytes) {
            val full = totalPcmBytes.toByteArray()
            totalPcmBytes.reset()
            full
        }

        val processedBytes = if (silenceTrimmingEnabled) {
            trimSilence(fullBytes)
        } else {
            fullBytes
        }

        val fullWav = pcmToWav(processedBytes, sampleRate, 1, 16)

        return AudioRecordingResult(
            fullAudioBase64 = Base64.encodeToString(fullWav, Base64.NO_WRAP),
            mimeType = "audio/wav",
            durationMs = durationMs
        )
    }

    /**
     * Splits PCM into ~25ms frames, computes RMS, drops leading/trailing silence,
     * and collapses internal silent pauses exceeding 300ms down to 300ms.
     */
    private fun trimSilence(pcmBytes: ByteArray): ByteArray {
        val frameSizeBytes = 800 // 25ms @ 16kHz 16-bit mono (400 samples * 2 bytes)
        val numFrames = pcmBytes.size / frameSizeBytes
        if (numFrames <= 4) return pcmBytes

        val frameRmsList = DoubleArray(numFrames)
        val silenceThreshold = 550.0

        for (f in 0 until numFrames) {
            val offset = f * frameSizeBytes
            var sumSq = 0.0
            val samplesInFrame = frameSizeBytes / 2
            for (s in 0 until samplesInFrame) {
                val low = pcmBytes[offset + s * 2].toInt() and 0xFF
                val high = pcmBytes[offset + s * 2 + 1].toInt()
                val sample = ((high shl 8) or low).toShort()
                sumSq += sample * sample
            }
            frameRmsList[f] = sqrt(sumSq / samplesInFrame)
        }

        var firstNonSilent = -1
        for (f in 0 until numFrames) {
            if (frameRmsList[f] >= silenceThreshold) {
                firstNonSilent = f
                break
            }
        }

        var lastNonSilent = -1
        for (f in (numFrames - 1) downTo 0) {
            if (frameRmsList[f] >= silenceThreshold) {
                lastNonSilent = f
                break
            }
        }

        if (firstNonSilent == -1 || lastNonSilent == -1 || firstNonSilent > lastNonSilent) {
            return pcmBytes
        }

        val maxInternalSilentFrames = 12 // ~300ms
        val output = ByteArrayOutputStream()
        var consecutiveSilent = 0

        for (f in firstNonSilent..lastNonSilent) {
            val isSilent = frameRmsList[f] < silenceThreshold
            if (isSilent) {
                consecutiveSilent++
                if (consecutiveSilent <= maxInternalSilentFrames) {
                    val offset = f * frameSizeBytes
                    output.write(pcmBytes, offset, frameSizeBytes)
                }
            } else {
                consecutiveSilent = 0
                val offset = f * frameSizeBytes
                output.write(pcmBytes, offset, frameSizeBytes)
            }
        }

        val trimmed = output.toByteArray()
        return if (trimmed.isNotEmpty()) trimmed else pcmBytes
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size + 36
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
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        val wavStream = ByteArrayOutputStream(44 + pcmData.size)
        wavStream.write(header)
        wavStream.write(pcmData)
        return wavStream.toByteArray()
    }
}
