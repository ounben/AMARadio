package net.ounben.AMARadio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import net.ounben.AMARadio.BuildConfig
import kotlinx.coroutines.*

open class FragmentBase : Fragment() {
    private var relativeUrl: String? = null
    private var urlResult: String? = null
    private var isCreated = false
    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCreated = true
        if (relativeUrl == null) {
            val bundle = this.arguments
            relativeUrl = bundle?.getString("url")
        }
        DownloadUrl(false)
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    protected fun getUrlResult(): String? = urlResult

    protected fun hasUrl(): Boolean = !TextUtils.isEmpty(relativeUrl)

    @JvmOverloads
    fun DownloadUrl(forceUpdate: Boolean, displayProgress: Boolean = true) {
        if (!isCreated) return
        
        downloadJob?.cancel()

        val context = context ?: return
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val showBroken = sharedPref.getBoolean("show_broken", false)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Download relativeUrl: $relativeUrl")
        }

        val url = relativeUrl
        if (!url.isNullOrBlank()) {
            val cache = Utils.getCacheFile(context, url)
            if (cache == null || forceUpdate) {
                if (displayProgress) {
                    LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
                }

                val AMARadioApp = requireActivity().application as AMARadioApp
                val httpClient = AMARadioApp.httpClient

                downloadJob = scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val p = HashMap<String, String>()
                        p["hidebroken"] = (!showBroken).toString()
                        Utils.downloadFeedRelative(httpClient, context, url, forceUpdate, p)
                    }

                    DownloadFinished()
                    LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
                    
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Download relativeUrl finished: $url")
                    }

                    if (result != null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Download relativeUrl OK: $url")
                        }
                        urlResult = result
                        RefreshListGui()
                    } else {
                        try {
                            Toast.makeText(context, resources.getText(R.string.error_list_update), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("ERR", e.toString())
                        }
                    }
                }
            } else {
                urlResult = cache
                DownloadFinished()
                RefreshListGui()
            }
        } else {
            RefreshListGui()
        }
    }

    protected open fun RefreshListGui() {}
    protected open fun DownloadFinished() {}

    companion object {
        private const val TAG = "FragmentBase"
    }
}
