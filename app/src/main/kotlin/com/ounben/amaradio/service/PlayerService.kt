package com.ounben.amaradio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.audiofx.AudioEffect
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.IPlayerService
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.history.TrackHistoryRepository
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.players.RadioPlayer
import com.ounben.amaradio.players.selector.PlayerType
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import com.ounben.amaradio.utils.StationPlaceholderUtils
import com.ounben.amaradio.widget.WidgetUpdateHelper
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.Date
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerService : MediaLibraryService(), RadioPlayer.PlayerListener {
    private val tag = "PLAY"
    private var sharedPref: SharedPreferences? = null
    private var trackHistoryRepository: TrackHistoryRepository? = null
    private var itsContext: Context? = null
    private var handler: Handler? = null
    internal var itsCurrentStation: DataRadioStation? = null
    @Volatile
    private var currentStationBitmap: Bitmap? = null
    private var radioPlayer: RadioPlayer? = null
    private var currentForwardingPlayer: Player? = null
    private var audioManager: AudioManager? = null
    private var mediaSession: MediaLibrarySession? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ((AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) && (radioPlayer?.isPlaying() == true)) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                if (prefs.getBoolean("pause_when_noisy", true)) {
                    pause(PauseReason.BECAME_NOISY)
                }
            }
        }
    }
    private val headsetConnectionReceiver = HeadsetConnectionReceiver()
    private val connectivityChecker = ConnectivityChecker()
    private var pauseReason = PauseReason.NONE
    private var lastErrorFromPlayer = -1
    private var lastMeteredConnectionWarningTime: Long = 0
    private var toneGenerator: ToneGenerator? = null
    private var toneGeneratorStopRunnable: Runnable? = null
    private var timer: CountDownTimer? = null
    private var seconds: Long = 0
    private var audioFocusRequest: AudioFocusRequest? = null
    private var liveInfo = StreamLiveInfo(null)
    private var streamInfo: ShoutcastInfo? = null
    private var isHls = false
    private var lastPlayStartTime: Long = 0
    private var notificationIsActive = false
    private var isTransitioning = false
    private var lastNotificationUpdateTime: Long = 0
    private val NOTIFICATION_THROTTLE_MS = 1200L // Prevent system shedding (limit is ~5/sec)
    private val pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE
    internal lateinit var amaradioBrowser: AMARadioBrowser
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun launchInServiceScope(block: suspend CoroutineScope.() -> Unit) {
        serviceScope.launch(block = block)
    }

    private fun sendBroadCast(action: String) {
        val local = Intent()
        local.action = action
        AppEventManager.sendEvent(local)
    }

    private val itsBinder: IPlayerService.Stub = object : IPlayerService.Stub() {
        override fun SetStation(station: DataRadioStation) {
            // One-Click Fix: Sofortige Wiedergabe inklusive Warnung bei mobilen Daten
            this@PlayerService.playAndWarnIfMetered(station)
        }
        override fun SkipToNext() { this@PlayerService.next() }
        override fun SkipToPrevious() { this@PlayerService.previous() }
        override fun Play() { this@PlayerService.playCurrentStation() }
        override fun Pause(reason: PauseReason) { this@PlayerService.pause(reason) }
        override fun Resume() { this@PlayerService.resume() }
        override fun Stop() { this@PlayerService.stop() }
        override fun addTimer(secondsAdd: Int) { this@PlayerService.addTimer(secondsAdd) }
        override fun clearTimer() { this@PlayerService.clearTimer() }
        override fun getTimerSeconds(): Long = this@PlayerService.getTimerSeconds()
        override fun getCurrentStationID(): String? = this@PlayerService.itsCurrentStation?.StationUuid
        override fun getCurrentStation(): DataRadioStation? = this@PlayerService.itsCurrentStation
        override fun getMetadataLive(): StreamLiveInfo = this@PlayerService.liveInfo
        override fun getShoutcastInfo(): ShoutcastInfo? = this@PlayerService.streamInfo
        override fun getIsHls(): Boolean = this@PlayerService.isHls
        override fun isPlaying(): Boolean = this@PlayerService.radioPlayer?.isPlaying() ?: false
        override fun getPlayerState(): PlayState = this@PlayerService.radioPlayer?.playState ?: PlayState.Idle
        override fun getTransferredBytes(): Long = this@PlayerService.radioPlayer?.currentPlaybackTransferredBytes ?: 0
        override fun getBufferedSeconds(): Long = this@PlayerService.radioPlayer?.bufferedSeconds ?: 0
        override fun getLastPlayStartTime(): Long = this@PlayerService.lastPlayStartTime
        override fun getPauseReason(): PauseReason = this@PlayerService.pauseReason
        override fun warnAboutMeteredConnection(playerType: PlayerType) { this@PlayerService.warnAboutMeteredConnection(playerType) }
        override fun isNotificationActive(): Boolean = this@PlayerService.notificationIsActive
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    /**
     * Override onUpdateNotification to prevent Media3 from showing its own default notification.
     * This allows us to maintain our custom notification logic while still being a MediaLibraryService.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val logicalState = if (pauseReason == PauseReason.USER) PlayState.Paused else (radioPlayer?.playState ?: PlayState.Idle)
        updateNotification(logicalState)
    }

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (radioPlayer?.isLocal == false) return@OnAudioFocusChangeListener
        Log.d(tag, "afChangeListener: focusChange=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(tag, "audio focus gain")
                if (pauseReason == PauseReason.FOCUS_LOSS_TRANSIENT) {
                    resume()
                }
                radioPlayer?.setVolume(FULL_VOLUME)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(tag, "audio focus loss")
                pause(PauseReason.FOCUS_LOSS)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(tag, "audio focus loss transient")
                pause(PauseReason.FOCUS_LOSS_TRANSIENT)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(tag, "audio focus loss transient can duck")
                radioPlayer?.setVolume(DUCK_VOLUME)
            }
        }
    }

    private val connectivityCallback = ConnectivityChecker.ConnectivityCallback { _, connectionType ->
        if ((connectionType == ConnectivityChecker.ConnectionType.METERED) && (sharedPref!!.getBoolean(METERED_CONNECTION_WARNING_KEY, false))) {
            warnAboutMeteredConnection(PlayerType.AMARadio)
        }
    }

    private fun getTimerSeconds(): Long = seconds

    private fun clearTimer() {
        timer?.cancel()
        timer = null
        seconds = 0
        sendBroadCast(PLAYER_SERVICE_TIMER_UPDATE)
    }

    private fun addTimer(secondsAdd: Int) {
        timer?.cancel()
        seconds += secondsAdd.toLong()
        timer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                seconds = millisUntilFinished / 1000
                if (Utils.isDebug) Log.d(tag, seconds.toString())
                sendBroadCast(PLAYER_SERVICE_TIMER_UPDATE)
            }
            override fun onFinish() {
                stop()
                timer = null
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder {
        val binder = super.onBind(intent)
        return binder ?: itsBinder
    }

    override fun onCreate() {
        super.onCreate()
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        handler = Handler(mainLooper)
        
        itsContext = this

        powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        
        // 1. Initialize RadioPlayer
        radioPlayer = RadioPlayer(this).apply { setPlayerListener(this@PlayerService) }

        amaradioBrowser = AMARadioBrowser(application as? AMARadioApp ?: (applicationContext as AMARadioApp))

        // 2. Setup Session Activity
        val startActivityIntent = Intent(this, ActivityMain::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(this, 0, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag)

        // 3. Create MediaSession
        // We use the player's current internal player as the initial target.
        // It will be updated via session.setPlayer() in onPlayerCreated once the ForwardingPlayer is ready.
        val initialPlayer = currentForwardingPlayer ?: radioPlayer?.player!!
        mediaSession = MediaLibrarySession.Builder(this, initialPlayer, MediaSessionCallback(this, amaradioBrowser))
            .setSessionActivity(sessionActivityPendingIntent)
            .setSessionExtras(Bundle().apply { putString("android.media.metadata.ATTRIBUTION_TAG", "player_service") })
            .build()
        
        // 4. Load initial station to ensure non-empty state
        val app = application as AMARadioApp
        val initialStation = app.historyManager.first ?: app.favouriteManager.first
        if (initialStation != null) {
            setStation(initialStation)
        } else {
            // SQL Fallback: Load top station from local region if history is empty
            serviceScope.launch(Dispatchers.IO) {
                val countryCode = com.ounben.amaradio.Utils.getCountryCode(this@PlayerService) ?: "DE"
                val db = com.ounben.amaradio.database.AMARadioDatabase.getDatabase(this@PlayerService)
                val topStation = db.stationDao().getStationsByCountryCode(countryCode.uppercase()).firstOrNull()
                
                topStation?.let { entity ->
                    withContext(Dispatchers.Main) {
                        setStation(entity.toDataStation())
                    }
                }
            }
        }

        trackHistoryRepository = (application as AMARadioApp).trackHistoryRepository
        val headsetConnectionFilter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(headsetConnectionReceiver, headsetConnectionFilter)
        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        serviceScope.launch {
            AppEventManager.events.collect { intent ->
                if (intent.action == MediaSessionCallback.BROADCAST_PLAY_STATION_BY_ID) {
                    val stationId = intent.getStringExtra(MediaSessionCallback.EXTRA_STATION_ID)
                    if (stationId != null) {
                        val station = amaradioBrowser.getStationById(stationId)
                                    ?: (application as AMARadioApp).favouriteManager.getById(stationId)
                                    ?: (application as AMARadioApp).historyManager.getById(stationId)
                        
                        station?.let { playWithoutWarnings(it) }
                    }
                } else if (intent.action == "com.ounben.amaradio.TOGGLE_PLAY_PAUSE") {
                    if (radioPlayer?.isPlaying() == true) pause(PauseReason.USER) else resume()
                }
            }
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "AMARadio Player", NotificationManager.IMPORTANCE_LOW)
        channel.description = "AMARadio Playback Controls"
        channel.enableLights(false)
        channel.enableVibration(false)
        notificationManager.createNotificationChannel(channel)

        // 5. Observe History & Favorites for Media3 Library updates (Android Auto & Widget Sync)
        serviceScope.launch {
            val app = application as AMARadioApp
            
            launch {
                // Whenever history changes on smartphone, notify Android Auto & Widgets
                app.historyManager.stationsFlow.collect { list ->
                    // Small delay to ensure DB transaction is committed and settled
                    delay(500)
                    Log.d("PLAYER_SERVICE", "History changed, notifying Auto & Widgets.")
                    
                    mediaSession?.let { session ->
                        // Using Int.MAX_VALUE forces Android Auto to ignore its local item cache and re-fetch everything
                        session.notifyChildrenChanged(AMARadioBrowser.MEDIA_ID_MUSICS_HISTORY, Int.MAX_VALUE, null)
                        session.notifyChildrenChanged(AMARadioBrowser.MEDIA_ID_ROOT, Int.MAX_VALUE, null)
                    }
                    
                    WidgetUpdateHelper.updateAllWidgets(this@PlayerService, itsCurrentStation, radioPlayer?.isPlaying() ?: false)
                }
            }
            
            launch {
                // Whenever favorites change, notify Android Auto & Widgets
                app.favouriteManager.stationsFlow.collect { list ->
                    delay(500)
                    mediaSession?.let { session ->
                        session.notifyChildrenChanged(AMARadioBrowser.MEDIA_ID_MUSICS_FAVORITE, Int.MAX_VALUE, null)
                        session.notifyChildrenChanged(AMARadioBrowser.MEDIA_ID_ROOT, Int.MAX_VALUE, null)
                    }
                    WidgetUpdateHelper.updateAllWidgets(this@PlayerService, itsCurrentStation, radioPlayer?.isPlaying() ?: false)
                }
            }
        }
    }

    override fun onDestroy() {
        if (Utils.isDebug) Log.d(tag, "PlayService should be destroyed.")
        serviceScope.cancel()
        stop()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        radioPlayer?.destroy()
        unregisterReceiver(headsetConnectionReceiver)
        unregisterReceiver(becomingNoisyReceiver)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        PlayerServiceUtil.bindService(applicationContext)
        if (itsCurrentStation == null) {
            val app = application as AMARadioApp
            itsCurrentStation = app.historyManager.first ?: app.favouriteManager.first
        }
        
        intent?.let {
            it.action?.let { action ->
                when (action) {
                    ACTION_SKIP_TO_PREVIOUS -> previous()
                    ACTION_SKIP_TO_NEXT -> next()
                    ACTION_STOP -> { stop(); return START_NOT_STICKY }
                    ACTION_PAUSE -> pause(PauseReason.USER)
                    ACTION_RESUME -> resume()
                    Intent.ACTION_MEDIA_BUTTON -> {
                        val key = IntentCompat.getParcelableExtra(it, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        if (key?.action == KeyEvent.ACTION_UP) {
                            when (key.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PLAY -> resume()
                                KeyEvent.KEYCODE_MEDIA_NEXT -> next()
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> previous()
                            }
                        }
                    }
                }
            }
        }

        if ((itsCurrentStation != null) && (radioPlayer?.playState != PlayState.Idle)) {
            updateNotification(radioPlayer?.playState ?: PlayState.Paused)
        }

        return START_STICKY
    }

    private fun playWithoutWarnings(station: DataRadioStation) {
        setStation(station)
        playCurrentStation()
    }

    private fun playAndWarnIfMetered(station: DataRadioStation) {
        Utils.playAndWarnIfMetered(
            application as AMARadioApp,
            station,
            PlayerType.AMARadio,
            { playWithoutWarnings(station) },
            object : Utils.MeteredWarningCallback {
                override fun warn(station: DataRadioStation, playerType: PlayerType) {
                    setStation(station)
                    warnAboutMeteredConnection(playerType)
                }
            },
        )
    }

    fun setStation(station: DataRadioStation) {
        val app = application as AMARadioApp
        val fullStation = app.favouriteManager.getById(station.StationUuid) 
                        ?: app.historyManager.getById(station.StationUuid)
        
        val targetStation = fullStation ?: station
        this.itsCurrentStation = targetStation
        this.lastPlayStartTime = 0 // Reset time basis for new station to prevent AA sync issues

        // Prepare full metadata in background
        serviceScope.launch {
            val bitmap = fetchStationBitmap(targetStation)
            radioPlayer?.runInPlayerThread {
                currentStationBitmap = bitmap
                val metadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(targetStation, targetStation.Name, bitmap)
                
                // 1. Update Player Playlist Metadata
                radioPlayer?.player?.let { player ->
                    player.playlistMetadata = metadata
                    
                    // 2. Update the MediaItem's metadata (Critical for Android Auto)
                    val itemIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                    if (player.mediaItemCount > itemIndex) {
                        val currentItem = player.getMediaItemAt(itemIndex)
                        val updatedItem = currentItem.buildUpon()
                            .setMediaMetadata(metadata)
                            .build()
                        player.replaceMediaItem(itemIndex, updatedItem)
                    }
                }
                
                // 3. Ensure MediaSession is aware of the changes
                mediaSession?.setCustomLayout(listOf())
                updateNotification(if (radioPlayer?.isPlaying() == true) PlayState.Playing else PlayState.Paused)
                
                // Widget Update
                WidgetUpdateHelper.updateAllWidgets(this@PlayerService, targetStation, radioPlayer?.isPlaying() ?: false)
            }
        }
    }

    fun playCurrentStation() {
        val station = itsCurrentStation
        if (station != null) {
            val displayTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else liveInfo.title.ifEmpty { station.Name }
            updateMetadata(station, displayTitle)
            
            // Fire & Forget: Report click to official API for community ranking
            val app = application as AMARadioApp
            Utils.reportClickToOfficialApi(app.httpClient, station.StationUuid)
        }
        
        lastErrorFromPlayer = -1
        lastPlayStartTime = 0 // Reset timer for new playback
        this.pauseReason = PauseReason.NONE
        
        if (acquireAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            liveInfo = StreamLiveInfo(null)
            streamInfo = null
            acquireWakeLockAndWifiLock()
            radioPlayer?.setVolume(FULL_VOLUME)
            
            updateNotification(PlayState.PrePlaying)
            
            station?.let { radioPlayer?.play(it) }
        }
    }

    fun pause(reason: PauseReason) {
        if (Utils.isDebug) Log.d(tag, "pausing playback, reason $reason")
        this.pauseReason = reason
        forceStopAudioWarning()
        if (reason == PauseReason.METERED_CONNECTION) lastMeteredConnectionWarningTime = System.currentTimeMillis()
        releaseWakeLockAndWifiLock()
        if (reason != PauseReason.FOCUS_LOSS_TRANSIENT) releaseAudioFocus()
        radioPlayer?.pause()
    }

    fun next() {
        Log.d(tag, "next() called")
        itsCurrentStation?.let {
            val station = it.queue?.getNextById(it.StationUuid) ?: run {
                Log.w(tag, "next() - no queue or station found")
                return@let
            }
            if (radioPlayer?.isPlaying() == true) playWithoutWarnings(station) else playAndWarnIfMetered(station)
        }
    }

    fun previous() {
        Log.d(tag, "previous() called")
        itsCurrentStation?.let {
            val station = it.queue?.getPreviousById(it.StationUuid) ?: run {
                Log.w(tag, "previous() - no queue or station found")
                return@let
            }
            if (radioPlayer?.isPlaying() == true) playWithoutWarnings(station) else playAndWarnIfMetered(station)
        }
    }

    fun resume() {
        if (Utils.isDebug) Log.d(tag, "resuming playback.")
        forceStopAudioWarning()
        var bypass = false
        if (pauseReason == PauseReason.METERED_CONNECTION) {
            val delta = System.currentTimeMillis() - lastMeteredConnectionWarningTime
            bypass = delta in (1 until METERED_CONNECTION_WARNING_COOLDOWN)
        }
        pauseReason = PauseReason.NONE
        lastMeteredConnectionWarningTime = 0
        val app = application as AMARadioApp
        val station = itsCurrentStation ?: app.historyManager.first
        if ((radioPlayer?.isPlaying() == false) && (station != null)) {
            if (bypass) {
                startMeteredConnectionListener()
                playWithoutWarnings(station)
            } else {
                playAndWarnIfMetered(station)
            }
        }
    }

    fun stop() {
        if (Utils.isDebug) Log.d(tag, "stopping playback.")
        isTransitioning = false
        pauseReason = PauseReason.NONE
        lastMeteredConnectionWarningTime = 0
        notificationIsActive = false
        liveInfo = StreamLiveInfo(null)
        streamInfo = null
        forceStopAudioWarning()
        releaseAudioFocus()
        radioPlayer?.stop()
        releaseWakeLockAndWifiLock()
        clearTimer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopMeteredConnectionListener()
    }

    private fun acquireAudioFocus(): Int {
        if (Utils.isDebug) Log.d(tag, "acquiring audio focus.")
        val playbackAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(afChangeListener)
            .build()
        val result = audioManager!!.requestAudioFocus(audioFocusRequest!!)
        if (Utils.isDebug) Log.d(tag, "audio focus result: $result")
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.e(tag, "acquiring audio focus failed!")
            toastOnUi(R.string.error_grant_audiofocus)
        }
        return result
    }

    private fun releaseAudioFocus() {
        if (Utils.isDebug) Log.d(tag, "releasing audio focus.")
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
    }

    fun acquireWakeLockAndWifiLock() {
        if (Utils.isDebug) Log.d(tag, "acquiring wake lock and wifi lock.")
        if (wakeLock == null) wakeLock = powerManager!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlayerService:")
        if (!wakeLock!!.isHeld) wakeLock!!.acquire(10 * 60 * 1000L /*10 minutes*/)
        val wm = itsContext?.getSystemService(WIFI_SERVICE) as WifiManager?
        wm?.let {
            if (wifiLock == null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = it.createWifiLock(mode, "PlayerService")
            }
            if (!wifiLock!!.isHeld) wifiLock!!.acquire()
        }
    }

    private fun releaseWakeLockAndWifiLock() {
        if (Utils.isDebug) Log.d(tag, "releasing wake lock and wifi lock.")
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun sendMessage(theTitle: String, theMessage: String, theTicker: String, playState: PlayState?) {
        var msg = theMessage
        val notificationIntent = Intent(this, ActivityMain::class.java).apply {
            putExtra("stationid", itsCurrentStation?.StationUuid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val stopIntent = Intent(this, PlayerService::class.java).apply { action = ACTION_STOP }
        val pendingIntentStop = PendingIntent.getService(this, 0, stopIntent, pendingIntentFlag)
        val nextIntent = Intent(this, PlayerService::class.java).apply { action = ACTION_SKIP_TO_NEXT }
        val pendingIntentNext = PendingIntent.getService(this, 0, nextIntent, pendingIntentFlag)
        val previousIntent = Intent(this, PlayerService::class.java).apply { action = ACTION_SKIP_TO_PREVIOUS }
        val pendingIntentPrevious = PendingIntent.getService(this, 0, previousIntent, pendingIntentFlag)
        if (((playState == PlayState.Paused) || (playState == PlayState.Idle)) && (pauseReason == PauseReason.METERED_CONNECTION)) {
            msg = resources.getString(R.string.notify_metered_connection)
        } else if (lastErrorFromPlayer != -1) {
            try { msg = resources.getString(lastErrorFromPlayer) } catch (_: Exception) {}
        }
        val contentIntent = PendingIntent.getActivity(itsContext!!, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag)
        val builder = NotificationCompat.Builder(itsContext!!, NOTIFICATION_CHANNEL_ID)
            .setContentIntent(contentIntent)
            .setContentTitle(theTitle)
            .setContentText(msg)
            .setWhen(System.currentTimeMillis())
            .setTicker(theTicker)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_play_arrow_24dp)
            .setLargeIcon(currentStationBitmap) // For compatibility with some vendor UIs
            .addAction(R.drawable.ic_stop_24dp, getString(R.string.action_stop), pendingIntentStop)
            .addAction(R.drawable.ic_skip_previous_24dp, getString(R.string.action_skip_to_previous), pendingIntentPrevious)
        if ((playState == PlayState.Playing) || (playState == PlayState.PrePlaying)) {
            val pauseIntent = Intent(this, PlayerService::class.java).apply { action = ACTION_PAUSE }
            val pendingIntentPause = PendingIntent.getService(this, 0, pauseIntent, pendingIntentFlag)
            builder.addAction(R.drawable.ic_pause_24dp, getString(R.string.action_pause), pendingIntentPause)
            builder.setUsesChronometer(true).setOngoing(true)
        } else if ((playState == PlayState.Paused) || (playState == PlayState.Idle)) {
            val resumeIntent = Intent(this, PlayerService::class.java).apply { action = ACTION_RESUME }
            val pendingIntentResume = PendingIntent.getService(this, 0, resumeIntent, pendingIntentFlag)
            builder.addAction(R.drawable.ic_play_arrow_24dp, getString(R.string.action_resume), pendingIntentResume)
            builder.setUsesChronometer(false).setDeleteIntent(pendingIntentStop).setOngoing(false)
        }
        builder.addAction(R.drawable.ic_skip_next_24dp, getString(R.string.action_skip_to_next), pendingIntentNext)
        
        val style = mediaSession?.let { 
            androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(it)
                .setShowActionsInCompactView(1, 2, 3)
        } ?: @Suppress("DEPRECATION") androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(1, 2, 3)

        builder.setStyle(style)
        val notification = builder.build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        try {
            val isActiveState = (playState == PlayState.Playing || playState == PlayState.PrePlaying)
            
            if (isActiveState || (playState == PlayState.Paused && notificationIsActive)) {
                // Only call startForeground if we are actively playing or already in foreground.
                // This prevents ForegroundServiceStartNotAllowedException on Android 14+.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFY_ID, notification)
                }
                notificationIsActive = true
            } else if (playState == PlayState.Paused) {
                // If paused and not yet in foreground, just show a normal notification.
                manager.notify(NOTIFY_ID, notification)
            } else {
                // Idle or Error: Clean up
                if (notificationIsActive) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notificationIsActive = false
                } else {
                    manager.cancel(NOTIFY_ID)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Foreground state update failed: ${e.message}")
            // Fallback: Try showing a normal notification at least
            try { manager.notify(NOTIFY_ID, notification) } catch (_: Exception) {}
        }
    }

    private fun toastOnUi(messageId: Int) {
        handler?.post { Toast.makeText(this, resources.getString(messageId), Toast.LENGTH_SHORT).show() }
    }

    private fun updateMetadata(station: DataRadioStation, liveTitle: String) {
        serviceScope.launch {
            val bitmap = fetchStationBitmap(station)
            
            // Switch back to player thread to apply metadata safely
            radioPlayer?.runInPlayerThread {
                currentStationBitmap = bitmap
                val metadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station, liveTitle, bitmap)
                
                // 1. Update Player Playlist Metadata
                radioPlayer?.player?.let { player ->
                    player.playlistMetadata = metadata
                    
                    // 2. Update the MediaItem's metadata (Critical for Android Auto)
                    // Use item index directly as currentMediaItem can be null in STATE_IDLE
                    val itemIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                    if (player.mediaItemCount > itemIndex) {
                        val currentItem = player.getMediaItemAt(itemIndex)
                        val updatedItem = currentItem.buildUpon()
                            .setMediaMetadata(metadata)
                            .build()
                        player.replaceMediaItem(itemIndex, updatedItem)
                    }
                }
                
                // 3. Ensure MediaSession is aware of the changes
                mediaSession?.setCustomLayout(listOf())
                
                Log.d("METADATA_DEBUG", "Metadata pushed to Player and Session. Artwork size: ${metadata.artworkData?.size} bytes")

                // 4. Update System Notification
                updateNotification(radioPlayer?.playState ?: PlayState.Paused)
            }
        }
    }

    private suspend fun fetchStationBitmap(station: DataRadioStation): Bitmap {
        val iconDir = File(cacheDir, "station_icons").apply { if (!exists()) mkdirs() }
        val iconFile = File(iconDir, "${station.StationUuid}.jpg")

        // 1. STRIKT: Erst im UUID-basierten Cache nachsehen
        if (iconFile.exists()) {
            try {
                val request = ImageRequest.Builder(this)
                    .data(iconFile)
                    .size(512, 512)
                    .allowHardware(false) // Binder-Sicherheit
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        Log.d("METADATA_DEBUG", "Bild aus Cache geladen für ID: ${station.StationUuid}")
                        return bitmap
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to decode cached icon", e)
            }
        }

        // 2. STRIKT: Über IconUrl (URL vom Radio-Browser) laden
        if (station.IconUrl.isNotEmpty()) {
            try {
                Log.d("METADATA_DEBUG", "Lade Bild von URL: ${station.IconUrl}")
                val request = ImageRequest.Builder(this@PlayerService)
                    .data(station.IconUrl)
                    .size(512, 512)
                    .allowHardware(false) // Binder-Sicherheit
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap(512, 512, Bitmap.Config.RGB_565)
                    saveBitmapToFile(bitmap, iconFile)
                    return bitmap
                }
            } catch (e: Exception) {
                Log.e(tag, "URL fetch failed for ID ${station.StationUuid}: ${station.IconUrl}", e)
            }
        }

        // 3. FALLBACK: Lokales Drawable via ID (Präfix 's_', da Ressourcen-IDs nicht mit Zahlen starten dürfen)
        val uuidResName = "s_" + station.StationUuid.lowercase().replace("-", "_")
        val uuidResId = resources.getIdentifier(uuidResName, "drawable", packageName)
        if (uuidResId != 0) {
            Log.d("METADATA_DEBUG", "Lokales Drawable gefunden für ID: $uuidResName")
            try {
                val bitmap = ResourcesCompat.getDrawable(resources, uuidResId, null)!!.toBitmap(512, 512, Bitmap.Config.RGB_565)
                saveBitmapToFile(bitmap, iconFile)
                return bitmap
            } catch (e: Exception) { /* ignore */ }
        }

        // 4. FALLBACK: Lokales Drawable via Name
        val resName = station.Name.lowercase().replace(Regex("[^a-z0-9]"), "_").replace(Regex("__+"), "_").trim('_')
        val resId = resources.getIdentifier(resName, "drawable", packageName)
        if (resId != 0) {
            Log.d("METADATA_DEBUG", "Lokales Drawable gefunden für Name: $resName")
            try {
                val bitmap = ResourcesCompat.getDrawable(resources, resId, null)!!.toBitmap(512, 512, Bitmap.Config.RGB_565)
                saveBitmapToFile(bitmap, iconFile)
                return bitmap
            } catch (e: Exception) { /* ignore */ }
        }

        Log.w("METADATA_DEBUG", "Kein spezifisches Bild gefunden für ID: ${station.StationUuid}. Generiere dynamischen Platzhalter.")
        val placeholder = StationPlaceholderUtils.createPlaceholderBitmap(station.Name, station.StationUuid)
        saveBitmapToFile(placeholder, iconFile)
        return placeholder
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            Log.e(tag, "Save bitmap failed", e)
        }
    }

    private fun updateMetadata() {
        val station = itsCurrentStation ?: return
        val displayTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else liveInfo.title.ifEmpty { station.Name }
        updateMetadata(station, displayTitle)
    }

    private fun updateNotification(playState: PlayState) {
        val station = itsCurrentStation
        val logicalState = if (pauseReason == PauseReason.USER) PlayState.Paused else playState
        
        val now = System.currentTimeMillis()
        val isTransition = logicalState == PlayState.PrePlaying || logicalState == PlayState.Idle || isTransitioning
        
        // Critical: Allow immediate updates for user-driven Pause/Resume or Errors.
        // Throttle only frequent Metadata/Buffering updates to avoid system "Shedding".
        if (!isTransition && logicalState != PlayState.Error && (now - lastNotificationUpdateTime < NOTIFICATION_THROTTLE_MS)) {
            return
        }
        lastNotificationUpdateTime = now

        handler?.post {
            when (logicalState) {
                PlayState.Idle -> {
                    // Only remove notification if logically stopped or user paused without activity.
                    // Keep it during station-to-station transitions.
                    if (pauseReason != PauseReason.USER && !radioPlayer!!.isPlaying() && !isTransitioning) {
                        NotificationManagerCompat.from(this).cancel(NOTIFY_ID)
                        notificationIsActive = false
                    }
                }
                PlayState.PrePlaying -> {
                    sendMessage(station?.Name ?: "", resources.getString(R.string.notify_pre_play), resources.getString(R.string.notify_pre_play), logicalState)
                }
                PlayState.Playing -> {
                    val title = liveInfo.title
                    if (title.isNotEmpty()) sendMessage(station?.Name ?: "", title, title, logicalState)
                    else sendMessage(station?.Name ?: "", resources.getString(R.string.notify_play), station?.Name ?: "", logicalState)
                }
                PlayState.Paused -> {
                    sendMessage(station?.Name ?: "", resources.getString(R.string.notify_paused), station?.Name ?: "", logicalState)
                }
                PlayState.Error -> {
                    if (pauseReason != PauseReason.USER) {
                        sendMessage(station?.Name ?: "", resources.getString(R.string.error_station_load), station?.Name ?: "", logicalState)
                    }
                }
            }
        }
    }

    fun warnAboutMeteredConnection(playerType: PlayerType) {
        stopMeteredConnectionListener()
        pause(PauseReason.METERED_CONNECTION)
        handler?.post {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGenerator!!.startTone(ToneGenerator.TONE_SUP_RADIO_NOTAVAIL, AUDIO_WARNING_DURATION)
        }
        toneGeneratorStopRunnable = Runnable {
            toneGenerator?.let { it.stopTone(); it.release() }
            toneGenerator = null
            toneGeneratorStopRunnable = null
        }
        handler?.postDelayed(toneGeneratorStopRunnable!!, AUDIO_WARNING_DURATION.toLong())
        val broadcast = Intent(PLAYER_SERVICE_METERED_CONNECTION).apply { putExtra(PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE, playerType as Parcelable) }
        AppEventManager.sendEvent(broadcast)
        updateNotification(PlayState.Paused)
    }

    private fun forceStopAudioWarning() {
        toneGenerator?.let {
            handler?.removeCallbacks(toneGeneratorStopRunnable!!)
            toneGeneratorStopRunnable = null
            handler?.post {
                toneGenerator?.let { it.stopTone(); it.release() }
                toneGenerator = null
            }
        }
    }

    private fun startMeteredConnectionListener() {
        if (sharedPref!!.getBoolean(METERED_CONNECTION_WARNING_KEY, false)) connectivityChecker.startListening(this, connectivityCallback)
    }

    private fun stopMeteredConnectionListener() { connectivityChecker.stopListening(this) }

    override fun onStationTransitionStarting() {
        isTransitioning = true
        // Force an immediate state update to "BUFFERING" for MediaSession
        handler?.post {
            if (mediaSession != null) {
                // This will trigger getPlaybackState override in ForwardingPlayer
                updateNotification(PlayState.PrePlaying)
            }
        }
    }

    override fun onStateChanged(status: PlayState, audioSessionId: Int) {
        if (status == PlayState.Playing) {
            // Only end transition once we are truly playing
            isTransitioning = false
        }
        
        if (status == PlayState.Idle && pauseReason == PauseReason.USER) {
            // Keep the notification in Paused state even if engine stops
            updateNotification(PlayState.Paused)
            return
        }
        Log.d(tag, "onStateChanged: state=$status, audioSessionId=$audioSessionId")
        lastErrorFromPlayer = -1
        if (status == PlayState.Playing) {
            if (lastPlayStartTime <= 0) {
                lastPlayStartTime = android.os.SystemClock.elapsedRealtime()
            }
            if (audioSessionId > 0) {
                val i = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        putExtra("android.intent.extra.ATTRIBUTION_TAG", "player_service")
                    }
                }
                sendBroadcast(i)
            }
        } else {
            if (audioSessionId > 0) {
                val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        putExtra("android.intent.extra.ATTRIBUTION_TAG", "player_service")
                    }
                }
                sendBroadcast(i)
            }
        }
        if ((status != PlayState.Paused) && (status != PlayState.Idle)) startMeteredConnectionListener() else stopMeteredConnectionListener()
        updateNotification(status)
        val intent = Intent(PLAYER_SERVICE_STATE_CHANGE).apply { putExtra(PLAYER_SERVICE_STATE_EXTRA_KEY, status as Parcelable) }
        AppEventManager.sendEvent(intent)

        // Widget Update
        WidgetUpdateHelper.updateAllWidgets(this, itsCurrentStation, status == PlayState.Playing || status == PlayState.PrePlaying)
    }

    override fun onPlayerWarning(messageId: Int) { 
        Log.w(tag, "Player warning: ${resources.getString(messageId)}")
    }

    override fun onPlayerError(messageId: Int) {
        isTransitioning = false
        handler?.post { lastErrorFromPlayer = messageId; toastOnUi(messageId); updateNotification(PlayState.Error) }
    }

    override fun onPlayerCreated(player: Player) {
        currentForwardingPlayer = object : ForwardingPlayer(player) {
            override fun play() { this@PlayerService.resume() }
            override fun pause() { this@PlayerService.pause(PauseReason.USER) }
            override fun stop() { this@PlayerService.stop() }
            override fun seekToNext() { this@PlayerService.next() }
            override fun seekToPrevious() { this@PlayerService.previous() }
            override fun seekToNextMediaItem() { this@PlayerService.next() }
            override fun seekToPreviousMediaItem() { this@PlayerService.previous() }
            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true
            override fun prepare() { 
                // Block automated prepare from system if logically paused by user
                if (pauseReason != PauseReason.USER) {
                    this@PlayerService.resume() 
                }
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) {
                    // Force a full restart for Live Radio instead of simple resume
                    this@PlayerService.resume()
                } else {
                    this@PlayerService.pause(PauseReason.USER)
                }
            }

            override fun getPlaybackState(): Int {
                // Persistent State for Android Auto: Never report IDLE during user pause or station transition.
                if (pauseReason == PauseReason.USER) return Player.STATE_READY
                if (isTransitioning) return Player.STATE_BUFFERING
                return super.getPlaybackState()
            }

            override fun getPlaylistMetadata(): MediaMetadata {
                val realMetadata = super.getPlaylistMetadata()
                val station = itsCurrentStation ?: return realMetadata
                
                // FORCE: If type is Radio Station (21) or title is missing, sanitize it to MUSIC (1).
                // This fixes the Radio Italia HLS metadata drop.
                if (realMetadata.title == null || realMetadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION) {
                    val liveTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else liveInfo.title
                    return com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station, liveTitle, currentStationBitmap)
                }
                return realMetadata
            }

            override fun getMediaMetadata(): MediaMetadata {
                // MediaMetadata in Media3 is the combined metadata of the current item and playlist.
                // We ensure it always contains our station info.
                val realMetadata = super.getMediaMetadata()
                val station = itsCurrentStation ?: return realMetadata

                // FORCE: If transition is active or metadata is incomplete, use our verified builder.
                if (isTransitioning || realMetadata.title == null || realMetadata.artworkUri == null || realMetadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION) {
                    val liveTitle = if (liveInfo.track.isNotEmpty()) liveInfo.track else liveInfo.title
                    return com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station, liveTitle, currentStationBitmap)
                }
                return realMetadata
            }

            override fun getPlayWhenReady(): Boolean {
                // Force play button to show (playWhenReady = false) during user pause
                return if (pauseReason == PauseReason.USER) false else super.getPlayWhenReady()
            }

            override fun getCurrentMediaItem(): androidx.media3.common.MediaItem? {
                val realItem = super.getCurrentMediaItem()
                // Force a valid item during transitions or if the engine reports null
                if ((realItem == null || isTransitioning) && itsCurrentStation != null) {
                    val station = itsCurrentStation!!
                    val streamUrl = (if (!station.playableUrl.isNullOrEmpty()) station.playableUrl else station.StreamUrl) ?: ""
                    
                    val metadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station, station.Name, currentStationBitmap)
                    
                    return com.ounben.amaradio.players.exoplayer.Media3Utils.buildLiveMediaItem(
                        android.net.Uri.parse(streamUrl), 
                        metadata,
                        station.StationUuid
                    )
                }
                return realItem
            }

            override fun getCurrentPosition(): Long {
                // Return elapsed time for live streams to keep Bluetooth/AA happy
                val now = android.os.SystemClock.elapsedRealtime()
                if (radioPlayer?.isPlaying() == true && lastPlayStartTime > 0 && now > lastPlayStartTime) {
                    return now - lastPlayStartTime
                }
                return 0L // Ensure we never return -1 or random values
            }

            override fun getBufferedPosition(): Long {
                val pos = super.getBufferedPosition()
                return if (pos < 0) 0L else pos
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_GET_TIMELINE)
                    .add(Player.COMMAND_PREPARE)
                    .build()
            }
        }
        val session = mediaSession
        if (session != null) {
            radioPlayer?.runInPlayerThread {
                session.player = currentForwardingPlayer!!
            }
        }
    }
    override fun onBufferedTimeUpdate(bufferedMs: Long) {}
    override fun foundShoutcastStream(bitrate: ShoutcastInfo?, isHls: Boolean) {
        this.streamInfo = bitrate; this.isHls = isHls
        sendBroadCast(PLAYER_SERVICE_META_UPDATE)
    }
    override fun foundLiveStreamInfo(liveInfo: StreamLiveInfo) {
        val oldLiveInfo = this.liveInfo
        this.liveInfo = liveInfo
        if (oldLiveInfo.title != this.liveInfo.title) {
            sendBroadCast(PLAYER_SERVICE_META_UPDATE)
            updateMetadata() // Trigger metadata sync on live info change
            val currentTime = Calendar.getInstance().time
            trackHistoryRepository?.getLastInsertedHistoryItem { entry, dao ->
                if ((entry != null) && (entry.title == this.liveInfo.title)) {
                    entry.endTime = Date(0)
                    dao.update(entry)
                } else {
                    dao.setCurrentPlayingTrackEndTime(currentTime)
                    val newEntry = TrackHistoryEntry().apply {
                        stationUuid = itsCurrentStation?.StationUuid ?: ""
                        stationName = itsCurrentStation?.Name ?: ""
                        artist = this@PlayerService.liveInfo.artist
                        title = this@PlayerService.liveInfo.title
                        track = this@PlayerService.liveInfo.track
                        stationIconUrl = itsCurrentStation?.IconUrl ?: ""
                        startTime = currentTime
                        endTime = Date(0)
                    }
                    trackHistoryRepository?.insert(newEntry)
                }
            }
            // Widget Update on track change
            WidgetUpdateHelper.updateAllWidgets(this, itsCurrentStation, true)
        }
    }

    companion object {
        const val NOTIFY_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "amaradio_player_channel"
        const val METERED_CONNECTION_WARNING_KEY = "warn_no_wifi"
        const val PLAYER_SERVICE_TIMER_UPDATE = "com.ounben.amaradio.timerupdate"
        const val PLAYER_SERVICE_META_UPDATE = "com.ounben.amaradio.metaupdate"
        const val PLAYER_SERVICE_STATE_CHANGE = "com.ounben.amaradio.statechange"
        const val PLAYER_SERVICE_STATE_EXTRA_KEY = "state"
        const val PLAYER_SERVICE_METERED_CONNECTION = "com.ounben.amaradio.metered_connection"
        const val PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE = "PLAYER_TYPE"
        const val PLAYER_SERVICE_BOUND = "com.ounben.amaradio.playerservicebound"
        const val ACTION_PAUSE = "pause"; const val ACTION_RESUME = "resume"
        const val ACTION_SKIP_TO_NEXT = "next"; const val ACTION_SKIP_TO_PREVIOUS = "previous"
        const val ACTION_STOP = "stop"
        private const val FULL_VOLUME = 100f; private const val DUCK_VOLUME = 40f
        private const val METERED_CONNECTION_WARNING_COOLDOWN = 20 * 1000
        private const val AUDIO_WARNING_DURATION = 2000
    }
}
