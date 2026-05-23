package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class GameAudioPlayer {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playCoin() {
        playSound { sampleRate ->
            val duration = 0.12f
            val numSamples = (sampleRate * duration).toInt()
            val samples = ShortArray(numSamples)
            val breakPoint = (numSamples * 0.35f).toInt()
            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val freq = if (i < breakPoint) 980f else 1470f
                val angle = 2.0 * Math.PI * freq * progress * duration
                val value = if (sin(angle) >= 0) 0.10f else -0.10f
                samples[i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            samples
        }
    }

    fun playHurt() {
        playSound { sampleRate ->
            val duration = 0.15f
            val numSamples = (sampleRate * duration).toInt()
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val freq = 180f * (1.0f - progress * 0.8f)
                val angle = 2.0 * Math.PI * freq * progress * duration
                val noise = (Math.random() * 2.0 - 1.0) * 0.1
                val value = (if (sin(angle) >= 0) 0.1f else -0.1f) + noise
                samples[i] = (value * Short.MAX_VALUE * (1.0f - progress)).coerceIn(-32768.0, 32767.0).toInt().toShort()
            }
            samples
        }
    }

    fun playLevelUp() {
        playSound { sampleRate ->
            val duration = 0.5f
            val numSamples = (sampleRate * duration).toInt()
            val samples = ShortArray(numSamples)
            val notes = floatArrayOf(261.63f, 329.63f, 392.00f, 523.25f, 659.25f)
            val noteDuration = numSamples / notes.size
            for (i in 0 until numSamples) {
                val noteIdx = (i / noteDuration).coerceAtMost(notes.size - 1)
                val freq = notes[noteIdx]
                val progress = (i % noteDuration).toFloat() / noteDuration
                val angle = 2.0 * Math.PI * freq * progress * (duration / notes.size)
                val value = if (sin(angle) >= 0) 0.12f else -0.12f
                samples[i] = (value * Short.MAX_VALUE * (1.0f - i.toFloat() / numSamples)).toInt().toShort()
            }
            samples
        }
    }

    fun playSlash() {
        playSound { sampleRate ->
            val duration = 0.08f
            val numSamples = (sampleRate * duration).toInt()
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val r = Math.random() * 2.0 - 1.0
                val volume = 0.12f * (1.0f - progress)
                samples[i] = (r * volume * Short.MAX_VALUE).toInt().toShort()
            }
            samples
        }
    }

    fun playEvolve() {
        playSound { sampleRate ->
            val duration = 0.7f
            val numSamples = (sampleRate * duration).toInt()
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val vibrato = 12.0 * sin(2.0 * Math.PI * 14.0 * progress)
                val baseFreq = 400f + progress * 550f + vibrato
                val angle = 2.0 * Math.PI * baseFreq * progress * duration
                val triangleVal = Math.abs((angle % (2 * Math.PI)) / Math.PI - 1.0) * 2.0 - 1.0
                val value = triangleVal * 0.15f
                samples[i] = (value * Short.MAX_VALUE * (1.0f - progress * 0.2f)).toInt().toShort()
            }
            samples
        }
    }

    fun playBossSpawn() {
        playSound { sampleRate ->
            val duration = 1.0f
            val numSamples = (sampleRate * duration).toInt()
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val freq = 75f - progress * 15f
                val angle = 2.0 * Math.PI * freq * progress * duration
                val r = Math.random() * 2.0 - 1.0
                val base = if (sin(angle) >= 0) 0.16f else -0.16f
                val value = base + r * 0.06f
                samples[i] = (value * Short.MAX_VALUE * (1.0f - progress)).coerceIn(-32768.0, 32767.0).toInt().toShort()
            }
            samples
        }
    }

    private fun playSound(generator: (sampleRate: Int) -> ShortArray) {
        scope.launch {
            try {
                val sampleRate = 22050
                val samples = generator(sampleRate)
                val bufferSize = samples.size * 2
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                val durationMs = (samples.size.toFloat() / sampleRate * 1000).toLong()
                kotlinx.coroutines.delay(durationMs + 100)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e("GameAudioPlayer", "Error playing synthesizer sound: ${e.message}")
            }
        }
    }
}
