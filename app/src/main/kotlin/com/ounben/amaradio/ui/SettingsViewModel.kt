package com.ounben.amaradio.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.ounben.amaradio.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)

    data class SettingsUiState(
        val themeName: String = "system",
        val uiScaleLevel: String = "standard",
        val startupAction: String = "",
        val autoPlayOnStartup: Boolean = false,
        val autoOffOnStartup: Boolean = false,
        val autoOffTimeout: String = "10",
        val loadIcons: Boolean = true,
        val autoFavorite: Boolean = false,
        val showBroken: Boolean = false,
        val singleUseTags: Boolean = false,
        val playExternal: Boolean = false,
        val warnNoWifi: Boolean = false,
        val pauseWhenNoisy: Boolean = true,
        val autoResumeHeadset: Boolean = false,
        val autoResumeBluetooth: Boolean = false,
        val connectTimeout: Int = 5,
        val readTimeout: Int = 10,
        val retryTimeout: Int = 10,
        val retryDelay: Int = 100,
        val resumeWithin: Int = 60
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadSettings()
    }

    init {
        loadSettings()
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun loadSettings() {
        val app = getApplication<Application>()
        val defaultStartupAction = app.getString(R.string.startup_show_history)
        _uiState.update {
            it.copy(
                themeName = sharedPref.getString("theme_name", "system") ?: "system",
                uiScaleLevel = sharedPref.getString("ui_scale_level", "standard") ?: "standard",
                startupAction = sharedPref.getString("startup_action", defaultStartupAction) ?: defaultStartupAction,
                autoPlayOnStartup = sharedPref.getBoolean("auto_play_on_startup", false),
                autoOffOnStartup = sharedPref.getBoolean("auto_off_on_startup", false),
                autoOffTimeout = sharedPref.getString("auto_off_timeout", "10") ?: "10",
                loadIcons = sharedPref.getBoolean("load_icons", true),
                autoFavorite = sharedPref.getBoolean("auto_favorite", false),
                showBroken = sharedPref.getBoolean("show_broken", false),
                singleUseTags = sharedPref.getBoolean("single_use_tags", false),
                playExternal = sharedPref.getBoolean("play_external", false),
                warnNoWifi = sharedPref.getBoolean("warn_no_wifi", false),
                pauseWhenNoisy = sharedPref.getBoolean("pause_when_noisy", true),
                autoResumeHeadset = sharedPref.getBoolean("auto_resume_on_wired_headset_connection", false),
                autoResumeBluetooth = sharedPref.getBoolean("auto_resume_on_bluetooth_a2dp_connection", false),
                connectTimeout = sharedPref.getInt("stream_connect_timeout", 5),
                readTimeout = sharedPref.getInt("stream_read_timeout", 10),
                retryTimeout = sharedPref.getInt("settings_retry_timeout", 10),
                retryDelay = sharedPref.getInt("settings_retry_delay", 100),
                resumeWithin = sharedPref.getInt("settings_resume_within", 60)
            )
        }
    }

    fun updateString(key: String, value: String) {
        sharedPref.edit().putString(key, value).apply()
    }

    fun updateBoolean(key: String, value: Boolean) {
        sharedPref.edit().putBoolean(key, value).apply()
    }

    fun updateInt(key: String, value: Int) {
        sharedPref.edit().putInt(key, value).apply()
    }
}
