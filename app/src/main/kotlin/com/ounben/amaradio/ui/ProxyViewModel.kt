package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.proxy.ProxySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.net.Proxy
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class ProxyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AMARadioApp
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)

    data class ProxyUiState(
        val host: String = "",
        val port: String = "",
        val type: Proxy.Type = Proxy.Type.DIRECT,
        val login: String = "",
        val password: String = "",
        val testResult: String = "",
        val isTesting: Boolean = false
    )

    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState: StateFlow<ProxyUiState> = _uiState.asStateFlow()

    init {
        ProxySettings.fromPreferences(sharedPref)?.let { settings ->
            _uiState.update { it.copy(
                host = settings.host,
                port = settings.port.toString(),
                type = settings.type ?: Proxy.Type.DIRECT,
                login = settings.login,
                password = settings.password
            ) }
        }
    }

    fun onHostChange(v: String) = _uiState.update { it.copy(host = v) }
    fun onPortChange(v: String) = _uiState.update { it.copy(port = v) }
    fun onTypeChange(v: Proxy.Type) = _uiState.update { it.copy(type = v) }
    fun onLoginChange(v: String) = _uiState.update { it.copy(login = v) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }

    fun save() {
        val settings = ProxySettings().apply {
            host = _uiState.value.host
            port = Utils.parseIntWithDefault(_uiState.value.port, 0)
            login = _uiState.value.login
            password = _uiState.value.password
            type = _uiState.value.type
        }
        val editor = sharedPref.edit()
        settings.toPreferences(editor)
        editor.apply()
        app.rebuildHttpClient()
    }

    fun testProxy() {
        val settings = ProxySettings().apply {
            host = _uiState.value.host
            port = Utils.parseIntWithDefault(_uiState.value.port, 0)
            login = _uiState.value.login
            password = _uiState.value.password
            type = _uiState.value.type
        }

        _uiState.update { it.copy(isTesting = true, testResult = "") }
        
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val builder = app.newHttpClientWithoutProxy()
                    .connectTimeout(10.seconds)
                    .writeTimeout(10.seconds)
                    .readTimeout(10.seconds)

                if (!Utils.setOkHttpProxy(builder, settings)) {
                    app.getString(R.string.settings_proxy_invalid)
                } else {
                    val client = builder.build()
                    val request = Request.Builder().url("http://radio-browser.info").build()
                    try {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                app.getString(R.string.settings_proxy_working, "radio-browser.info")
                            } else {
                                String.format(Locale.ROOT, app.getString(R.string.settings_proxy_not_working), "radio-browser.info", response.message)
                            }
                        }
                    } catch (e: IOException) {
                        String.format(Locale.ROOT, app.getString(R.string.settings_proxy_not_working), "radio-browser.info", e.message)
                    }
                }
            }
            _uiState.update { it.copy(isTesting = false, testResult = result) }
        }
    }
}
