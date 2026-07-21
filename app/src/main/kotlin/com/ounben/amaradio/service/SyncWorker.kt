package com.ounben.amaradio.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.preference.PreferenceManager

class SyncWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        val app = applicationContext as AMARadioApp
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(app)
        
        // Respect user setting (defaults to true)
        if (!sharedPref.getBoolean("settings_auto_db_update", true)) {
            return@withContext androidx.work.ListenableWorker.Result.success()
        }

        val database = AMARadioDatabase.getDatabase(app)
        val stationDao = database.stationDao()

        Log.d("SYNC_DEBUG", "Starting sync process (lastchange)...")

        val params = mapOf("limit" to "1000")
        val result = tryFetchFromServers(app, "json/stations/lastchange", params)
        
        if (result != null) {
            val stations = DataRadioStation.DecodeJson(result)
            if (!stations.isNullOrEmpty()) {
                val entities = stations.map { it.toEntity() }
                stationDao.syncBatch(entities)
                
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                sharedPref.edit().putString("last_db_sync_time", now).apply()
                
                Log.d("SYNC_DEBUG", "Successfully synced ${entities.size} stations")
                androidx.work.ListenableWorker.Result.success()
            } else {
                androidx.work.ListenableWorker.Result.success()
            }
        } else {
            Log.e("SYNC_DEBUG", "Sync failed on all servers")
            androidx.work.ListenableWorker.Result.retry()
        }
    }

    private suspend fun tryFetchFromServers(app: AMARadioApp, path: String, params: Map<String, String>): String? {
        // Log current server for transparency in Logcat
        RadioBrowserServerManager.getCurrentServer()?.let { Log.d("SYNC_DEBUG", "Server: $it") }
        
        val res = Utils.downloadFeedRelative(app.httpClient, app, path, true, params)
        if (res != null) return res

        RadioBrowserServerManager.rotateServer()
        return Utils.downloadFeedRelative(app.httpClient, app, path, true, params)
    }

    companion object {
        fun enqueue(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            
            if (!sharedPref.getBoolean("settings_auto_db_update", true)) {
                workManager.cancelUniqueWork("ImmediateStartupSync")
                workManager.cancelUniqueWork("StationSync")
                return
            }

            val baseConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 1. Check for immediate startup sync (if older than 24h)
            val lastSyncStr = sharedPref.getString("last_db_sync_time", null)
            val needsSyncNow = if (lastSyncStr == null) true else {
                try {
                    val lastSyncDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(lastSyncStr)
                    System.currentTimeMillis() - (lastSyncDate?.time ?: 0L) > 24 * 60 * 60 * 1000
                } catch (e: Exception) { true }
            }

            if (needsSyncNow) {
                val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(baseConstraints)
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                workManager.enqueueUniqueWork("ImmediateStartupSync", ExistingWorkPolicy.KEEP, oneTimeRequest)
            }

            // 2. Regular background sync (24h interval)
            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(baseConstraints)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork("StationSync", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, periodicRequest)
        }
    }
}
