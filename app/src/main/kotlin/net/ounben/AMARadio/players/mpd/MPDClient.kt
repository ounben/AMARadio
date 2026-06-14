package net.ounben.AMARadio.players.mpd

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class MPDClient(context: Context) {
    companion object {
        private const val TAG = "MPDClient"
        private const val QUICK_REFRESH_TIMEOUT = 150L
        private const val ALIVE_REFRESH_TIMEOUT = 1000L
        private const val DEAD_REFRESH_TIMEOUT = 1000L
    }

    private val userTaskScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val checkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var quickCheckJob: Job? = null
    private var aliveCheckJob: Job? = null
    private var deadCheckJob: Job? = null

    val mpdServersRepository: MPDServersRepository = MPDServersRepository(context)
    private val mpdServers: LiveData<List<MPDServerData>> = mpdServersRepository.allServers

    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private val serverChangesQueue = ConcurrentLinkedQueue<MPDServerData>()

    private val aliveMpdServers = mutableSetOf<MPDServerData>()
    private val deadMpdServers = mutableSetOf<MPDServerData>()
    private val serversLock = Any()

    var isMpdEnabled = false
        private set

    var autoUpdateEnabled = false
        private set

    fun enqueueTask(server: MPDServerData, task: MPDAsyncTask) {
        if (!isMpdEnabled) {
            Log.e(TAG, "Trying to enqueue task when mpd is not enabled!")
            return
        }

        task.setTimeout(getTimeout(server.hostname).toLong())
        task.setParams(this, server)

        userTaskScope.launch {
            task.run()
        }
    }

    fun enableAutoUpdate() {
        if (!isMpdEnabled) {
            setMPDEnabled(true)
            Log.w(TAG, "enableAutoUpdate called with mpd disabled, enabling mpd")
        }

        autoUpdateEnabled = true
        mpdServersRepository.resetAllConnectionStatus()
        launchQuickCheck()
    }

    fun disableAutoUpdate() {
        autoUpdateEnabled = false
        cancelCheckJobs()
    }

    fun launchQuickCheck() {
        if (!autoUpdateEnabled) {
            Log.e(TAG, "Trying to launch quick servers check while autoUpdateEnabled = false!")
            return
        }

        cancelCheckJobs()

        val servers = ArrayList(mpdServers.value ?: emptyList())
        quickCheckJob = checkScope.launch {
            checkServers(servers) { QUICK_REFRESH_TIMEOUT.toInt() }
            
            aliveCheckJob = launch {
                while (autoUpdateEnabled && isActive) {
                    val alive = synchronized(serversLock) { ArrayList(aliveMpdServers) }
                    checkServers(alive) { ALIVE_REFRESH_TIMEOUT.toInt() }
                    delay(2000)
                }
            }
            
            deadCheckJob = launch {
                while (autoUpdateEnabled && isActive) {
                    val dead = synchronized(serversLock) { ArrayList(deadMpdServers) }
                    checkServers(dead) { DEAD_REFRESH_TIMEOUT.toInt() }
                    delay(8000)
                }
            }
        }
    }

    private fun cancelCheckJobs() {
        quickCheckJob?.cancel()
        quickCheckJob = null
        aliveCheckJob?.cancel()
        aliveCheckJob = null
        deadCheckJob?.cancel()
        deadCheckJob = null

        synchronized(serversLock) {
            aliveMpdServers.clear()
            deadMpdServers.clear()
        }
    }

    fun setMPDEnabled(enabled: Boolean) {
        if (enabled != isMpdEnabled) {
            if (!enabled) {
                disableAutoUpdate()
            }
            isMpdEnabled = enabled
        }
    }

    fun notifyServerUpdate(mpdServerData: MPDServerData) {
        serverChangesQueue.add(mpdServerData)

        mainThreadHandler.post {
            var changedData: MPDServerData?
            while (serverChangesQueue.poll().also { changedData = it } != null) {
                mpdServersRepository.updateRuntimeData(changedData!!)
            }
        }
    }

    private fun getTimeout(hostname: String): Int {
        return if (hostname.startsWith("192.168.") ||
            hostname.startsWith("127.0.") ||
            hostname.startsWith("localhost") ||
            hostname.startsWith("10.") ||
            hostname.contains(".local")
        ) 300 else 2000
    }

    private fun checkServers(servers: Iterable<MPDServerData>, timeoutFunc: (MPDServerData) -> Int) {
        for (mpdServerData in servers) {
            val task = MPDAsyncTask()
            task.setStages(
                arrayOf(
                    MPDAsyncTask.okReadStage(),
                    MPDAsyncTask.statusReadStage(false)
                ),
                arrayOf(MPDAsyncTask.statusWriteStage()),
                object : MPDAsyncTask.FailureCallback {
                    override fun onFailure(task: MPDAsyncTask) {
                        task.getMpdServerData()?.let {
                            if (it.connected) {
                                it.connected = false
                                task.notifyServerUpdated()
                            }
                        }
                    }
                }
            )

            task.setTimeout(timeoutFunc(mpdServerData).toLong())
            task.setParams(this, mpdServerData)

            task.run()

            synchronized(serversLock) {
                if (mpdServerData.connected) {
                    aliveMpdServers.add(mpdServerData)
                    deadMpdServers.remove(mpdServerData)
                } else {
                    aliveMpdServers.remove(mpdServerData)
                    deadMpdServers.add(mpdServerData)
                }
            }
        }
    }
}
