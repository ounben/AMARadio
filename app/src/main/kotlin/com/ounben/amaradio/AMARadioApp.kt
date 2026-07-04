package com.ounben.amaradio

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.ounben.amaradio.history.TrackHistoryRepository
import com.ounben.amaradio.proxy.ProxySettings
import com.ounben.amaradio.utils.LocaleUtils
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
    lateinit var reviewManager: com.ounben.amaradio.utils.ReviewManager
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
    private var _httpClient: OkHttpClient? = null
    val httpClient: OkHttpClient 
        get() = _httpClient ?: rebuildHttpClient()

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
        Log.d("APP", "onCreate started")
        try {
            Utils.init(this)

            // Apply selected language on startup
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            val selectedLang = sharedPref.getString("settings_language", "system") ?: "system"
            LocaleUtils.applyLocale(selectedLang)

            connectionPool = ConnectionPool()

            rebuildHttpClient()

            CountryCodeDictionary.instance.load(this)
            CountryFlagsLoader.instance

            historyManager = HistoryManager(this)
            favouriteManager = FavouriteManager(this)
            fallbackStationsManager = FallbackStationsManager(this)
            reviewManager = com.ounben.amaradio.utils.ReviewManager(this)

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
            Log.d("APP", "onCreate finished")
        } catch (e: Exception) {
            Log.e("APP", "onCreate failed", e)
        }
    }

    fun rebuildHttpClient(): OkHttpClient {
        val builder = newHttpClient()
            .connectTimeout(10.seconds)
            .writeTimeout(10.seconds)
            .readTimeout(10.seconds)
            .addInterceptor(UserAgentInterceptor("AMARadio/0.99.3"))

        val client = builder.build()
        _httpClient = client
        return client
    }

    fun newHttpClient(): OkHttpClient.Builder {
        val pool = if (this::connectionPool.isInitialized) connectionPool else ConnectionPool()
        val builder = OkHttpClient.Builder().connectionPool(pool)

        testsInterceptor?.let {
            builder.addInterceptor(it)
        }

        if (!setCurrentOkHttpProxy(builder)) {
            Log.w("APP", "Proxy settings invalid")
        }
        return Utils.enableTls12OnPreLollipop(builder)
    }

    fun newHttpClientWithoutProxy(): OkHttpClient.Builder {
        val pool = if (this::connectionPool.isInitialized) connectionPool else ConnectionPool()
        val builder = OkHttpClient.Builder().connectionPool(pool)

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
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
