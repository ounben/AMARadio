package com.ounben.amaradio.audio

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
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
    val isSupported: Boolean = true
)

/**
 * Manages both Android's standard OS Equalizer integration and a compact built-in 5-band
 * Equalizer fallback (via [android.media.audiofx.Equalizer]) for devices without a system EQ app.
 *
 * When disabled, the native [Equalizer] instance is released immediately so it never interferes
 * with an external / system OS equalizer.
 */
class InAppEqualizerManager(context: Context) {

    companion object {
        private const val TAG = "InAppEqualizerManager"
        const val PREF_EQ_ENABLED = "in_app_eq_enabled"
        const val PREF_EQ_PRESET = "in_app_eq_preset"
        const val PREF_EQ_BANDS = "in_app_eq_bands"

        private val DEFAULT_CENTER_FREQS_HZ = listOf(60, 230, 910, 3600, 14000)
        private const val DEFAULT_MIN_MB: Short = -1500
        private const val DEFAULT_MAX_MB: Short = 1500

        // Fallback presets used when editing EQ while no playback session is active yet.
        private val FALLBACK_PRESETS: List<Pair<String, List<Short>>> = listOf(
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Bass Boost" to listOf(500, 300, 0, 0, 100),
            "Vocal" to listOf(-200, 100, 400, 300, -100),
            "Pop" to listOf(-100, 200, 400, 200, -100),
            "Rock" to listOf(400, 200, -100, 300, 400),
            "Jazz" to listOf(300, 100, -100, 200, 300),
            "Classical" to listOf(400, 200, -100, 200, 300)
        )

        fun buildSystemEqualizerIntent(context: Context, audioSessionId: Int): Intent {
            return Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                if (audioSessionId > 0) {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                }
            }
        }

        fun hasSystemEqualizer(context: Context, audioSessionId: Int = 0): Boolean {
            return try {
                val intent = buildSystemEqualizerIntent(context, audioSessionId)
                intent.resolveActivity(context.packageManager) != null
            } catch (_: Exception) {
                false
            }
        }

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
    private var dialogOpen: Boolean = false

