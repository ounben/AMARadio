package com.ounben.amaradio

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.players.selector.PlayerSelectorDialog
import com.ounben.amaradio.players.selector.PlayerType
import com.ounben.amaradio.proxy.ProxySettings
import com.ounben.amaradio.service.ConnectivityChecker
import com.ounben.amaradio.service.MediaSessionCallback
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

object Utils {
    private var loadIcons = -1
    private val testing = AtomicBoolean(false)
    var isDebug = false
        private set

    @JvmStatic
    fun init(context: Context) {
        isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    @JvmStatic
    fun isTesting(): Boolean {
        return testing.get()
    }

    @JvmStatic
    fun setTesting(value: Boolean) {
        testing.set(value)
    }

    @JvmStatic
    fun parseIntWithDefault(number: String?, defaultVal: Int): Int {
        return try {
            number?.toInt() ?: defaultVal
        } catch (e: NumberFormatException) {
            defaultVal
        }
    }

    @JvmStatic
    fun getCacheFile(ctx: Context, theURI: String): String? {
        val chaine = StringBuilder("")
        try {
            var aFileName = theURI.lowercase(Locale.ROOT).replace("http://", "")
            aFileName = aFileName.replace("https://", "")
            aFileName = sanitizeName(aFileName)

            val file = File(ctx.cacheDir.absolutePath + "/" + aFileName)
            if (!file.exists()) return null
            
            val lastModDate = Date(file.lastModified())
            val now = Date()
            val millis = now.time - file.lastModified()
            val secs = millis / 1000
            val mins = secs / 60
            val hours = mins / 60

            if (isDebug) {
                Log.d("UTIL", "File last modified : $lastModDate secs=$secs  mins=$mins hours=$hours")
            }

            if (hours < 1) {
                val aStream = FileInputStream(file)
                val rd = BufferedReader(InputStreamReader(aStream))
                var line: String?
                while (rd.readLine().also { line = it } != null) {
                    chaine.append(line)
                }
                rd.close()
                if (isDebug) {
                    Log.d("UTIL", "used cache for:$theURI")
                }
                return chaine.toString()
            }
            if (isDebug) {
                Log.d("UTIL", "do not use cache, because too old:$theURI")
            }
            return null
        } catch (e: Exception) {
            Log.e("UTIL", "getCacheFile() $e")
        }
        return null
    }

    @JvmStatic
    fun writeFileCache(ctx: Context, theURI: String, content: String) {
        try {
            var aFileName = theURI.lowercase(Locale.ROOT).replace("http://", "")
            aFileName = aFileName.replace("https://", "")
            aFileName = sanitizeName(aFileName)

            val f = File(ctx.cacheDir.toString() + "/" + aFileName)
            val aStream = FileOutputStream(f)
            aStream.write(content.toByteArray(charset("utf-8")))
            aStream.close()
        } catch (e: Exception) {
            Log.e("UTIL", "writeFileCache() could not write to cache file for:$theURI")
        }
    }

    private fun downloadFeed(httpClient: OkHttpClient, ctx: Context, theURI: String, forceUpdate: Boolean, dictParams: Map<String, String>?, timeoutMs: Long? = null): String? {
        Log.i("DOWN", "Url=$theURI")
        if (!forceUpdate) {
            val cache = getCacheFile(ctx, theURI)
            if (cache != null) {
                return cache
            }
        }
        Log.i("DOWN", "Url=$theURI (not cached)")

        try {
            val urlBuilder = theURI.toHttpUrlOrNull()?.newBuilder() ?: return null
            
            if (dictParams != null) {
                for ((key, value) in dictParams) {
                    urlBuilder.addQueryParameter(key, value)
                }
            }
            
            val url = urlBuilder.build()
            Log.i("DOWN", "Final Url=$url")
            
            val client = if (timeoutMs != null) {
                httpClient.newBuilder()
                    .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()
            } else {
                httpClient
            }

            val request = Request.Builder().url(url).get().build()
            
            val response = client.newCall(request).execute()
            val responseStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("UTIL", "Unsuccessful response: ${response.code} ${response.message}\n$responseStr")
                return null
            }

            writeFileCache(ctx, theURI, responseStr)
            if (isDebug) {
                Log.d("UTIL", "wrote cache file for:$theURI")
            }
            return responseStr
        } catch (e: Exception) {
            Log.e("UTIL", "downloadFeed() $e")
        }
        return null
    }

