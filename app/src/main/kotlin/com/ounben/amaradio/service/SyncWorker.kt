package com.ounben.amaradio.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.RadioBrowserServerManager
import com.ounben.amaradio.Utils
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        val app = applicationContext as AMARadioApp
        val database = AMARadioDatabase.getDatabase(app)
        val stationDao = database.stationDao()

        // 1. Get last sync time from DB
        val lastSyncTime = stationDao.getLastSyncTime() ?: "2024-01-01 00:00:00"
        Log.d("SYNC", "Starting background sync since: $lastSyncTime")

        // 2. Cascade server logic
        val params = mapOf("limit" to "1000")
        val result = tryFetchFromServers(app, "json/stations/changed", params)
        
        if (result != null) {
            val stations = DataRadioStation.DecodeJson(result)
            if (!stations.isNullOrEmpty()) {
                val entities = stations.map { it.toEntity() }
                stationDao.insertAll(entities)
                Log.d("SYNC", "Successfully synced ${entities.size} stations")
                androidx.work.ListenableWorker.Result.success()
            } else {
                Log.d("SYNC", "No changes found")
                androidx.work.ListenableWorker.Result.success()
            }
        } else {
            Log.e("SYNC", "Sync failed on all servers")
            androidx.work.ListenableWorker.Result.retry()
        }
    }

    private suspend fun tryFetchFromServers(app: AMARadioApp, path: String, params: Map<String, String>): String? {
        // Try Official Servers
        val currentServer = RadioBrowserServerManager.getCurrentServer()
        if (currentServer != null) {
            val res = Utils.downloadFeedRelative(app.httpClient, app, path, true, params)
            if (res != null) return res
        }

        // Try Rotation
        RadioBrowserServerManager.rotateServer()
        val rotatedRes = Utils.downloadFeedRelative(app.httpClient, app, path, true, params)
        if (rotatedRes != null) return rotatedRes

        // Mirror Fallback logic is already partly in Utils.downloadFeedRelative
        return null
    }

    companion object {
        fun enqueue(context: Context) {
            // Constraints: Works on Mobile Data (CONNECTED) and ignores battery status
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 30 minute delay after enqueue to avoid direct execution on app start
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "StationSync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
