package net.ounben.AMARadio.utils

import android.content.Context
import android.os.RemoteException
import kotlinx.coroutines.*
import net.ounben.AMARadio.IPlayerService
import net.ounben.AMARadio.AMARadioApp
import net.ounben.AMARadio.Utils
import net.ounben.AMARadio.station.DataRadioStation
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
