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
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        
        // Start playback logic immediately
        job = scope.launch(Dispatchers.Main) {
            try {
                // Get real link in background
                val result = withContext(Dispatchers.IO) {
                    if (!stationToPlay.hasValidUuid()) {
                        if (!stationToPlay.refresh(AMARadioApp.httpClient, ctx)) return@withContext null
                    }
                    Utils.getRealStationLink(AMARadioApp.httpClient, ctx.applicationContext, stationToPlay.StationUuid)
                }

                if (result != null) {
                    stationToPlay.playableUrl = result
                    playFunc.play(result)
                } else {
                    Toast.makeText(ctx.applicationContext, R.string.error_station_load, Toast.LENGTH_SHORT).show()
                }
                postExecuteTask?.onPostExecute(if (result != null) ExecutionResult.SUCCESS else ExecutionResult.FAILURE)
            } catch (e: Exception) {
                Log.e("PLAY", "Error in PlayStationTask", e)
                postExecuteTask?.onPostExecute(ExecutionResult.FAILURE)
            } finally {
                AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))
            }
        }

        // Parallel: Add to history and auto-favorite in background without blocking play start
        scope.launch(Dispatchers.Default) {
            AMARadioApp.historyManager.add(stationToPlay)
            
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

    fun cancel() {
        job?.cancel()
    }
}