    @JvmStatic
    fun downloadFeedRelative(httpClient: OkHttpClient, ctx: Context, theRelativeUri: String, forceUpdate: Boolean, dictParams: Map<String, String>?): String? {
        // Step 1: Try official radio-browser.info servers with strict 2s timeout
        val currentServer = RadioBrowserServerManager.getCurrentServer()
        if (currentServer != null) {
            val endpoint = RadioBrowserServerManager.constructEndpoint(currentServer, theRelativeUri)
            val result = downloadFeed(httpClient, ctx, endpoint, forceUpdate, dictParams, 2000L)
            if (result != null) return result
            
            // If current server failed, try rotating through the list once with strict timeout
            val serverList = RadioBrowserServerManager.getServerList(false)
            for (newServer in serverList) {
                if (newServer == currentServer) continue
                val rotatedEndpoint = RadioBrowserServerManager.constructEndpoint(newServer, theRelativeUri)
                val rotatedResult = downloadFeed(httpClient, ctx, rotatedEndpoint, forceUpdate, dictParams, 2000L)
                if (rotatedResult != null) {
                    RadioBrowserServerManager.setCurrentServer(newServer)
                    return rotatedResult
                }
            }
        }

        // Step 2: Fail-over to Mirror Mirror radiobrowser.ounben.com with standard timeout
        Log.w("UTIL", "Official servers failed or timed out, trying mirror fallback...")
        val mirrorEndpoint = RadioBrowserServerManager.constructEndpoint(RadioBrowserServerManager.getMirrorServer(), theRelativeUri)
        return downloadFeed(httpClient, ctx, mirrorEndpoint, forceUpdate, dictParams)
    }

    @JvmStatic
    fun getRealStationLink(httpClient: OkHttpClient, ctx: Context, stationId: String): String? {
        Log.i("UTIL", "StationUUID:$stationId")
        val result = downloadFeedRelative(httpClient, ctx, "json/url/$stationId", true, null)
        if (result != null) {
            Log.i("UTIL", result)
            return try {
                val jsonObj = JSONObject(result)
                jsonObj.getString("url")
            } catch (e: Exception) {
                Log.e("UTIL", "getRealStationLink() $e")
                null
            }
        }
        return null
    }

    @JvmStatic
    fun getStationByUuid(httpClient: OkHttpClient, ctx: Context, stationUuid: String): DataRadioStation? {
        Log.w("UTIL", "Search by uuid:$stationUuid")
        val result = downloadFeedRelative(httpClient, ctx, "json/stations/byuuid/$stationUuid", true, null)
        if (result != null) {
            try {
                val list = DataRadioStation.DecodeJson(result)
                if (list != null) {
                    if (list.size == 1) {
                        return list[0]
                    }
                    Log.e("UTIL", "stations by uuid did have length:" + list.size)
                }
            } catch (e: Exception) {
                Log.e("UTIL", "getStationByUuid() $e")
            }
        }
        return null
    }

    @JvmStatic
    fun getStationsByUuid(httpClient: OkHttpClient, ctx: Context, listUUids: Iterable<String>): List<DataRadioStation>? {
        val uuids = TextUtils.join(",", listUUids)
        Log.d("UTIL", "Search by uuid for items")
        val p = HashMap<String, String>()
        p["uuids"] = uuids
        val result = downloadFeedRelative(httpClient, ctx, "json/stations/byuuid", true, p)
        if (result != null) {
            try {
                val list = DataRadioStation.DecodeJson(result)
                if (list != null) {
                    return list
                } else {
                    Log.e("UTIL", "stations by uuid was null")
                }
            } catch (e: Exception) {
                Log.e("UTIL", "getStationsByUuid() $e")
            }
        }
        return null
    }

    @JvmStatic
    fun getCurrentOrLastStation(ctx: Context): DataRadioStation? {
        var station = PlayerServiceUtil.getCurrentStation()
        if (station == null) {
            val AMARadioApp = ctx.applicationContext as AMARadioApp
            val historyManager = AMARadioApp.historyManager
            station = historyManager.first
        }
        return station
    }

    @JvmStatic
    fun showMpdServersDialog(context: Context, fragmentManager: FragmentManager, station: DataRadioStation?) {
        val AMARadioApp = context.applicationContext as AMARadioApp
        val oldFragment = fragmentManager.findFragmentByTag(PlayerSelectorDialog.FRAGMENT_TAG)
        if (oldFragment != null && oldFragment.isVisible) {
            return
        }
        if (station == null) return
        val playerSelectorDialogFragment = PlayerSelectorDialog(AMARadioApp.mpdClient, station)
        playerSelectorDialogFragment.show(fragmentManager, PlayerSelectorDialog.FRAGMENT_TAG)
    }

