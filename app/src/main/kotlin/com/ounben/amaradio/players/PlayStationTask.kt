package com.ounben.amaradio.players

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.mpd.MPDClient
import com.ounben.amaradio.players.mpd.MPDServerData
import com.ounben.amaradio.players.mpd.tasks.MPDPlayTask
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
        fun playMPD(mpdClient: MPDClient, mpdServerData: MPDServerData, stationToPlay: DataRadioStation, ctx: Context): PlayStationTask {
            return PlayStationTask(stationToPlay, ctx, { url -> mpdClient.enqueueTask(mpdServerData, MPDPlayTask(url, null)) }, null)
        }

        @JvmStatic
        fun playExternal(stationToPlay: DataRadioStation, ctx: Context): PlayStationTask {
            return PlayStationTask(stationToPlay, ctx, { url ->
                val share = Intent(Intent.ACTION_VIEW)
                share.setDataAndType(Uri.parse(url), "audio/*")
                ctx.startActivity(share)
            }, null)
        }

        @JvmStatic
        fun playCAST(stationToPlay: DataRadioStation, ctx: Context): PlayStationTask {
            val AMARadioApp = ctx.applicationContext as AMARadioApp
            val castHandler = AMARadioApp.castHandler
            return PlayStationTask(stationToPlay, ctx, { url -> castHandler.playRemote(stationToPlay.Name, url, stationToPlay.IconUrl) }, null)
        }
    }

    fun execute() {
        val ctx = contextRef.get() ?: return
        AppEventManager.sendEvent(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val AMARadioApp = ctx.applicationContext as AMARadioApp
        AMARadioApp.historyManager.add(stationToPlay)
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (sharedPref.getBoolean("auto_favorite", false)) {
            val favouriteManager = AMARadioApp.favouriteManager
            if (!favouriteManager.has(stationToPlay.StationUuid)) {
                favouriteManager.add(stationToPlay)
                Toast.makeText(ctx, ctx.getString(R.string.notify_autostarred), Toast.LENGTH_SHORT).show()
            }
        }

        job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!stationToPlay.hasValidUuid()) {
                    if (!stationToPlay.refresh(AMARadioApp.httpClient, ctx)) {
                        return@withContext null
                    }
                }
                Utils.getRealStationLink(AMARadioApp.httpClient, ctx.applicationContext, stationToPlay.StationUuid)
            }

            val context = contextRef.get() ?: return@launch
            AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))

            if (result != null) {
                stationToPlay.playableUrl = result
                playFunc.play(result)
            } else {
                Toast.makeText(context.applicationContext, context.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
            }
            postExecuteTask?.onPostExecute(if (result != null) ExecutionResult.SUCCESS else ExecutionResult.FAILURE)
        }
    }

    fun cancel(mayInterruptIfRunning: Boolean) {
        job?.cancel()
    }
}
