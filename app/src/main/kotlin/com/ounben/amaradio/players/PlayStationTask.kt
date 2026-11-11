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
                try {
                    val share = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(url.toUri(), "audio/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(share)
                } catch (_: Exception) {
                    Toast.makeText(ctx, ctx.getString(R.string.error_external_player_not_found), Toast.LENGTH_SHORT).show()
                }
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

            // Playlist resolution - Offload to IO dispatcher to avoid ANR on body.string()
            if (PlaylistParser.isPlaylist(streamUrl)) {
                val resolvedUrl = withContext(Dispatchers.IO) {
                    try {
                        val versionName = ctx.getString(R.string.version_name)
                        val userAgent = "AMARadio/$versionName (Android; +https://github.com/ounben/AMARadio)"
                        val request = Request.Builder()
                            .url(streamUrl)
                            .header("User-Agent", userAgent)
                            .build()
                        AMARadioApp.httpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val maxBytes = 128 * 1024L
                                val body = response.body?.use { body ->
                                    val source = body.source()
                                    source.request(maxBytes)
                                    val availableBytes = source.buffer.size.coerceAtMost(maxBytes)
                                    source.buffer.readUtf8(availableBytes)
                                }.orEmpty()

                                PlaylistParser.parse(streamUrl, body)
                            } else null
                        }
                    } catch (e: Throwable) {
                        Log.e("PLAY", "Playlist resolution failed for $streamUrl", e)
                        null
                    }
                }
                if (resolvedUrl != null) {
                    streamUrl = resolvedUrl
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
