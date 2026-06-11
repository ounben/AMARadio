package net.ounben.AMARadio

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import net.ounben.AMARadio.alarm.RadioAlarmManager
import net.ounben.AMARadio.history.TrackHistoryRepository
import net.ounben.AMARadio.players.mpd.MPDClient
import net.ounben.AMARadio.proxy.ProxySettings
import net.ounben.AMARadio.recording.RecordingsManager
import net.ounben.AMARadio.station.live.metadata.TrackMetadataSearcher
import net.ounben.AMARadio.utils.TvChannelManager
import net.ounben.AMARadio.cast.CastHandler
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
    lateinit var recordingsManager: RecordingsManager
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

    inner class UserAgentInterceptor(private val userAgent: String) : Interceptor {
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

        connectionPool = ConnectionPool()

        rebuildHttpClient()

        CountryCodeDictionary.instance.load(this)
        CountryFlagsLoader.instance

        historyManager = HistoryManager(this)
        favouriteManager = FavouriteManager(this)
        fallbackStationsManager = FallbackStationsManager(this)
        recordingsManager = RecordingsManager()
        alarmManager = RadioAlarmManager(this)

        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            tvChannelManager = TvChannelManager(this)
            favouriteManager.addObserver(tvChannelManager)
        }

        trackHistoryRepository = TrackHistoryRepository(this)
        mpdClient = MPDClient(this)
        
        // Initialisierung des Cast-Handlers
        castHandler = CastHandler(this)

        trackMetadataSearcher = TrackMetadataSearcher(httpClient)
        recordingsManager.updateRecordingsList()
    }

    fun setTestsInterceptor(testsInterceptor: Interceptor?) {
        this.testsInterceptor = testsInterceptor
    }

    fun rebuildHttpClient() {
        val builder = newHttpClient()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor("AMARadio/" + BuildConfig.VERSION_NAME))

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
