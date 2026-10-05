package com.ounben.amaradio.sync

import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DatabaseSyncState {
    object Idle : DatabaseSyncState()
    data class Running(
        val current: Int,
        val total: Int,
        val formattedProgress: String,
        val mode: String
    ) : DatabaseSyncState()
    data class Success(val message: String, val lastSyncTime: String) : DatabaseSyncState()
    data class Error(val message: String) : DatabaseSyncState()
}

class DatabaseSync(private val context: Context) {

    private val app = context.applicationContext as AMARadioApp
    private val loader = DatabaseLoader(context)
    private val database = AMARadioDatabase.getDatabase(app)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(app)

    private val numberFormat = DecimalFormat("#,###")

    companion object {
        private val _progressFlow = MutableStateFlow<DatabaseSyncState>(DatabaseSyncState.Idle)
        val progressFlow: StateFlow<DatabaseSyncState> = _progressFlow.asStateFlow()
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun syncDatabase(
        mode: String = "full",
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            val errorMsg = "No internet connection"
            _progressFlow.value = DatabaseSyncState.Error(errorMsg)
            return@withContext false
        }

        try {
            if (mode == "incremental") {
                performIncrementalSync(onProgress)
            } else {
                performFullSync(onProgress)
            }
        } catch (e: SQLiteFullException) {
            Log.e("DatabaseSync", "Disk full during sync", e)
            _progressFlow.value = DatabaseSyncState.Error("Not enough disk space")
            false
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.e("DatabaseSync", "Sync failed: ${e.message}", e)
            _progressFlow.value = DatabaseSyncState.Error(e.localizedMessage ?: "Sync failed")
            false
        }
    }

    private suspend fun performFullSync(
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ): Boolean = coroutineScope {
        val totalCount = loader.getTotalStationCount() ?: 45000
        var currentLoaded = 0
        val chunkSize = 1000 // 1.000 Sender pro Abfrage
        val parallelBatches = 2 // 2x 1.000 = 2.000 Sender pro Zyklus

        reportProgress(0, totalCount, mode = "full", onProgress = onProgress)

        while (currentLoaded < totalCount) {
            coroutineContext.ensureActive()

            val tasks = (0 until parallelBatches).map { batchIdx ->
                val offset = currentLoaded + (batchIdx * chunkSize)
                if (offset < totalCount) {
                    async(Dispatchers.IO) {
                        loader.getStationsChunk(offset = offset, limit = chunkSize)
                    }
                } else null
            }.filterNotNull()

            val results = tasks.awaitAll()
            var batchCount = 0

            for (chunk in results) {
                if (!chunk.isNullOrEmpty()) {
                    val entities = chunk.map { it.toEntity() }
                    database.stationDao().syncBatch(entities)
                    batchCount += chunk.size
                }
            }

            if (batchCount == 0) {
                // No more stations returned from API
                break
            }

            currentLoaded += batchCount
            reportProgress(currentLoaded, totalCount, mode = "full", onProgress = onProgress)
        }

        saveLastSyncTimestamp()
        val finalSyncTime = prefs.getString("last_db_sync_time", "Just now") ?: "Just now"
        _progressFlow.value = DatabaseSyncState.Success("Full sync completed", finalSyncTime)
        true
    }

    private suspend fun performIncrementalSync(
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ): Boolean = coroutineScope {
        val maxIncrementalLimit = 1000 // Inkrementell: 1.000 neueste Änderungen in einem einzigen Aufruf laden

        reportProgress(0, maxIncrementalLimit, mode = "incremental", onProgress = onProgress)

        coroutineContext.ensureActive()

        val changed = loader.getStationsLastChange(offset = 0, limit = maxIncrementalLimit)
        if (!changed.isNullOrEmpty()) {
            val entities = changed.map { it.toEntity() }
            database.stationDao().syncBatch(entities)

            reportProgress(entities.size, maxIncrementalLimit, mode = "incremental", onProgress = onProgress)
        } else {
            reportProgress(0, maxIncrementalLimit, mode = "incremental", onProgress = onProgress)
        }

        saveLastSyncTimestamp()
        val finalSyncTime = prefs.getString("last_db_sync_time", "Just now") ?: "Just now"
        _progressFlow.value = DatabaseSyncState.Success("Incremental sync completed", finalSyncTime)
        true
    }

    private fun reportProgress(
        current: Int,
        total: Int,
        mode: String,
        onProgress: (current: Int, total: Int, formattedText: String) -> Unit
    ) {
        val formattedCurrent = numberFormat.format(current)
        val formattedTotal = numberFormat.format(total)
        val text = "$formattedCurrent / $formattedTotal"

        _progressFlow.value = DatabaseSyncState.Running(
            current = current,
            total = total,
            formattedProgress = text,
            mode = mode
        )
        onProgress(current, total, text)
    }

    private fun saveLastSyncTimestamp() {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        prefs.edit().putString("last_db_sync_time", nowStr).apply()
    }
}
