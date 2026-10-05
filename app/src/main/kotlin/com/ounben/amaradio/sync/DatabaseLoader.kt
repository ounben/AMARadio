package com.ounben.amaradio.sync

import android.content.Context
import android.util.Log
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.RadioBrowserServerManager
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class DatabaseLoader(private val context: Context) {

    private val app = context.applicationContext as AMARadioApp
    private val client: OkHttpClient = app.httpClient

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Fetches total station count from /json/stats
     */
    suspend fun getTotalStationCount(): Int? {
        val endpoint = "json/stats"
        val responseBody = executeWithRetryAndFailover(endpoint) ?: return null
        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val stationsCount = jsonElement.jsonObject["stations"]?.jsonPrimitive?.int
            stationsCount
        } catch (e: Exception) {
            Log.e("DatabaseLoader", "Error parsing stats JSON", e)
            null
        }
    }

    /**
     * Fetches paginated stations chunk using /json/stations?limit=100&offset={offset}
     */
    suspend fun getStationsChunk(offset: Int, limit: Int = 100): List<DataRadioStation>? {
        val endpoint = "json/stations?limit=$limit&offset=$offset&hidebroken=true"
        val responseBody = executeWithRetryAndFailover(endpoint) ?: return null
        return parseStationsJson(responseBody)
    }

    /**
     * Fetches incremental changes using /json/stations/lastchange?offset={offset}&limit={limit}
     */
    suspend fun getStationsLastChange(offset: Int, limit: Int = 100): List<DataRadioStation>? {
        val endpoint = "json/stations/lastchange?offset=$offset&limit=$limit&hidebroken=true"
        val responseBody = executeWithRetryAndFailover(endpoint) ?: return null
        return parseStationsJson(responseBody)
    }

    private fun parseStationsJson(jsonString: String): List<DataRadioStation>? {
        return try {
            DataRadioStation.DecodeJson(jsonString)?.toList()
        } catch (e: Exception) {
            Log.e("DatabaseLoader", "Error decoding stations JSON chunk", e)
            null
        }
    }

    /**
     * Executes HTTP GET with up to 3 retries (backoff 1s, 2s, 4s) on primary server,
     * and automatically switches mid-stream to backup server radiobrowser.ounben.com if primary fails.
     */
    private suspend fun executeWithRetryAndFailover(relativeEndpoint: String): String? {
        val servers = mutableListOf<String>()
        
        // 1. Primary server
        val primary = RadioBrowserServerManager.getCurrentServer() ?: "de1.api.radio-browser.info"
        servers.add(primary)

        // 2. Backup / Mirror server
        val mirror = RadioBrowserServerManager.getMirrorServer() // radiobrowser.ounben.com
        if (!servers.contains(mirror)) {
            servers.add(mirror)
        }

        for (server in servers) {
            var backoffMs = 1000L
            for (attempt in 1..3) {
                val url = RadioBrowserServerManager.constructEndpoint(server, relativeEndpoint)
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AMARadio/1.41")
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    response.use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string()
                            if (!body.isNullOrBlank()) {
                                return body
                            }
                        } else if (res.code == 503 || res.code == 500) {
                            Log.w("DatabaseLoader", "Server $server returned ${res.code} (Attempt $attempt/3)")
                        }
                    }
                } catch (e: IOException) {
                    Log.w("DatabaseLoader", "Network IOException for $server (Attempt $attempt/3): ${e.message}")
                }

                if (attempt < 3) {
                    delay(backoffMs)
                    backoffMs *= 2 // 1s, 2s, 4s
                }
            }
            Log.w("DatabaseLoader", "Primary server $server failed. Switching mid-stream to next mirror/backup server...")
        }

        Log.e("DatabaseLoader", "All servers failed for endpoint: $relativeEndpoint")
        return null
    }
}