    @JvmStatic
    fun showPlaySelection(context: Context, station: DataRadioStation, fragmentManager: FragmentManager) {
        val AMARadioApp = context.applicationContext as AMARadioApp
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val externalAvailable = sharedPref.getBoolean("play_external", false)
        val castHandler = AMARadioApp.castHandler
        val castAvailable = castHandler.isCastSessionAvailable
        val mpdAvailable = AMARadioApp.mpdClient.isMpdEnabled

        if (castAvailable && !externalAvailable && !mpdAvailable) {
            PlayStationTask(station, context.applicationContext,
                { url -> castHandler.playRemote(station.Name, url, station.IconUrl) },
                null)
                .execute()
        } else if (externalAvailable || mpdAvailable) {
            showMpdServersDialog(context, fragmentManager, station)
        } else {
            playAndWarnIfMetered(context, station, PlayerType.AMARadio, Runnable { play(context, station) })
        }
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
        val warnOnMetered = sharedPref.getBoolean("warn_no_wifi", false)
        if (warnOnMetered && ConnectivityChecker.getCurrentConnectionType(context) == ConnectivityChecker.ConnectionType.METERED) {
            warningCallback.warn(station, playerType)
        } else {
            playFunc.run()
        }
    }

    @JvmStatic
    fun play(context: Context, station: DataRadioStation) {
        PlayerServiceUtil.play(station)
    }

    @JvmStatic
    fun shouldLoadIcons(context: Context): Boolean {
        when (loadIcons) {
            -1 -> return if (PreferenceManager.getDefaultSharedPreferences(context.applicationContext).getBoolean("load_icons", false)) {
                loadIcons = 1
                true
            } else {
                loadIcons = 0
                true
            }
            0 -> return false
            1 -> return true
        }
        return false
    }

    @JvmStatic
    fun getTheme(context: Context): String {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPref.getString("theme_name", "system") ?: "system"
    }

    @JvmStatic
    fun getThemeResId(context: Context): Int {
        val selectedTheme = getTheme(context)

        val isDark = when (selectedTheme) {
            "dark" -> true
            "light" -> false
            "system" -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            // Backward compatibility for translated strings
            context.resources.getString(R.string.theme_dark) -> true
            context.resources.getString(R.string.theme_light) -> false
            else -> false
        }

        return if (isDark) R.style.MyMaterialTheme_Dark else R.style.MyMaterialTheme
    }

