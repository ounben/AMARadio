package com.ounben.amaradio.players.exoplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.ounben.amaradio.audio.InAppEqualizerManager
import com.ounben.amaradio.audio.StationAudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
class VolumeNormalizationAudioProcessorTest {

    private val sampleRate = 48_000
    private val channels = 2
    private val format = AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT)

    private fun createConfiguredProcessor(
        onLearned: ((String, Float) -> Unit)? = null
    ): VolumeNormalizationAudioProcessor {
        val processor = VolumeNormalizationAudioProcessor(onLearned)
        processor.configure(format)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        return processor
    }

    private fun generateSineBlock(
        amplitude: Float,
        durationMs: Int = 20,
        freqHz: Float = 1000f
    ): ByteBuffer {
        val frameCount = (sampleRate * durationMs) / 1000
        val buffer = ByteBuffer.allocate(frameCount * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val phase = 2.0 * PI * freqHz * i / sampleRate
            val sample = (sin(phase) * amplitude * 32767.0).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            repeat(channels) {
                buffer.putShort(sample)
            }
        }
        buffer.flip()
        return buffer
    }

    private fun measureRmsDb(buffer: ByteBuffer): Float {
        val copy = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        var count = 0
        while (copy.remaining() >= 2) {
            val s = copy.short.toFloat() / 32768f
            sumSq += (s * s).toDouble()
            count++
        }
        if (count == 0) return -120f
        val rms = sqrt(sumSq / count).toFloat()
        return if (rms > 1e-6f) 20f * log10(rms) else -120f
    }

    private fun measurePeakAbs(buffer: ByteBuffer): Int {
        val copy = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var peak = 0
        while (copy.remaining() >= 2) {
            val v = abs(copy.short.toInt())
            if (v > peak) peak = v
        }
        return peak
    }

    @Test
    fun passthrough_whenDisabledAndManualGainZero_preservesSamplesExactly() {
        val processor = createConfiguredProcessor()
        processor.setAutoNormalizationEnabled(false)
        processor.setManualGainDb(0f)

        val input = generateSineBlock(amplitude = 0.5f)
        val expected = input.duplicate()
        processor.queueInput(input)
        val output = processor.output

        assertEquals(expected.remaining(), output.remaining())
        while (expected.hasRemaining()) {
            assertEquals(expected.get(), output.get())
        }
    }

    @Test
    fun manualGain_worksWhenAutoNormalizationDisabled() {
        val processor = createConfiguredProcessor()
        processor.setAutoNormalizationEnabled(false)
        processor.setManualGainDb(-6.0f)

        var lastOut: ByteBuffer = ByteBuffer.allocate(0)
        // Run ~100ms so the 12ms gain ramp settles completely
        repeat(5) {
            processor.queueInput(generateSineBlock(amplitude = 0.5f, durationMs = 20))
            lastOut = processor.output
        }

        val inputDb = measureRmsDb(generateSineBlock(amplitude = 0.5f, durationMs = 20))
        val outputDb = measureRmsDb(lastOut)
        assertEquals(inputDb - 6.0f, outputDb, 0.5f)
    }

    @Test
    fun autoNormalization_attenuatesLoudSignalQuicklyTowardsTarget() {
        val processor = createConfiguredProcessor()
        processor.setAutoNormalizationEnabled(true)
        processor.setStation("loud-station", manualGainDb = 0f, learnedGainDb = null)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        // Loud 1kHz sine: amplitude 0.9 -> RMS ~ -3.9 dBFS (well above -16 dBFS target)
        var lastOut: ByteBuffer = ByteBuffer.allocate(0)
        // 1.5 seconds (75 x 20ms) — fast attack (0.35s) should bring it close to -16 dBFS
        repeat(75) {
            processor.queueInput(generateSineBlock(amplitude = 0.9f, durationMs = 20))
            lastOut = processor.output
        }

        val outputDb = measureRmsDb(lastOut)
        assertTrue(
            "Expected loud signal to be attenuated near -16 dBFS, got $outputDb dBFS",
            outputDb in -17.5f..-14.5f
        )
    }

    @Test
    fun autoNormalization_boostsQuietSignalAndRespectsSilenceGate() {
        val processor = createConfiguredProcessor()
        processor.setAutoNormalizationEnabled(true)
        processor.setStation("quiet-station", manualGainDb = 0f, learnedGainDb = null)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        // 1. Feed near-silence (-60 dBFS) for 2 seconds -> autoGain must remain ~0 dB
        repeat(100) {
            processor.queueInput(generateSineBlock(amplitude = 0.001f, durationMs = 20))
            processor.output
        }
        assertEquals(0f, processor.getCurrentAutoGainDb(), 0.05f)

        // 2. Feed quiet signal (amplitude 0.05 -> RMS ~ -29 dBFS) for 6 seconds -> should boost smoothly
        var lastOut: ByteBuffer = ByteBuffer.allocate(0)
        repeat(300) {
            processor.queueInput(generateSineBlock(amplitude = 0.05f, durationMs = 20))
            lastOut = processor.output
        }
        assertTrue(
            "Expected positive auto gain for quiet signal, got ${processor.getCurrentAutoGainDb()} dB",
            processor.getCurrentAutoGainDb() > 6.0f
        )
        val outputDb = measureRmsDb(lastOut)
        assertTrue(
            "Expected boosted signal near target, got $outputDb dBFS",
            outputDb > -21.0f
        )
    }

    @Test
    fun combinedAutoAndManualGain_isAdditiveAndSoftLimiterPreventsClipping() {
        // 1. Verify soft limiter prevents clipping even with +12 dB boost on near-full-scale audio
        val processor = createConfiguredProcessor()
        processor.setAutoNormalizationEnabled(false)
        processor.setManualGainDb(12.0f)

        var lastOut: ByteBuffer = ByteBuffer.allocate(0)
        repeat(5) {
            processor.queueInput(generateSineBlock(amplitude = 0.95f, durationMs = 20))
            lastOut = processor.output
        }

        val peak = measurePeakAbs(lastOut)
        assertTrue("Peak $peak must stay within true-peak ceiling (< Short.MAX_VALUE)", peak <= 32_500)
        assertTrue("Peak $peak should stay high near ceiling", peak > 28_000)

        // Verify softLimit function directly for extreme overdrive
        assertTrue(VolumeNormalizationAudioProcessor.softLimit(10.0f) <= 0.99f)
        assertTrue(VolumeNormalizationAudioProcessor.softLimit(-10.0f) >= -0.99f)
        assertEquals(0.5f, VolumeNormalizationAudioProcessor.softLimit(0.5f), 1e-6f)

        // 2. Verify Auto + Manual offset is additive (auto targets -16 dBFS, manual +3 dB -> ~ -13 dBFS)
        processor.setAutoNormalizationEnabled(true)
        processor.setStation("additive-station", manualGainDb = 3.0f, learnedGainDb = null)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        repeat(75) {
            processor.queueInput(generateSineBlock(amplitude = 0.9f, durationMs = 20))
            lastOut = processor.output
        }
        val combinedDb = measureRmsDb(lastOut)
        assertTrue(
            "Expected auto (-16 dBFS) + manual (+3 dB) ≈ -13 dBFS, got $combinedDb dBFS",
            combinedDb in -14.5f..-11.5f
        )
    }

    @Test
    fun learnedGain_initializesImmediatelyOnStationSwitchAndReportsAfterConvergence() {
        var reportedUuid: String? = null
        var reportedGain: Float? = null
        val processor = createConfiguredProcessor { uuid, gain ->
            reportedUuid = uuid
            reportedGain = gain
        }
        processor.setAutoNormalizationEnabled(true)

        // Switch to a station with a previously learned gain of -9.5 dB
        processor.setStation("somafm-poptron", manualGainDb = 0f, learnedGainDb = -9.5f)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(-9.5f, processor.getCurrentAutoGainDb(), 0.01f)

        // Feed 5 seconds of loud audio and switch away -> callback should persist learned gain
        repeat(250) {
            processor.queueInput(generateSineBlock(amplitude = 0.9f, durationMs = 20))
            processor.output
        }
        processor.setStation("next-station", manualGainDb = 0f, learnedGainDb = null)

        assertEquals("somafm-poptron", reportedUuid)
        assertTrue("Expected negative learned gain for loud station, got $reportedGain", (reportedGain ?: 0f) < -8.0f)
    }

    @Test
    fun helperFormattingAndSnapping() {
        assertEquals(0f, StationAudioManager.normalizeGainStep(0.1f), 0.001f)
        assertEquals(2.5f, StationAudioManager.normalizeGainStep(2.4f), 0.001f)
        assertEquals(12f, StationAudioManager.normalizeGainStep(20f), 0.001f)
        assertEquals(-12f, StationAudioManager.normalizeGainStep(-20f), 0.001f)

        assertEquals("+3.5 dB", StationAudioManager.formatGainDb(3.5f))
        assertEquals("-4.0 dB", StationAudioManager.formatGainDb(-4.0f))
        assertEquals("0.0 dB", StationAudioManager.formatGainDb(0f))

        assertEquals("60", InAppEqualizerManager.formatFrequency(60))
        assertEquals("3.6k", InAppEqualizerManager.formatFrequency(3600))
        assertEquals("14k", InAppEqualizerManager.formatFrequency(14000))
    }
}
