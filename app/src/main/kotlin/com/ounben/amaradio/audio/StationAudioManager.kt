package com.ounben.amaradio.audio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.StationAudioEntity
import com.ounben.amaradio.players.exoplayer.VolumeNormalizationAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manages global automatic loudness normalization and per-station manual / learned gain offsets.
 *
 * Keeps an in-memory lock-free cache for instant reads from ExoPlayer's audio pipeline while
 * persisting per-station settings in Room (`station_audio_settings`) and global toggle state
 * in default SharedPreferences.
 */
class StationAudioManager(context: Context) {

    interface Listener {
        fun onAutoNormalizationChanged(enabled: Boolean)
        fun onStationManualGainChanged(stationUuid: String, manualGainDb: Float)
    }

    companion object {
        private const val TAG = "StationAudioManager"
        const val PREF_VOLUME_NORMALIZATION = "settings_volume_normalization"

        fun normalizeGainStep(gainDb: Float): Float {
            val clamped = gainDb.coerceIn(
                VolumeNormalizationAudioProcessor.MIN_MANUAL_GAIN_DB,
                VolumeNormalizationAudioProcessor.MAX_MANUAL_GAIN_DB
            )
            // Snap to 0.5 dB increments
            val snapped = (clamped * 2f).roundToInt() / 2f
            return if (abs(snapped) < 0.01f) 0f else snapped
        }

        fun formatGainDb(gainDb: Float): String {
            val normalized = normalizeGainStep(gainDb)
            return when {
                normalized > 0f -> String.format(java.util.Locale.US, "+%.1f dB", normalized)
                normalized < 0f -> String.format(java.util.Locale.US, "%.1f dB", normalized)
                else -> "0.0 dB"
            }
        }
    }

    private val appContext = context.applicationContext
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val dao = AMARadioUserDatabase.getDatabase(appContext).stationAudioDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cache = ConcurrentHashMap<String, StationAudioEntity>()
    private val listeners = CopyOnWriteArraySet<Listener>()

    private val _stationGains = MutableStateFlow<Map<String, Float>>(emptyMap())
    val stationGains: StateFlow<Map<String, Float>> = _stationGains.asStateFlow()

    private val _isAutoNormalizationEnabled = MutableStateFlow(
        sharedPref.getBoolean(PREF_VOLUME_NORMALIZATION, false)
    )
    val isAutoNormalizationEnabled: StateFlow<Boolean> = _isAutoNormalizationEnabled.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == PREF_VOLUME_NORMALIZATION || key == null) {
            val enabled = prefs.getBoolean(PREF_VOLUME_NORMALIZATION, false)
            if (_isAutoNormalizationEnabled.value != enabled) {
                _isAutoNormalizationEnabled.value = enabled
                listeners.forEach { it.onAutoNormalizationChanged(enabled) }
            }
        }
    }

    init {
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
        reloadFromDb()
    }

    fun reloadFromDb() {
        scope.launch {
            try {
                val all = dao.getAll()
                cache.clear()
                val manualMap = HashMap<String, Float>(all.size)
                all.forEach { entity ->
                    cache[entity.stationUuid] = entity
                    if (abs(entity.manualGainDb) >= 0.01f) {
                        manualMap[entity.stationUuid] = entity.manualGainDb
                    }
                }
                _stationGains.value = manualMap
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load station audio settings", e)
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun getManualGainDb(stationUuid: String?): Float {
        if (stationUuid.isNullOrBlank()) return 0f
        return cache[stationUuid]?.manualGainDb ?: 0f
    }

    fun getLearnedGainDb(stationUuid: String?): Float? {
        if (stationUuid.isNullOrBlank()) return null
        return cache[stationUuid]?.learnedGainDb
    }

    fun setAutoNormalizationEnabled(enabled: Boolean) {
        sharedPref.edit { putBoolean(PREF_VOLUME_NORMALIZATION, enabled) }
        if (_isAutoNormalizationEnabled.value != enabled) {
            _isAutoNormalizationEnabled.value = enabled
            listeners.forEach { it.onAutoNormalizationChanged(enabled) }
        }
    }

    fun setManualGainDb(stationUuid: String, gainDb: Float) {
        if (stationUuid.isBlank()) return
        val normalized = normalizeGainStep(gainDb)
        val existing = cache[stationUuid]
        val learned = existing?.learnedGainDb
        val now = System.currentTimeMillis()

        if (normalized == 0f && learned == null) {
            cache.remove(stationUuid)
        } else {
            cache[stationUuid] = StationAudioEntity(
                stationUuid = stationUuid,
                manualGainDb = normalized,
                learnedGainDb = learned,
                updatedAt = now
            )
        }

        // Update Compose-facing flow immediately
        val updatedMap = HashMap(_stationGains.value)
        if (normalized == 0f) {
            updatedMap.remove(stationUuid)
        } else {
            updatedMap[stationUuid] = normalized
        }
        _stationGains.value = updatedMap

        // Notify live audio pipeline immediately
        listeners.forEach { it.onStationManualGainChanged(stationUuid, normalized) }

        scope.launch {
            try {
                val entity = cache[stationUuid]
                if (entity == null) {
                    dao.deleteByStationUuid(stationUuid)
                } else {
                    dao.upsert(entity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist manual gain for $stationUuid", e)
            }
        }
    }

    fun saveLearnedGainDb(stationUuid: String, learnedGainDb: Float) {
        if (stationUuid.isBlank()) return
        val clamped = learnedGainDb.coerceIn(
            VolumeNormalizationAudioProcessor.MIN_AUTO_GAIN_DB,
            VolumeNormalizationAudioProcessor.MAX_AUTO_GAIN_DB
        )
        val existing = cache[stationUuid]
        val manual = existing?.manualGainDb ?: 0f
        val updated = StationAudioEntity(
            stationUuid = stationUuid,
            manualGainDb = manual,
            learnedGainDb = clamped,
            updatedAt = System.currentTimeMillis()
        )
        cache[stationUuid] = updated

        scope.launch {
            try {
                dao.upsert(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist learned gain for $stationUuid", e)
            }
        }
    }
}