    private val _uiState = MutableStateFlow(loadFallbackState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    val isEnabled: Boolean
        get() = sharedPref.getBoolean(PREF_EQ_ENABLED, false)

    /**
     * Called by PlayerService when playback enters Playing with a valid audioSessionId.
     * Only attaches a hardware Equalizer if the user enabled the in-app equalizer (or the EQ dialog is open).
     */
    fun onPlaybackSessionActive(audioSessionId: Int) {
        synchronized(lock) {
            if (audioSessionId <= 0) return
            if (isEnabled || dialogOpen) {
                ensureAttachedLocked(audioSessionId)
            } else if (attachedSessionId != 0) {
                releaseLocked()
            }
        }
    }

    /**
     * Called when playback stops or PlayerService is destroyed.
     */
    fun releaseSession() {
        synchronized(lock) {
            releaseLocked()
            _uiState.value = loadFallbackState()
        }
    }

    /**
     * Called when the In-App Equalizer dialog opens so real device bands & presets are shown if playing.
     */
    fun onDialogOpened(audioSessionId: Int) {
        synchronized(lock) {
            dialogOpen = true
            if (audioSessionId > 0) {
                ensureAttachedLocked(audioSessionId)
            } else {
                _uiState.value = loadFallbackState()
            }
        }
    }

    /**
     * Called when the In-App Equalizer dialog closes. If disabled, releases native Equalizer so
     * it never conflicts with an OS equalizer.
     */
    fun onDialogClosed() {
        synchronized(lock) {
            dialogOpen = false
            if (!isEnabled) {
                releaseLocked()
            }
        }
    }

    fun setEnabled(enabled: Boolean, audioSessionId: Int) {
        synchronized(lock) {
            sharedPref.edit { putBoolean(PREF_EQ_ENABLED, enabled) }
            if (enabled) {
                if (audioSessionId > 0) {
                    ensureAttachedLocked(audioSessionId)
                } else {
                    _uiState.value = _uiState.value.copy(enabled = true)
                }
            } else {
                try {
                    equalizer?.enabled = false
                } catch (_: Exception) {}
                if (!dialogOpen) {
                    releaseLocked()
                }
                _uiState.value = _uiState.value.copy(enabled = false)
            }
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Short, audioSessionId: Int) {
        synchronized(lock) {
            val eq = if (audioSessionId > 0) ensureAttachedLocked(audioSessionId) else equalizer
            val currentBands = _uiState.value.bands.toMutableList()
            if (bandIndex !in currentBands.indices) return

            val band = currentBands[bandIndex]
            val clamped = levelMb.coerceIn(band.minMb, band.maxMb)
            currentBands[bandIndex] = band.copy(levelMb = clamped)

            if (eq != null) {
                try {
                    eq.setBandLevel(bandIndex.toShort(), clamped)
                } catch (e: Exception) {
                    Log.w(TAG, "setBandLevel failed for band $bandIndex", e)
                }
            }

            saveBandsLocked(currentBands.map { it.levelMb }, presetIndex = -1)
            _uiState.value = _uiState.value.copy(
                presetIndex = -1,
                bands = currentBands
            )
        }
    }

    fun selectPreset(presetIndex: Int, audioSessionId: Int) {
        synchronized(lock) {
            val eq = if (audioSessionId > 0) ensureAttachedLocked(audioSessionId) else equalizer
            if (eq != null) {
                try {
                    val count = eq.numberOfPresets.toInt()
                    if (presetIndex in 0 until count) {
                        eq.usePreset(presetIndex.toShort())
                        val updatedBands = readBandsFromHardwareLocked(eq)
                        saveBandsLocked(updatedBands.map { it.levelMb }, presetIndex = presetIndex)
                        _uiState.value = _uiState.value.copy(
                            presetIndex = presetIndex,
                            bands = updatedBands
                        )
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "selectPreset failed for index $presetIndex", e)
                }
            }

            // Offline / idle fallback preset selection
            if (presetIndex in FALLBACK_PRESETS.indices) {
                val levels = FALLBACK_PRESETS[presetIndex].second
                val updatedBands = _uiState.value.bands.mapIndexed { idx, band ->
                    val lvl = levels.getOrElse(idx) { 0 }.coerceIn(band.minMb, band.maxMb)
                    band.copy(levelMb = lvl)
                }
                saveBandsLocked(updatedBands.map { it.levelMb }, presetIndex = presetIndex)
                _uiState.value = _uiState.value.copy(
                    presetIndex = presetIndex,
                    bands = updatedBands
                )
            }
        }
    }

    fun reset(audioSessionId: Int) {
        synchronized(lock) {
            val eq = if (audioSessionId > 0) ensureAttachedLocked(audioSessionId) else equalizer
            val resetBands = _uiState.value.bands.map { it.copy(levelMb = 0) }
            if (eq != null) {
                resetBands.forEach { band ->
                    try {
                        eq.setBandLevel(band.index.toShort(), 0)
                    } catch (_: Exception) {}
                }
            }
            saveBandsLocked(resetBands.map { it.levelMb }, presetIndex = -1)
            _uiState.value = _uiState.value.copy(
                presetIndex = -1,
                bands = resetBands
            )
        }
    }

    private fun ensureAttachedLocked(audioSessionId: Int): Equalizer? {
        if (audioSessionId <= 0) return null
        if (equalizer != null && attachedSessionId == audioSessionId) {
            applyStoredSettingsLocked(equalizer!!)
            refreshStateFromHardwareLocked(equalizer!!)
            return equalizer
        }

        releaseLocked()

        return try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            attachedSessionId = audioSessionId
            applyStoredSettingsLocked(eq)
            refreshStateFromHardwareLocked(eq)
            eq
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Android Equalizer for session $audioSessionId", e)
            equalizer = null
            attachedSessionId = 0
            _uiState.value = loadFallbackState().copy(isSupported = false)
            null
        }
    }

    private fun applyStoredSettingsLocked(eq: Equalizer) {
        val enabled = isEnabled
        val savedPreset = sharedPref.getInt(PREF_EQ_PRESET, -1)
        val savedBands = loadSavedBandLevels()
        val bandCount = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val minMb = range.getOrElse(0) { DEFAULT_MIN_MB }
        val maxMb = range.getOrElse(1) { DEFAULT_MAX_MB }

        try {
            if (savedPreset in 0 until eq.numberOfPresets.toInt()) {
                eq.usePreset(savedPreset.toShort())
            } else if (savedBands.isNotEmpty()) {
                for (i in 0 until bandCount) {
                    val lvl = savedBands.getOrElse(i) { 0 }.coerceIn(minMb, maxMb)
                    eq.setBandLevel(i.toShort(), lvl)
                }
            }
            eq.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply stored EQ settings", e)
        }
    }

    private fun refreshStateFromHardwareLocked(eq: Equalizer) {
        try {
            val bands = readBandsFromHardwareLocked(eq)
            val presetCount = eq.numberOfPresets.toInt()
            val presetNames = (0 until presetCount).map { idx ->
                eq.getPresetName(idx.toShort()) ?: "Preset ${idx + 1}"
            }
            val savedPreset = sharedPref.getInt(PREF_EQ_PRESET, -1)
            val validPreset = if (savedPreset in 0 until presetCount) savedPreset else -1

            _uiState.value = EqualizerUiState(
                enabled = isEnabled,
                presetIndex = validPreset,
                presetNames = presetNames,
                bands = bands,
                isAttachedToSession = true,
                isSupported = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EQ state from hardware", e)
        }
    }

    private fun readBandsFromHardwareLocked(eq: Equalizer): List<EqualizerBandInfo> {
        val bandCount = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val minMb = range.getOrElse(0) { DEFAULT_MIN_MB }
        val maxMb = range.getOrElse(1) { DEFAULT_MAX_MB }
        return (0 until bandCount).map { idx ->
            val centerHz = (eq.getCenterFreq(idx.toShort()) / 1000).coerceAtLeast(1)
            val levelMb = eq.getBandLevel(idx.toShort())
            EqualizerBandInfo(
                index = idx,
                centerFreqHz = centerHz,
                levelMb = levelMb,
                minMb = minMb,
                maxMb = maxMb
            )
        }
    }

    private fun releaseLocked() {
        try {
            equalizer?.enabled = false
        } catch (_: Exception) {}
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        attachedSessionId = 0
    }

    private fun loadFallbackState(): EqualizerUiState {
        val enabled = isEnabled
        val savedPreset = sharedPref.getInt(PREF_EQ_PRESET, -1)
        val savedBands = loadSavedBandLevels()
        val bands = DEFAULT_CENTER_FREQS_HZ.mapIndexed { idx, freq ->
            val lvl = savedBands.getOrElse(idx) { 0 }.coerceIn(DEFAULT_MIN_MB, DEFAULT_MAX_MB)
            EqualizerBandInfo(
                index = idx,
                centerFreqHz = freq,
                levelMb = lvl,
                minMb = DEFAULT_MIN_MB,
                maxMb = DEFAULT_MAX_MB
            )
        }
        val validPreset = if (savedPreset in FALLBACK_PRESETS.indices) savedPreset else -1
        return EqualizerUiState(
            enabled = enabled,
            presetIndex = validPreset,
            presetNames = FALLBACK_PRESETS.map { it.first },
            bands = bands,
            isAttachedToSession = false,
            isSupported = true
        )
    }

    private fun loadSavedBandLevels(): List<Short> {
        val raw = sharedPref.getString(PREF_EQ_BANDS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toShortOrNull() }
    }

    private fun saveBandsLocked(levels: List<Short>, presetIndex: Int) {
        sharedPref.edit {
            putInt(PREF_EQ_PRESET, presetIndex)
            putString(PREF_EQ_BANDS, levels.joinToString(","))
        }
    }
}
