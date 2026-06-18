package com.ounben.amaradio.utils

import android.content.Context
import android.os.RemoteException
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.IPlayerService
import com.ounben.amaradio.Utils
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class GetRealLinkAndPlayTask(context: Context, private val station: DataRadioStation, playerService: IPlayerService) {
    private val contextRef = WeakReference(context)
    private val playerServiceRef = WeakReference(playerService)
    private val httpClient = (context.applicationContext as AMARadioApp).httpClient
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    fun execute() {
        job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                val context = contextRef.get()
                if (context != null) {
                    Utils.getRealStationLink(httpClient, context.applicationContext, station.StationUuid)
                } else null
            }
            
            val playerService = playerServiceRef.get()
            if (result != null && playerService != null && job?.isCancelled == false) {
                try {
                    station.playableUrl = result
                    playerService.SetStation(station)
                    playerService.Play(false)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun cancel(mayInterruptIfRunning: Boolean) {
        job?.cancel()
    }
}
