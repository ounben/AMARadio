package com.ounben.amaradio.audio

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Short,
    val minMb: Short,
    val maxMb: Short
)

data class EqualizerUiState(
    val enabled: Boolean = false,
    val presetIndex: Int = -1, // -1 = Custom
    val presetNames: List<String> = emptyList(),
    val bands: List<EqualizerBandInfo> = emptyList(),
    val isAttachedToSession: Boolean = false,
    val isSupported: Boolean = true,
    val editingStationUuid: String? = null
)

/**
 * Built-in crash-safe 5-band Equalizer (via [android.media.audiofx.Equalizer]) supporting both:
 * - Global equalizer settings (persisted in SharedPreferences)
 * - Optional per-station equalizer overrides (persisted in Room via [StationAudioManager])
 *
 * Whenever a station has a custom EQ profile enabled, it overrides the global EQ while that
 * station is playing. Switching to a station without an override seamlessly restores the global EQ.
 * When neither global nor station EQ is enabled, the native [Equalizer] is released.
 */
class InAppEqualizerManager(
    context: Context,
    private val stationAudioManager: StationAudioManager
) {

    companion object {
        private const val TAG = "InAppEqualizerManager"
        const val PREF_EQ_ENABLED = "in_app_eq_enabled"
        const val PREF_EQ_PRESET = "in_app_eq_preset"
        const val PREF_EQ_BANDS = "in_app_eq_bands"

        private val DEFAULT_CENTER_FREQS_HZ = listOf(60, 230, 910, 3600, 14000)
        private const val DEFAULT_MIN_MB: Short = -1500
        private const val DEFAULT_MAX_MB: Short = 1500

        private val FALLBACK_PRESETS: List<Pair<String, List<Short>>> = listOf(
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Bass Boost" to listOf(500, 300, 0, 0, 100),
            "Vocal" to listOf(-200, 100, 400, 300, -100),
            "Pop" to listOf(-100, 200, 400, 200, -100),
            "Rock" to listOf(400, 200, -100, 300, 400),
            "Jazz" to listOf(300, 100, -100, 200, 300),
            "Classical" to listOf(400, 200, -100, 200, 300)
        )

        fun formatFrequency(freqHz: Int): String {
            return if (freqHz >= 1000) {
                val khz = freqHz / 1000.0
                if (freqHz % 1000 == 0) "${freqHz / 1000}k"
                else String.format(java.util.Locale.US, "%.1fk", khz)
            } else {
                "$freqHz"
            }
        }
    }

    private val appContext = context.applicationContext
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val lock = Any()

    private var equalizer: Equalizer? = null
    private var attachedSessionId: Int = 0
    private var activeStationUuid: String? = null
    private var editingStationUuid: String? = null
    private var dialogOpen: Boolean = false
    private var hardwareSupported: Boolean = true

    // Cached device hardware capabilities (populated once an Equalizer attaches to any session)
    private var cachedCenterFreqsHz: List<Int> = DEFAULT_CENTER_FREQS_HZ
    private var cachedMinMb: Short = DEFAULT_MIN_MB
    private var cachedMaxMb: Short = DEFAULT_MAX_MB
    private var cachedPresetNames: List<String> = FALLBACK_PRESETS.map { it.first }
    private var hasRealHardwareMetadata: Boolean = false

    private val _uiState = MutableStateFlow(buildUiStateLocked(null))
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    val isGlobalEnabled: Boolean
        get() = sharedPref.getBoolean(PREF_EQ_ENABLED, false)

    fun getPresetDisplayName(presetIndex: Int): String? {
        synchronized(lock) {
            return cachedPresetNames.getOrNull(presetIndex)
        }
    }

    /**
     * Called by PlayerService when playback enters Playing with a valid audioSessionId.
     */
    fun onPlaybackSessionActive(audioSessionId: Int, stationUuid: String?) {
        synchronized(lock) {
            if (!stationUuid.isNullOrBlank()) {
                activeStationUuid = stationUuid
            }
            if (audioSessionId <= 0) return

            val needsHardware = isEffectiveEqEnabledLocked() || dialogOpen
            if (needsHardware) {
                ensureHardwareAttachedLocked(audioSessionId)
                applyEffectiveProfileLocked()
            } else if (attachedSessionId != 0) {
                releaseHardwareLocked()
            }
            refreshUiStateLocked()
        }
    }

    /**
     * Called on station transitions (including when ExoPlayer reuses the same audioSessionId).
     */
    fun onStationChanged(stationUuid: String?, audioSessionId: Int) {
        synchronized(lock) {
            activeStationUuid = stationUuid
            val sessionId = if (audioSessionId > 0) audioSessionId else attachedSessionId
            if (sessionId > 0) {
                val needsHardware = isEffectiveEqEnabledLocked() || dialogOpen
                if (needsHardware) {
                    ensureHardwareAttachedLocked(sessionId)
                    applyEffectiveProfileLocked()
                } else if (attachedSessionId != 0) {
                    releaseHardwareLocked()
                }
            }
            refreshUiStateLocked()
        }
    }

    /**
     * Called when playback stops or PlayerService is destroyed.
     */
    fun releaseSession() {
        synchronized(lock) {
            releaseHardwareLocked()
            refreshUiStateLocked()
        }
    }

    /**
     * Opens the Equalizer UI for either global settings ([stationUuid] == null)
     * or a specific station's override ([stationUuid] != null).
     */
    fun onDialogOpened(audioSessionId: Int, stationUuid: String? = null) {
        synchronized(lock) {
            dialogOpen = true
            editingStationUuid = stationUuid
            if (audioSessionId > 0) {
                ensureHardwareAttachedLocked(audioSessionId)
                applyEffectiveProfileLocked()
            }
            refreshUiStateLocked()
        }
    }

    /**
     * Closes the Equalizer UI and releases native resources if neither global nor active station EQ is enabled.
     */
    fun onDialogClosed() {
        synchronized(lock) {
            dialogOpen = false
            editingStationUuid = null
            if (!isEffectiveEqEnabledLocked()) {
                releaseHardwareLocked()
            }
        }
    }

    fun setEnabled(enabled: Boolean, audioSessionId: Int, stationUuid: String? = null) {
        synchronized(lock) {
            if (stationUuid == null) {
                sharedPref.edit { putBoolean(PREF_EQ_ENABLED, enabled) }
            } else {
                if (enabled) {
                    val existing = stationAudioManager.getStationEqProfile(stationUuid)
                    val initialProfile = existing ?: StationEqProfile(
                        presetIndex = sharedPref.getInt(PREF_EQ_PRESET, -1),
                        bands = loadGlobalBandLevelsLocked().ifEmpty { List(cachedCenterFreqsHz.size) { 0.toShort() } }
                    )
                    stationAudioManager.setStationEqProfile(stationUuid, initialProfile)
                } else {
                    stationAudioManager.setStationEqProfile(stationUuid, null)
                }
            }

            syncHardwareAfterEditLocked(audioSessionId)
            refreshUiStateLocked()
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Short, audioSessionId: Int, stationUuid: String? = null) {
        synchronized(lock) {
            val bandCount = cachedCenterFreqsHz.size
            if (bandIndex !in 0 until bandCount) return
            val clamped = levelMb.coerceIn(cachedMinMb, cachedMaxMb)

            val currentLevels = loadTargetBandLevelsLocked(stationUuid).toMutableList()
            while (currentLevels.size < bandCount) currentLevels.add(0)
            currentLevels[bandIndex] = clamped

            saveTargetProfileLocked(stationUuid, presetIndex = -1, bands = currentLevels)
            syncHardwareAfterEditLocked(audioSessionId)
            refreshUiStateLocked()
        }
    }

    fun selectPreset(presetIndex: Int, audioSessionId: Int, stationUuid: String? = null) {
        synchronized(lock) {
            if (presetIndex !in cachedPresetNames.indices) return

            val eq = if (audioSessionId > 0) ensureHardwareAttachedLocked(audioSessionId) else equalizer
            val resolvedLevels: List<Short> = if (eq != null && hasRealHardwareMetadata) {
                resolveHardwarePresetLevelsLocked(eq, presetIndex)
            } else {
                FALLBACK_PRESETS.getOrNull(presetIndex)?.second?.map {
                    it.coerceIn(cachedMinMb, cachedMaxMb)
                } ?: List(cachedCenterFreqsHz.size) { 0.toShort() }
            }

            saveTargetProfileLocked(stationUuid, presetIndex = presetIndex, bands = resolvedLevels)
            syncHardwareAfterEditLocked(audioSessionId)
            refreshUiStateLocked()
        }
    }

    fun reset(audioSessionId: Int, stationUuid: String? = null) {
        synchronized(lock) {
            val zeroBands = List(cachedCenterFreqsHz.size) { 0.toShort() }
            saveTargetProfileLocked(stationUuid, presetIndex = -1, bands = zeroBands)
            syncHardwareAfterEditLocked(audioSessionId)
            refreshUiStateLocked()
        }
    }

    private fun isEffectiveEqEnabledLocked(): Boolean {
        val stationOverride = stationAudioManager.getStationEqProfile(activeStationUuid)
        return stationOverride != null || isGlobalEnabled
    }

    private fun syncHardwareAfterEditLocked(audioSessionId: Int) {
        val sessionId = if (audioSessionId > 0) audioSessionId else attachedSessionId
        if (sessionId <= 0) return

        val needsHardware = isEffectiveEqEnabledLocked() || dialogOpen
        if (needsHardware) {
            ensureHardwareAttachedLocked(sessionId)
            applyEffectiveProfileLocked()
        } else {
            releaseHardwareLocked()
        }
    }

    private fun ensureHardwareAttachedLocked(audioSessionId: Int): Equalizer? {
        if (audioSessionId <= 0) return null
        if (equalizer != null && attachedSessionId == audioSessionId) {
            return equalizer
        }

        releaseHardwareLocked()

        return try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            attachedSessionId = audioSessionId
            hardwareSupported = true
            captureHardwareMetadataLocked(eq)
            eq
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Android Equalizer for session $audioSessionId", e)
            equalizer = null
            attachedSessionId = 0
            hardwareSupported = false
            null
        }
    }

    private fun captureHardwareMetadataLocked(eq: Equalizer) {
        try {
            val bandCount = eq.numberOfBands.toInt()
            if (bandCount > 0) {
                val range = eq.bandLevelRange
                cachedMinMb = range.getOrElse(0) { DEFAULT_MIN_MB }
                cachedMaxMb = range.getOrElse(1) { DEFAULT_MAX_MB }
                cachedCenterFreqsHz = (0 until bandCount).map { idx ->
                    (eq.getCenterFreq(idx.toShort()) / 1000).coerceAtLeast(1)
                }
                val presetCount = eq.numberOfPresets.toInt()
                if (presetCount > 0) {
                    cachedPresetNames = (0 until presetCount).map { idx ->
                        eq.getPresetName(idx.toShort()) ?: "Preset ${idx + 1}"
                    }
                }
                hasRealHardwareMetadata = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read hardware EQ metadata", e)
        }
    }

    /**
     * Reads band levels for [presetIndex] from hardware without leaving the wrong preset active
     * (caller immediately calls [applyEffectiveProfileLocked] right afterward).
     */
    private fun resolveHardwarePresetLevelsLocked(eq: Equalizer, presetIndex: Int): List<Short> {
        return try {
            val presetCount = eq.numberOfPresets.toInt()
            if (presetIndex in 0 until presetCount) {
                eq.usePreset(presetIndex.toShort())
                val bandCount = eq.numberOfBands.toInt()
                (0 until bandCount).map { idx -> eq.getBandLevel(idx.toShort()) }
            } else {
                List(cachedCenterFreqsHz.size) { 0.toShort() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve hardware preset $presetIndex", e)
            List(cachedCenterFreqsHz.size) { 0.toShort() }
        }
    }

    private fun applyEffectiveProfileLocked() {
        val eq = equalizer ?: return
        val stationOverride = stationAudioManager.getStationEqProfile(activeStationUuid)
        val effectiveEnabled = stationOverride != null || isGlobalEnabled
        val effectivePreset = stationOverride?.presetIndex ?: sharedPref.getInt(PREF_EQ_PRESET, -1)
        val effectiveBands = stationOverride?.bands ?: loadGlobalBandLevelsLocked()

        try {
            if (!effectiveEnabled) {
                eq.enabled = false
                return
            }

            val bandCount = eq.numberOfBands.toInt()
            val presetCount = eq.numberOfPresets.toInt()
            if (effectivePreset in 0 until presetCount) {
                eq.usePreset(effectivePreset.toShort())
            } else {
                for (i in 0 until bandCount) {
                    val lvl = effectiveBands.getOrElse(i) { 0.toShort() }.coerceIn(cachedMinMb, cachedMaxMb)
                    eq.setBandLevel(i.toShort(), lvl)
                }
            }
            eq.enabled = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply effective EQ profile", e)
        }
    }

    private fun loadGlobalBandLevelsLocked(): List<Short> {
        val raw = sharedPref.getString(PREF_EQ_BANDS, "") ?: ""
        return StationAudioManager.parseBands(raw)
    }

    private fun loadTargetBandLevelsLocked(stationUuid: String?): List<Short> {
        return if (stationUuid == null) {
            loadGlobalBandLevelsLocked()
        } else {
            stationAudioManager.getStationEqProfile(stationUuid)?.bands ?: emptyList()
        }
    }

    private fun saveTargetProfileLocked(stationUuid: String?, presetIndex: Int, bands: List<Short>) {
        if (stationUuid == null) {
            sharedPref.edit {
                putInt(PREF_EQ_PRESET, presetIndex)
                putString(PREF_EQ_BANDS, StationAudioManager.serializeBands(bands))
            }
        } else {
            stationAudioManager.setStationEqProfile(
                stationUuid,
                StationEqProfile(presetIndex = presetIndex, bands = bands)
            )
        }
    }

    private fun refreshUiStateLocked() {
        _uiState.value = buildUiStateLocked(editingStationUuid)
    }

    private fun buildUiStateLocked(targetStationUuid: String?): EqualizerUiState {
        val isEnabled: Boolean
        val presetIndex: Int
        val savedBands: List<Short>

        if (targetStationUuid == null) {
            isEnabled = isGlobalEnabled
            presetIndex = sharedPref.getInt(PREF_EQ_PRESET, -1)
            savedBands = loadGlobalBandLevelsLocked()
        } else {
            val override = stationAudioManager.getStationEqProfile(targetStationUuid)
            isEnabled = override != null
            presetIndex = override?.presetIndex ?: -1
            savedBands = override?.bands ?: emptyList()
        }

        val bands = cachedCenterFreqsHz.mapIndexed { idx, freq ->
            val lvl = savedBands.getOrElse(idx) { 0.toShort() }.coerceIn(cachedMinMb, cachedMaxMb)
            EqualizerBandInfo(
                index = idx,
                centerFreqHz = freq,
                levelMb = lvl,
                minMb = cachedMinMb,
                maxMb = cachedMaxMb
            )
        }
        val validPreset = if (presetIndex in cachedPresetNames.indices) presetIndex else -1

        return EqualizerUiState(
            enabled = isEnabled,
            presetIndex = validPreset,
            presetNames = cachedPresetNames,
            bands = bands,
            isAttachedToSession = equalizer != null && attachedSessionId > 0,
            isSupported = hardwareSupported,
            editingStationUuid = targetStationUuid
        )
    }

    private fun releaseHardwareLocked() {
        try {
            equalizer?.enabled = false
        } catch (_: Exception) {}
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        attachedSessionId = 0
    }
}
