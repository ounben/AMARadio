package com.ounben.amaradio

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.players.selector.PlayerType
import com.ounben.amaradio.proxy.ProxySettings
import com.ounben.amaradio.service.ConnectivityChecker
import com.ounben.amaradio.service.MediaSessionCallback
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

object Utils {
    private var loadIcons = -1
    private val testing = AtomicBoolean(false)
    private val clickThrottleMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val CLICK_THROTTLE_MS = 5000L
    var isDebug = false
        private set

    @JvmStatic
    fun init(context: Context) {
        isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    @JvmStatic
    fun getAttributedContext(context: Context): Context {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                context.createAttributionContext("player_service")
            } catch (_: Exception) {
                context
            }
        } else {
            context
        }
    }

    @JvmStatic
    fun isTesting(): Boolean = testing.get()

    @JvmStatic
    fun getCountryCode(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        return tm?.networkCountryIso?.takeIf { it.length == 2 }
            ?: tm?.simCountryIso?.takeIf { it.length == 2 }
            ?: context.resources.configuration.locales[0].country.takeIf { it.length == 2 }
    }

    @JvmStatic
    fun parseIntWithDefault(number: String?, defaultVal: Int): Int {
        return try {
            number?.toInt() ?: defaultVal
        } catch (_: NumberFormatException) {
            defaultVal
        }
    }

    private fun String.md5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @JvmStatic
    suspend fun getCacheFile(ctx: Context, cacheKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "cache_" + cacheKey.md5()
            val file = File(ctx.cacheDir, fileName)
            if (!file.exists()) return@withContext null
            
            val millis = System.currentTimeMillis() - file.lastModified()
            val hours = millis / (1000 * 60 * 60)

            if (hours < 48) {
                val content = file.readText(Charsets.UTF_8)
                if (isDebug) Log.d("UTIL", "Used cache for: $cacheKey")
                return@withContext content
            } else {
                if (isDebug) Log.d("UTIL", "Cache too old for: $cacheKey")
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("UTIL", "Error reading cache", e)
        }
        null
    }

    @JvmStatic
    suspend fun writeFileCache(ctx: Context, cacheKey: String, content: String) = withContext(Dispatchers.IO) {
        try {
            val fileName = "cache_" + cacheKey.md5()
            val file = File(ctx.cacheDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            if (isDebug) Log.d("UTIL", "Wrote cache file for: $cacheKey")
        } catch (e: Exception) {
            Log.e("UTIL", "Error writing cache", e)
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }

    private suspend fun downloadFeed(httpClient: OkHttpClient, ctx: Context, theURI: String, dictParams: Map<String, String>?, timeoutMs: Long? = null): String? = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = theURI.toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
            dictParams?.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            val url = urlBuilder.build()
            
            val client = if (timeoutMs != null) {
                httpClient.newBuilder()
                    .connectTimeout(timeoutMs.milliseconds)
                    .readTimeout(timeoutMs.milliseconds)
                    .build()
            } else {
                httpClient
            }

            val request = Request.Builder().url(url).get().build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@withContext null
                return@withContext response.body.string()
            }
        } catch (_: Exception) {
        }
        null
    }

    @JvmStatic
    suspend fun downloadFeedRelative(httpClient: OkHttpClient, ctx: Context, theRelativeUri: String, forceUpdate: Boolean, dictParams: Map<String, String>?): String? {
        if (theRelativeUri.isBlank()) return null

        val cacheKey = theRelativeUri + (dictParams?.toString() ?: "")
        
        if (!forceUpdate) {
            val cached = getCacheFile(ctx, cacheKey)
            if (cached != null) return cached
        }

        // Add Cache-Buster to params if forceUpdate is true
        val finalParams = if (forceUpdate) {
            val p = dictParams?.toMutableMap() ?: mutableMapOf()
            p["cb"] = System.currentTimeMillis().toString()
            p
        } else dictParams

        Log.i("DOWN", "Url=$theRelativeUri (forceUpdate=$forceUpdate)")

        // Try official servers
        val currentServer = RadioBrowserServerManager.getCurrentServer()
        if (currentServer != null) {
            val endpoint = RadioBrowserServerManager.constructEndpoint(currentServer, theRelativeUri)
            val result = downloadFeed(httpClient, ctx, endpoint, finalParams, 10000L) // Increase to 10s
            if (result != null) {
                Log.d("SYNC_DEBUG", "Success from Official: $endpoint")
                writeFileCache(ctx, cacheKey, result)
                return result
            }
            
            // Rotation fallback
            val serverList = RadioBrowserServerManager.getServerList(false)
            for (newServer in serverList) {
                if (newServer == currentServer) continue
                val rotEndpoint = RadioBrowserServerManager.constructEndpoint(newServer, theRelativeUri)
                val rotResult = downloadFeed(httpClient, ctx, rotEndpoint, finalParams, 5000L)
                if (rotResult != null) {
                    Log.d("SYNC_DEBUG", "Success from Rotated: $rotEndpoint")
                    RadioBrowserServerManager.setCurrentServer(newServer)
                    writeFileCache(ctx, cacheKey, rotResult)
                    return rotResult
                }
            }
        }

        // Mirror fallback
        val mirrorEndpoint = RadioBrowserServerManager.constructEndpoint(RadioBrowserServerManager.getMirrorServer(), theRelativeUri)
        val mirrorResult = downloadFeed(httpClient, ctx, mirrorEndpoint, finalParams)
        if (mirrorResult != null) {
            Log.d("SYNC_DEBUG", "Success from MIRROR: $mirrorEndpoint")
            writeFileCache(ctx, cacheKey, mirrorResult)
        }
        return mirrorResult
    }

    @JvmStatic
    suspend fun getRealStationLink(httpClient: OkHttpClient, ctx: Context, stationId: String): String? {
        // 1. Try local databases first (Custom, Favorites, History, Catalog)
        try {
            val userDb = com.ounben.amaradio.database.user.AMARadioUserDatabase.getDatabase(ctx)
            
            val customUrl = userDb.customStationDao().getByUuid(stationId)?.Url
            if (!customUrl.isNullOrEmpty()) return customUrl

            val favUrl = userDb.favoriteDao().getByUuid(stationId)?.Url
            if (!favUrl.isNullOrEmpty()) return favUrl

            val histUrl = userDb.historyDao().getByUuid(stationId)?.Url
            if (!histUrl.isNullOrEmpty()) return histUrl

            val catalogDb = com.ounben.amaradio.database.AMARadioDatabase.getDatabase(ctx)
            val catalogUrl = catalogDb.stationDao().getStationByUuid(stationId)?.url
            if (!catalogUrl.isNullOrEmpty()) return catalogUrl
        } catch (e: Exception) {
            Log.e("Utils", "Error checking local DBs for $stationId", e)
        }

        // 2. Fallback to RadioBrowser online API if not found in any local database
        val result = downloadFeedRelative(httpClient, ctx, "json/url/$stationId", true, null)
        if (result != null) {
            return try {
                val jsonObj = withContext(Dispatchers.Default) {
                    Json.parseToJsonElement(result).jsonObject
                }
                jsonObj["url"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    @JvmStatic
    suspend fun getStationByUuid(httpClient: OkHttpClient, ctx: Context, stationUuid: String): DataRadioStation? {
        val result = downloadFeedRelative(httpClient, ctx, "json/stations/byuuid/$stationUuid", true, null)
        if (result != null) {
            try {
                val list = withContext(Dispatchers.Default) { DataRadioStation.DecodeJson(result) }
                if (list?.size == 1) return list[0]
            } catch (_: Exception) {}
        }
        return null
    }

    @JvmStatic
    suspend fun getStationsByUuid(httpClient: OkHttpClient, ctx: Context, listUUids: List<String>): List<DataRadioStation>? {
        if (listUUids.isEmpty()) return emptyList()
        val allResults = mutableListOf<DataRadioStation>()
        val chunks = listUUids.chunked(50)
        
        for (chunk in chunks) {
            val p = HashMap<String, String>()
            p["uuids"] = TextUtils.join(",", chunk)
            val result = downloadFeedRelative(httpClient, ctx, "json/stations/byuuid", true, p)
            if (result != null) {
                try {
                    val decoded = withContext(Dispatchers.Default) { DataRadioStation.DecodeJson(result) }
                    if (decoded != null) allResults.addAll(decoded)
                } catch (_: Exception) {}
            }
        }
        return if (allResults.isEmpty() && listUUids.isNotEmpty()) null else allResults
    }

    @JvmStatic
    fun getCurrentOrLastStation(ctx: Context): DataRadioStation? {
        var station = PlayerServiceUtil.getCurrentStation()
        if (station == null) {
            val app = ctx.applicationContext as AMARadioApp
            station = app.historyManager.first
        }
        return station
    }

    @JvmStatic
    fun playAndWarnIfMetered(context: Context, station: DataRadioStation, playerType: PlayerType, playFunc: Runnable) {
        playAndWarnIfMetered(context, station, playerType, playFunc, object : MeteredWarningCallback {
            override fun warn(station: DataRadioStation, playerType: PlayerType) {
                PlayerServiceUtil.setStation(station)
                PlayerServiceUtil.warnAboutMeteredConnection(playerType)
            }
        })
    }

    @JvmStatic
    fun urlIndicatesHlsStream(streamUrl: String): Boolean {
        val p = Pattern.compile(".*\\.m3u8([#?\\s].*)?$")
        return p.matcher(streamUrl).matches()
    }

    interface MeteredWarningCallback {
        fun warn(station: DataRadioStation, playerType: PlayerType)
    }

    @JvmStatic
    fun playAndWarnIfMetered(context: Context, station: DataRadioStation, playerType: PlayerType,
                             playFunc: Runnable, warningCallback: MeteredWarningCallback) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        if (sharedPref.getBoolean("warn_no_wifi", false) && (ConnectivityChecker.getCurrentConnectionType(context) == ConnectivityChecker.ConnectionType.METERED)) {
            warningCallback.warn(station, playerType)
        } else {
            playFunc.run()
        }
    }

    @JvmStatic
    fun play(station: DataRadioStation) {
        PlayerServiceUtil.play(station)
    }

    /**
     * Safely starts an activity and catches ActivityNotFoundException.
     * Shows a toast to the user if no app can handle the intent.
     */
    @JvmStatic
    fun safeStartActivity(context: Context, intent: Intent, errorResId: Int = R.string.error_no_browser) {
        try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("Utils", "Failed to start activity for intent: ${intent.action}", e)
            if (context is Activity) {
                showModernToast(context, errorResId)
            } else {
                android.widget.Toast.makeText(context, errorResId, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    @JvmStatic
    fun shouldLoadIcons(context: Context): Boolean {
        if (loadIcons != -1) return loadIcons == 1
        val result = PreferenceManager.getDefaultSharedPreferences(context.applicationContext).getBoolean("load_icons", false)
        loadIcons = if (result) 1 else 0
        return result
    }

    @JvmStatic
    fun getTheme(context: Context): String {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPref.getString("theme_name", "system") ?: "system"
    }

    @JvmStatic
    fun getThemeResId(context: Context): Int {
        val isDark = when (getTheme(context)) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        return if (isDark) R.style.MyMaterialTheme_Dark else R.style.MyMaterialTheme
    }

    @JvmStatic
    fun isDarkTheme(context: Context): Boolean = getThemeResId(context) == R.style.MyMaterialTheme_Dark

    @JvmStatic
    fun setOkHttpProxy(builder: OkHttpClient.Builder, proxySettings: ProxySettings): Boolean {
        if (proxySettings.type == Proxy.Type.DIRECT) return true
        if (proxySettings.host.isEmpty() || proxySettings.port !in (1..65535)) return false
        val proxyAddress = InetSocketAddress.createUnresolved(proxySettings.host, proxySettings.port)
        builder.proxy(Proxy(proxySettings.type, proxyAddress))
        if (proxySettings.login.isNotEmpty()) {
            builder.authenticator { _, response ->
                val credential = Credentials.basic(proxySettings.login, proxySettings.password)
                response.request.newBuilder().header("Proxy-Authorization", credential).build()
            }
        }
        return true
    }

    @JvmStatic
    fun enableTls12OnPreLollipop(client: OkHttpClient.Builder): OkHttpClient.Builder = client

    @JvmStatic
    fun hasAnyConnection(context: Context): Boolean {
        val attributedContext = getAttributedContext(context)
        val connManager = attributedContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connManager.getNetworkCapabilities(connManager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @JvmStatic
    fun bottomNavigationEnabled(context: Context): Boolean = true

    @JvmStatic
    fun themeAttributeToColor(themeAttributeId: Int, context: Context, fallbackColorId: Int): Int {
        val outValue = TypedValue()
        return if (context.theme.resolveAttribute(themeAttributeId, outValue, true)) {
            if (outValue.resourceId == 0) outValue.data else ContextCompat.getColor(context, outValue.resourceId)
        } else fallbackColorId
    }

    @JvmStatic
    fun getAccentColor(context: Context): Int = themeAttributeToColor(androidx.appcompat.R.attr.colorAccent, context, Color.LTGRAY)

    @JvmStatic
    fun showSnackbar(view: View, message: String) {
        val snackbar = com.google.android.material.snackbar.Snackbar.make(view, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
        snackbar.setBackgroundTint(getAccentColor(view.context))
        snackbar.setTextColor(Color.WHITE)
        snackbar.show()
    }

    @JvmStatic
    fun showModernToast(activity: Activity, resId: Int) {
        val view = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView
        showSnackbar(view, activity.getString(resId))
    }

    @JvmStatic
    fun resourceToUri(resources: Resources, resID: Int): Uri {
        return (ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                resources.getResourcePackageName(resID) + '/' +
                resources.getResourceTypeName(resID) + '/' +
                resources.getResourceEntryName(resID)).toUri()
    }

    @JvmStatic
    fun getMimeType(url: String, defaultMimeType: String?): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return if (extension != null) MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) else defaultMimeType
    }

    @JvmStatic
    fun createShortcut(ctx: Context, station: DataRadioStation, bitmap: Bitmap?): ShortcutInfo {
        val playByUUIDintent = Intent(MediaSessionCallback.ACTION_PLAY_STATION_BY_UUID, null, ctx, ActivityMain::class.java)
            .putExtra(MediaSessionCallback.EXTRA_STATION_UUID, station.StationUuid)
        val builder = ShortcutInfo.Builder(ctx.applicationContext, ctx.packageName + "/" + station.StationUuid)
            .setShortLabel(station.Name)
            .setIntent(playByUUIDintent)
        bitmap?.let { builder.setIcon(Icon.createWithBitmap(it)) }
        return builder.build()
    }

    /**
     * Reports a click to the official radio-browser.info API to support their click count.
     * Fixed server: de1.api.radio-browser.info
     * Fire & Forget: No response needed, no stability impact on AMARadio.
     */
    @JvmStatic
    fun reportClickToOfficialApi(httpClient: OkHttpClient, stationUuid: String) {
        if (stationUuid.isBlank() || stationUuid == "null") return

        // Throttle check: Prevent duplicate reporting within the same window (e.g., rapid Play/Pause toggle)
        val now = System.currentTimeMillis()
        val lastReportTime = clickThrottleMap[stationUuid] ?: 0L
        if (now - lastReportTime < CLICK_THROTTLE_MS) {
            // Already reported this station recently, skip to avoid API spamming
            return
        }
        clickThrottleMap[stationUuid] = now

        val url = "https://de1.api.radio-browser.info/json/url/$stationUuid"
        if (isDebug) Log.d("CLICK_REPORT", "Sending click to: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        // Async enqueue (OkHttp internal thread pool)
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Ignore, fire and forget
            }
            override fun onResponse(call: Call, response: Response) {
                // Important: Close response body to release resources
                response.close()
            }
        })
    }
}
