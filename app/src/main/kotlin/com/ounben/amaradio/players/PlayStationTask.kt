package com.ounben.amaradio.players

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.playlist.PlaylistParser
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.*
import okhttp3.Request
import java.lang.ref.WeakReference

class PlayStationTask(
    private val stationToPlay: DataRadioStation,
    ctx: Context,
    private val playFunc: PlayFunc,
    private val postExecuteTask: PostExecuteTask? = null
) {
    fun interface PlayFunc {
        fun play(url: String)
    }

    enum class ExecutionResult {
        FAILURE, SUCCESS
    }

    fun interface PostExecuteTask {
        fun onPostExecute(executionResult: ExecutionResult)
    }

    private val contextRef = WeakReference(ctx)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    companion object {
        @JvmStatic
        fun playExternal(stationToPlay: DataRadioStation, ctx: Context): PlayStationTask {
            return PlayStationTask(stationToPlay, ctx, { url ->
                val share = Intent(Intent.ACTION_VIEW)
                share.setDataAndType(url.toUri(), "audio/*")
                ctx.startActivity(share)
            }, null)
        }
    }

    fun execute() {
        val ctx = contextRef.get() ?: return
        AppEventManager.sendEvent(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val AMARadioApp = ctx.applicationContext as AMARadioApp
        
        job = scope.launch {
            var streamUrl = stationToPlay.StreamUrl
            
            if (streamUrl.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx.applicationContext, R.string.error_station_load, Toast.LENGTH_SHORT).show()
                    postExecuteTask?.onPostExecute(ExecutionResult.FAILURE)
                    AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))
                }
                return@launch
            }

            // Playlist resolution
            if (PlaylistParser.isPlaylist(streamUrl)) {
                try {
                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("User-Agent", "RadioDroid")
                        .build()
                    val response = withContext(Dispatchers.IO) { 
                        AMARadioApp.httpClient.newCall(request).execute() 
                    }
                    if (response.isSuccessful) {
                        val body = response.body.string()
                        val resolvedUrl = PlaylistParser.parse(streamUrl, body)
                        if (resolvedUrl != null) {
                            streamUrl = resolvedUrl
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PLAY", "Playlist resolution failed for $streamUrl", e)
                }
            }

            withContext(Dispatchers.Main) {
                stationToPlay.playableUrl = streamUrl
                playFunc.play(streamUrl)
                postExecuteTask?.onPostExecute(ExecutionResult.SUCCESS)
                AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))
            }

            // Parallel: Add to history and auto-favorite
            withContext(Dispatchers.Default) {
                if (stationToPlay.Bitrate > 0 || stationToPlay.TagsAll.isNotEmpty()) {
                    AMARadioApp.historyManager.add(stationToPlay)
                }
                
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
                if (sharedPref.getBoolean("auto_favorite", false)) {
                    if (!AMARadioApp.favouriteManager.has(stationToPlay.StationUuid)) {
                        AMARadioApp.favouriteManager.add(stationToPlay)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, R.string.notify_autostarred, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
