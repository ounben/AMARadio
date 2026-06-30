package com.ounben.amaradio

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.MainScreen
import com.ounben.amaradio.ui.MainViewModel
import com.ounben.amaradio.utils.LocaleUtils
import com.ounben.amaradio.utils.UiScaler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivityMain : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var mainViewModel: MainViewModel
    private var sharedPref: SharedPreferences? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val saveM3ULauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val app = application as AMARadioApp
                scope.launch {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            contentResolver.openOutputStream(uri)?.use { os ->
                                val writer = os.bufferedWriter(Charsets.UTF_8)
                                app.favouriteManager.exportM3U(writer)
                            } ?: false
                        } catch (e: Exception) {
                            Log.e("MAIN", "Save failed", e)
                            false
                        }
                    }
                    if (success) Utils.showModernToast(this@ActivityMain, R.string.notify_save_playlist_ok)
                }
            }
        }
    }

    private val loadM3ULauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val app = application as AMARadioApp
                scope.launch {
                    val loadedStations = withContext(Dispatchers.IO) {
                        try {
                            contentResolver.openInputStream(uri)?.use { isStr ->
                                app.favouriteManager.importM3U(isStr.bufferedReader(Charsets.UTF_8))
                            }
                        } catch (e: Exception) {
                            Log.e("MAIN", "Load failed", e)
                            null
                        }
                    }
                    if (loadedStations != null && loadedStations.isNotEmpty()) {
                        app.favouriteManager.addMultiple(loadedStations)
                        Utils.showModernToast(this@ActivityMain, R.string.notify_load_playlist_ok)
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(newBase)
        val lang = sharedPref.getString("settings_language", "system") ?: "system"
        val localeContext = LocaleUtils.wrapContext(newBase, lang)
        super.attachBaseContext(UiScaler.wrapContext(localeContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d("MAIN", "onCreate started")

        try {
            sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            sharedPref?.registerOnSharedPreferenceChangeListener(this)
            
            mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
            
            PlayerServiceUtil.startService(applicationContext)

            setContent {
                AMARadioTheme {
                    MainScreen(
                        onSaveM3U = { triggerSaveM3U() },
                        onLoadM3U = { triggerLoadM3U() }
                    )
                }
            }
            
            setupBroadcastReceiver()
        } catch (e: Exception) {
            Log.e("MAIN", "onCreate failed", e)
        }
    }

    private fun triggerSaveM3U() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        saveM3ULauncher.launch(intent)
    }

    private fun triggerLoadM3U() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
        }
        loadM3ULauncher.launch(intent)
    }

    private fun setupBroadcastReceiver() {
        scope.launch {
            AppEventManager.events.collect { intent ->
                when (intent.action) {
                    ACTION_HIDE_LOADING -> if (this@ActivityMain::mainViewModel.isInitialized) mainViewModel.setLoading(false)
                    ACTION_SHOW_LOADING -> if (this@ActivityMain::mainViewModel.isInitialized) mainViewModel.setLoading(true)
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "theme_name" || key == UiScaler.PREF_KEY_UI_SCALE || key == "settings_language") {
            recreate()
        }
    }

    override fun onDestroy() {
        sharedPref?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW_LOADING = "com.ounben.amaradio.show_loading"
        const val ACTION_HIDE_LOADING = "com.ounben.amaradio.hide_loading"
        const val LAUNCH_EQUALIZER_REQUEST = 1
        const val MAX_DYNAMIC_LAUNCHER_SHORTCUTS = 4
        const val FRAGMENT_FROM_BACKSTACK = 777
    }
}
