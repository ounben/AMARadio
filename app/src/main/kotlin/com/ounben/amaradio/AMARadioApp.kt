package com.ounben.amaradio

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ounben.amaradio.alarm.RadioAlarmManager
import com.ounben.amaradio.history.TrackHistoryRepository
import com.ounben.amaradio.players.mpd.MPDClient
import com.ounben.amaradio.proxy.ProxySettings
import com.ounben.amaradio.station.live.metadata.TrackMetadataSearcher
import com.ounben.amaradio.utils.TvChannelManager
import com.ounben.amaradio.cast.CastHandler
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AMARadioApp : Application(), ImageLoaderFactory {

    lateinit var historyManager: HistoryManager
        private set
    lateinit var favouriteManager: FavouriteManager
        private set
    lateinit var fallbackStationsManager: FallbackStationsManager
        private set
    lateinit var alarmManager: RadioAlarmManager
        private set
    var tvChannelManager: TvChannelManager? = null
        private set

    lateinit var trackHistoryRepository: TrackHistoryRepository
        private set

    lateinit var mpdClient: MPDClient
        private set

    lateinit var castHandler: CastHandler
        private set

    lateinit var trackMetadataSearcher: TrackMetadataSearcher
        private set

    private lateinit var connectionPool: ConnectionPool
    lateinit var httpClient: OkHttpClient
        private set

    private var testsInterceptor: Interceptor? = null

    class UserAgentInterceptor(private val userAgent: String) : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", userAgent)
                .build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    companion object {
        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Utils.init(this)

        connectionPool = ConnectionPool()

        rebuildHttpClient()

        CountryCodeDictionary.instance.load(this)
        CountryFlagsLoader.instance

        historyManager = HistoryManager(this)
        favouriteManager = FavouriteManager(this)
        fallbackStationsManager = FallbackStationsManager(this)
        alarmManager = RadioAlarmManager(this)

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            tvChannelManager = TvChannelManager(this)
        }

        trackHistoryRepository = TrackHistoryRepository(this)
        mpdClient = MPDClient(this)
        
        // Initialisierung des Cast-Handlers
        castHandler = CastHandler(this)

        trackMetadataSearcher = TrackMetadataSearcher(httpClient)
    }

    fun setTestsInterceptor(testsInterceptor: Interceptor?) {
        this.testsInterceptor = testsInterceptor
    }

    fun rebuildHttpClient() {
        val builder = newHttpClient()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor("AMARadio/" + resources.getString(R.string.version_name)))

        httpClient = builder.build()
    }

    fun newHttpClient(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder().connectionPool(connectionPool)

        testsInterceptor?.let {
            builder.addInterceptor(it)
        }

        if (!setCurrentOkHttpProxy(builder)) {
            Toast.makeText(this, resources.getString(R.string.ignore_proxy_settings_invalid), Toast.LENGTH_SHORT).show()
        }
        return Utils.enableTls12OnPreLollipop(builder)
    }

    fun newHttpClientWithoutProxy(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder().connectionPool(connectionPool)

        testsInterceptor?.let {
            builder.addInterceptor(it)
        }

        return Utils.enableTls12OnPreLollipop(builder)
    }

    fun setCurrentOkHttpProxy(builder: OkHttpClient.Builder): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val proxySettings = ProxySettings.fromPreferences(sharedPref)
        if (proxySettings != null) {
            if (!Utils.setOkHttpProxy(builder, proxySettings)) {
                return false
            }
        }
        return true
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { httpClient }
            .build()
    }
}
