package com.ounben.amaradio.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
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

        Log.d("SYNC_DEBUG", "Starting sync process (lastchange)...")

        val params = mapOf("limit" to "1000")
        val result = tryFetchFromServers(app, "json/stations/lastchange", params)
        
        if (result != null) {
            val stations = DataRadioStation.DecodeJson(result)
            if (!stations.isNullOrEmpty()) {
                Log.d("SYNC_DEBUG", "Received ${stations.size} stations from API")
                
                val entities = stations.map { it.toEntity() }
                stationDao.insertAll(entities)
                
                val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(app).edit().putString("last_db_sync_time", now).apply()
                
                Log.d("SYNC_DEBUG", "Successfully inserted ${entities.size} entities. First: ${entities[0].name}, Date: ${entities[0].lastChangeTime}")
                androidx.work.ListenableWorker.Result.success()
            } else {
                Log.w("SYNC_DEBUG", "API returned empty station list")
                androidx.work.ListenableWorker.Result.success()
            }
        } else {
            Log.e("SYNC_DEBUG", "Sync failed on all servers")
            androidx.work.ListenableWorker.Result.retry()
        }
    }

    private suspend fun tryFetchFromServers(app: AMARadioApp, path: String, params: Map<String, String>): String? {
        val currentServer = RadioBrowserServerManager.getCurrentServer()
        Log.d("SYNC_DEBUG", "Trying server: $currentServer")
        val res = Utils.downloadFeedRelative(app.httpClient, app, path, true, params)
        if (res != null) return res

        RadioBrowserServerManager.rotateServer()
        val rotatedServer = RadioBrowserServerManager.getCurrentServer()
        Log.d("SYNC_DEBUG", "Rotating to server: $rotatedServer")
        return Utils.downloadFeedRelative(app.httpClient, app, path, true, params)
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            // Changed to UPDATE to ensure new logic (lastchange) is applied even if a job was already enqueued
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "StationSync",
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }
    }
}
