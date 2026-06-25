package com.ounben.amaradio

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Handler
import android.os.HandlerThread
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ounben.amaradio.history.TrackHistoryRepository
import com.ounben.amaradio.proxy.ProxySettings
import com.ounben.amaradio.utils.TvChannelManager
import kotlinx.coroutines.android.asCoroutineDispatcher
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class AMARadioApp : Application(), ImageLoaderFactory {

    lateinit var historyManager: HistoryManager
        private set
    lateinit var favouriteManager: FavouriteManager
        private set
    lateinit var fallbackStationsManager: FallbackStationsManager
        private set
    var tvChannelManager: TvChannelManager? = null
        private set

    lateinit var trackHistoryRepository: TrackHistoryRepository
        private set

    lateinit var audioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        private set
    
    lateinit var audioLooper: android.os.Looper
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

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            tvChannelManager = TvChannelManager(this)
        }

        trackHistoryRepository = TrackHistoryRepository(this)

        val audioThread = HandlerThread("AudioThread", android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        audioThread.start()
        val looper = audioThread.looper
        audioLooper = looper
        audioDispatcher = Handler(looper).asCoroutineDispatcher("AudioThread")
    }

    fun rebuildHttpClient() {
        val builder = newHttpClient()
            .connectTimeout(10.seconds)
            .writeTimeout(10.seconds)
            .readTimeout(10.seconds)
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
