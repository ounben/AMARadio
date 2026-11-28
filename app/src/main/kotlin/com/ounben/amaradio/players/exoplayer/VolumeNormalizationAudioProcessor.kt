package com.ounben.amaradio.players.exoplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time loudness normalizer + per-station manual gain + soft peak limiter for Media3 ExoPlayer.
 *
 * - Runs on PCM 16-bit audio before AudioTrack (never touches raw stream recordings).
 * - Combines optional automatic loudness normalization (-16 dBFS RMS target, fast attack / slow release,
 *   silence gate) with a per-station manual gain offset (-12 dB .. +12 dB).
 * - Manual per-station gain works both as an offset on top of auto-normalization and standalone when
 *   auto-normalization is disabled.
 * - Ends with a smooth tanh soft-knee limiter (-1 dBFS threshold) so peaks never clip harshly.
 */
@UnstableApi
class VolumeNormalizationAudioProcessor(
    private val onLearnedGainUpdated: ((stationUuid: String, learnedGainDb: Float) -> Unit)? = null
) : BaseAudioProcessor() {

    companion object {
        const val TARGET_RMS_DB = -16.0f
        const val SILENCE_GATE_DB = -45.0f
        const val MIN_AUTO_GAIN_DB = -15.0f
        const val MAX_AUTO_GAIN_DB = 12.0f
        const val MIN_MANUAL_GAIN_DB = -12.0f
        const val MAX_MANUAL_GAIN_DB = 12.0f
        const val MIN_TOTAL_GAIN_DB = -24.0f
        const val MAX_TOTAL_GAIN_DB = 18.0f

        // Asymmetric envelope time constants (seconds) to tame sudden blasts fast without pumping.
        private const val ATTACK_TAU_SEC = 0.35f
        private const val RELEASE_TAU_SEC = 3.50f

        // Per-sample linear gain smoothing (~12 ms) to avoid clicks/zipper noise during live changes.
        private const val GAIN_RAMP_TAU_SEC = 0.012f

        // Soft limiter starts compressing gently at -1 dBFS (~0.891) and asymptotically approaches
        // 0.99 (-0.09 dBFS) to leave inter-sample true-peak headroom for DAC / Bluetooth resamplers.
        private const val LIMITER_THRESHOLD = 0.8912509f
        private const val LIMITER_CEILING = 0.99f
        private const val LIMITER_HEADROOM = LIMITER_CEILING - LIMITER_THRESHOLD

        // Minimum active (non-silent) audio duration before persisting a station's learned auto-gain.
        private const val MIN_LEARN_SECONDS = 4.0f
        private const val LEARN_REPORT_INTERVAL_SEC = 10.0f
        private const val LEARN_REPORT_DELTA_DB = 0.5f

        private const val PCM16_SCALE = 32768.0f
        private const val PCM16_MAX = 32767.0f
        private const val PCM16_MIN = -32768.0f

        internal fun dbToLinear(db: Float): Float = 10.0.pow((db / 20.0)).toFloat()

        internal fun softLimit(sample: Float): Float {
            val absVal = abs(sample)
            if (absVal <= LIMITER_THRESHOLD) return sample
            val excess = (absVal - LIMITER_THRESHOLD) / LIMITER_HEADROOM
            val compressed = LIMITER_THRESHOLD + LIMITER_HEADROOM * tanh(excess.toDouble()).toFloat()
            return if (sample < 0f) -compressed else compressed
        }
    }

    @Volatile
    private var autoNormalizationEnabled: Boolean = false

    @Volatile
    private var manualGainDb: Float = 0f

    @Volatile
    private var activeStationUuid: String? = null

    @Volatile
    private var pendingAutoGainReset: Float? = null

    // Audio-thread DSP state
    private var autoGainDb: Float = 0f
    private var currentLinearGain: Float = 1.0f
    private var hpAlpha: Float = 0.985f
    private var rampCoeff: Float = 0.002f
    private var hpPrevIn: FloatArray = FloatArray(0)
    private var hpPrevOut: FloatArray = FloatArray(0)

    // Learned gain reporting state (audio thread)
    private var activeAudioSeconds: Float = 0f
    private var secondsSinceLastReport: Float = 0f
    private var lastReportedGainDb: Float? = null
    private var lastReportedStationUuid: String? = null

    fun setAutoNormalizationEnabled(enabled: Boolean) {
        autoNormalizationEnabled = enabled
    }

    fun isAutoNormalizationEnabled(): Boolean = autoNormalizationEnabled

    fun setManualGainDb(gainDb: Float) {
        manualGainDb = gainDb.coerceIn(MIN_MANUAL_GAIN_DB, MAX_MANUAL_GAIN_DB)
    }

    fun getManualGainDb(): Float = manualGainDb

    fun getCurrentAutoGainDb(): Float = autoGainDb

    /**
     * Switches the processor to a new station, applying its saved manual gain and
     * initializing the automatic normalizer from any previously learned gain so channel
     * transitions start smoothly without an initial loud blast.
     */
    fun setStation(stationUuid: String?, manualGainDb: Float, learnedGainDb: Float?) {
        // Persist any converged auto-gain from the previous station before switching
        reportLearnedGainIfReady(force = true)

        activeStationUuid = stationUuid
        this.manualGainDb = manualGainDb.coerceIn(MIN_MANUAL_GAIN_DB, MAX_MANUAL_GAIN_DB)
        pendingAutoGainReset = (learnedGainDb ?: 0f).coerceIn(MIN_AUTO_GAIN_DB, MAX_AUTO_GAIN_DB)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount <= 0 ||
            inputAudioFormat.sampleRate <= 0
        ) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)

        // 1st-order high-pass filter at ~100 Hz (RC = 1 / (2*pi*100))
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * 100.0f)
        val dt = 1.0f / sampleRate.toFloat()
        hpAlpha = rc / (rc + dt)
        rampCoeff = 1.0f - exp((-dt / GAIN_RAMP_TAU_SEC).toDouble()).toFloat()

        hpPrevIn = FloatArray(channels)
        hpPrevOut = FloatArray(channels)

        applyPendingStationResetIfAny()
        val initialTotalDb = computeTotalGainDb(autoNormalizationEnabled, autoGainDb, manualGainDb)
        currentLinearGain = dbToLinear(initialTotalDb)
    }

    override fun onReset() {
        reportLearnedGainIfReady(force = true)
        hpPrevIn = FloatArray(0)
        hpPrevOut = FloatArray(0)
        currentLinearGain = 1.0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        applyPendingStationResetIfAny()

        val channels = inputAudioFormat.channelCount
        val sampleRate = inputAudioFormat.sampleRate
        val bytesPerFrame = channels * 2
        val frameCount = remaining / bytesPerFrame
        if (frameCount <= 0 || channels <= 0 || sampleRate <= 0) {
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val autoOn = autoNormalizationEnabled
        val manualDb = manualGainDb

        // Fast zero-processing pass-through when disabled, neutral manual gain, and ramp settled.
        if (!autoOn && abs(manualDb) < 0.01f && abs(currentLinearGain - 1.0f) < 0.0005f) {
            currentLinearGain = 1.0f
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        if (hpPrevIn.size != channels) {
            hpPrevIn = FloatArray(channels)
            hpPrevOut = FloatArray(channels)
        }

        val startPosition = inputBuffer.position()
        val sampleCount = frameCount * channels

        // 1. Measure block loudness (high-pass filtered RMS) when auto-normalization is active.
        if (autoOn) {
            var sumSq = 0.0
            var byteIndex = startPosition
            val alpha = hpAlpha
            repeat(frameCount) {
                for (ch in 0 until channels) {
                    val sample = inputBuffer.getShort(byteIndex).toFloat() / PCM16_SCALE
                    byteIndex += 2
                    val hp = alpha * (hpPrevOut[ch] + sample - hpPrevIn[ch])
                    hpPrevIn[ch] = sample
                    hpPrevOut[ch] = hp
                    sumSq += (hp * hp).toDouble()
                }
            }

            val rms = sqrt(sumSq / sampleCount.toDouble()).toFloat()
            val rmsDb = if (rms > 1e-6f) 20.0f * log10(rms) else -120.0f
            val blockDurationSec = frameCount.toFloat() / sampleRate.toFloat()

            if (rmsDb >= SILENCE_GATE_DB) {
                val desiredAutoGainDb = (TARGET_RMS_DB - rmsDb).coerceIn(MIN_AUTO_GAIN_DB, MAX_AUTO_GAIN_DB)
                val tau = if (desiredAutoGainDb < autoGainDb) ATTACK_TAU_SEC else RELEASE_TAU_SEC
                val envCoeff = 1.0f - exp((-blockDurationSec / tau).toDouble()).toFloat()
                autoGainDb += (desiredAutoGainDb - autoGainDb) * envCoeff
                autoGainDb = autoGainDb.coerceIn(MIN_AUTO_GAIN_DB, MAX_AUTO_GAIN_DB)

                activeAudioSeconds += blockDurationSec
                secondsSinceLastReport += blockDurationSec
                if (activeAudioSeconds >= MIN_LEARN_SECONDS && secondsSinceLastReport >= LEARN_REPORT_INTERVAL_SEC) {
                    reportLearnedGainIfReady(force = false)
                }
            }
        }

        // 2. Apply smoothed combined gain + soft limiter to output PCM 16-bit buffer.
        val totalGainDb = computeTotalGainDb(autoOn, autoGainDb, manualDb)
        val targetLinearGain = dbToLinear(totalGainDb)
        var gain = currentLinearGain
        val smooth = rampCoeff

        val out = replaceOutputBuffer(remaining)
        var byteIndex = startPosition
        repeat(frameCount) {
            gain += (targetLinearGain - gain) * smooth
            repeat(channels) {
                val raw = inputBuffer.getShort(byteIndex).toFloat() / PCM16_SCALE
                byteIndex += 2
                val limited = softLimit(raw * gain)
                val pcm16 = (limited * PCM16_SCALE)
                    .roundToInt()
                    .coerceIn(PCM16_MIN.toInt(), PCM16_MAX.toInt())
                    .toShort()
                out.putShort(pcm16)
            }
        }

        // Copy any trailing partial-frame bytes verbatim (defensive).
        val processedBytes = frameCount * bytesPerFrame
        if (processedBytes < remaining) {
            inputBuffer.position(startPosition + processedBytes)
            out.put(inputBuffer)
        } else {
            inputBuffer.position(startPosition + remaining)
        }

        currentLinearGain = gain
        out.flip()
    }

    private fun computeTotalGainDb(autoOn: Boolean, currentAutoDb: Float, currentManualDb: Float): Float {
        val combined = (if (autoOn) currentAutoDb else 0f) + currentManualDb
        return combined.coerceIn(MIN_TOTAL_GAIN_DB, MAX_TOTAL_GAIN_DB)
    }

    private fun applyPendingStationResetIfAny() {
        val resetGain = pendingAutoGainReset
        if (resetGain != null) {
            pendingAutoGainReset = null
            autoGainDb = resetGain
            activeAudioSeconds = 0f
            secondsSinceLastReport = 0f
            lastReportedGainDb = resetGain
            lastReportedStationUuid = activeStationUuid
        }
    }

    private fun reportLearnedGainIfReady(force: Boolean) {
        val uuid = activeStationUuid ?: return
        if (!autoNormalizationEnabled) return
        if (activeAudioSeconds < MIN_LEARN_SECONDS) return

        val currentRounded = (autoGainDb * 2f).roundToInt() / 2f
        val prev = if (lastReportedStationUuid == uuid) lastReportedGainDb else null
        if (force || prev == null || abs(currentRounded - prev) >= LEARN_REPORT_DELTA_DB) {
            lastReportedGainDb = currentRounded
            lastReportedStationUuid = uuid
            secondsSinceLastReport = 0f
            onLearnedGainUpdated?.invoke(uuid, currentRounded)
        }
    }
}