    @JvmStatic
    fun isDarkTheme(context: Context): Boolean {
        return getThemeResId(context) == R.style.MyMaterialTheme_Dark
    }

    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @JvmStatic
    fun verifyStoragePermissions(activity: Activity, request_id: Int): Boolean {
        val permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                PERMISSIONS_STORAGE,
                request_id
            )
            return false
        }
        return true
    }

    @JvmStatic
    fun verifyStoragePermissions(fragment: Fragment, request_id: Int): Boolean {
        val permission = ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            fragment.requestPermissions(PERMISSIONS_STORAGE, request_id)
            return false
        }
        return true
    }

    @JvmStatic
    fun getReadableBytes(bytesIn: Double): String {
        var bytes = bytesIn
        val str = arrayOf("B", "KB", "MB", "GB", "TB")
        for (aStr in str) {
            if (bytes < 1024) {
                return String.format(Locale.getDefault(), "%1$,.1f %2\$s", bytes, aStr)
            }
            bytes /= 1024.0
        }
        return String.format(Locale.getDefault(), "%1$,.1f %2\$s", bytes * 1024, str[str.size - 1])
    }

    @JvmStatic
    fun sanitizeName(str: String): String {
        return str.replace("\\W+".toRegex(), "_").replace("^_+".toRegex(), "").replace("_+$".toRegex(), "")
    }

    @JvmStatic
    fun hasWifiConnection(context: Context): Boolean {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        return mWifi?.isConnected ?: false
    }

    @JvmStatic
    fun hasAnyConnection(context: Context): Boolean {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = connManager.activeNetworkInfo
        return netInfo != null && netInfo.isConnected
    }

    @JvmStatic
    fun bottomNavigationEnabled(context: Context): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPref.getBoolean("bottom_navigation", true)
    }

    @JvmStatic
    fun formatStringWithNamedArgs(format: String, args: Map<String, String>): String {
        val builder = StringBuilder(format)
        for ((key1, value) in args) {
            val key = "\${$key1}"
            var startIdx = 0
            while (true) {
                val keyIdx = builder.indexOf(key, startIdx)
                if (keyIdx == -1) {
                    break
                }
                builder.replace(keyIdx, keyIdx + key.length, value)
                startIdx = keyIdx + value.length
            }
        }
        return builder.toString()
    }

    @JvmStatic
    fun themeAttributeToColor(themeAttributeId: Int, context: Context, fallbackColorId: Int): Int {
        val outValue = TypedValue()
        val theme = context.theme
        val wasResolved = theme.resolveAttribute(themeAttributeId, outValue, true)
        return if (wasResolved) {
            if (outValue.resourceId == 0) outValue.data else ContextCompat.getColor(context, outValue.resourceId)
        } else {
            fallbackColorId
        }
    }

    @JvmStatic
    fun getAccentColor(context: Context): Int {
        return themeAttributeToColor(androidx.appcompat.R.attr.colorAccent, context, Color.LTGRAY)
    }

    @JvmStatic
    fun showSnackbar(view: View, message: String) {
        val snackbar = com.google.android.material.snackbar.Snackbar.make(view, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
        snackbar.setBackgroundTint(getAccentColor(view.context))
        snackbar.setTextColor(Color.WHITE)
        snackbar.show()
    }

    @JvmStatic
    fun showSnackbar(view: View, resId: Int) {
        showSnackbar(view, view.context.getString(resId))
    }

    @JvmStatic
    fun showModernToast(activity: Activity, resId: Int) {
        val view = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView
        showSnackbar(view, resId)
    }

    @JvmStatic
    fun setOkHttpProxy(builder: OkHttpClient.Builder, proxySettings: ProxySettings): Boolean {
        if (proxySettings.type == Proxy.Type.DIRECT) {
            return true
        }
        if (TextUtils.isEmpty(proxySettings.host)) {
            return false
        }
        if (proxySettings.port < 1 || proxySettings.port > 65535) {
            return false
        }
        val proxyAddress = InetSocketAddress.createUnresolved(proxySettings.host, proxySettings.port)
        val proxy = Proxy(proxySettings.type, proxyAddress)
        builder.proxy(proxy)
        if (proxySettings.login.isNotEmpty()) {
            val proxyAuthenticator = Authenticator { _, response ->
                val credential = Credentials.basic(proxySettings.login, proxySettings.password)
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
            builder.authenticator(proxyAuthenticator)
        }
        return true
    }

    @JvmStatic
    fun resourceToUri(resources: Resources, resID: Int): Uri {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                resources.getResourcePackageName(resID) + '/' +
                resources.getResourceTypeName(resID) + '/' +
                resources.getResourceEntryName(resID))
    }

    @JvmStatic
    fun getMimeType(url: String, defaultMimeType: String?): String? {
        var type = defaultMimeType
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
    }

    @JvmStatic
    fun enableTls12OnPreLollipop(client: OkHttpClient.Builder): OkHttpClient.Builder {
        return client
    }

    @JvmStatic
    @RequiresApi(25)
    fun createShortcut(ctx: Context, station: DataRadioStation, bitmap: Bitmap?): ShortcutInfo {
        val playByUUIDintent = Intent(MediaSessionCallback.ACTION_PLAY_STATION_BY_UUID, null, ctx, ActivityMain::class.java)
            .putExtra(MediaSessionCallback.EXTRA_STATION_UUID, station.StationUuid)
        
        val builder = ShortcutInfo.Builder(ctx.applicationContext, ctx.packageName + "/" + station.StationUuid)
            .setShortLabel(station.Name)
            .setIntent(playByUUIDintent)
        
        bitmap?.let {
            builder.setIcon(Icon.createWithBitmap(it))
        }
        
        return builder.build()
    }

    @JvmStatic
    fun setupStationRecyclerView(context: Context, rv: RecyclerView, adapter: RecyclerView.Adapter<*>) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        
        while (rv.itemDecorationCount > 0) {
            rv.removeItemDecorationAt(0)
        }

        if (isGrid) {
            val columnCount = com.ounben.amaradio.utils.UiScaler.getGridColumnCount(context)
            rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, columnCount)
        } else {
            val llm = androidx.recyclerview.widget.LinearLayoutManager(context)
            rv.layoutManager = llm
            val dividerItemDecoration = androidx.recyclerview.widget.DividerItemDecoration(context, llm.orientation)
            rv.addItemDecoration(dividerItemDecoration)
        }
        rv.adapter = adapter
    }

    @JvmStatic
    fun createStationAdapter(activity: androidx.fragment.app.FragmentActivity, filterType: com.ounben.amaradio.station.StationsFilter.FilterType): com.ounben.amaradio.station.ItemAdapterStation {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        
        return if (isGrid) {
            com.ounben.amaradio.station.ItemAdapterIconOnlyStation(activity, R.layout.list_item_icon_only_station, filterType)
        } else {
            com.ounben.amaradio.station.ItemAdapterStation(activity, R.layout.list_item_station, filterType)
        }
    }
}
