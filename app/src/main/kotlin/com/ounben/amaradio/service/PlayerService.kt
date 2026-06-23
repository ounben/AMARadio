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
import android.graphics.drawable.BitmapDrawable
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
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.preference.PreferenceManager
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerService : MediaLibraryService(), RadioPlayer.PlayerListener {
    private val tag = "PLAY"
    private var sharedPref: SharedPreferences? = null
    private var trackHistoryRepository: TrackHistoryRepository? = null
    private var itsContext: Context? = null
    private var handler: Handler? = null
    private var itsCurrentStation: DataRadioStation? = null
    private var radioIcon: BitmapDrawable? = null
    private var radioPlayer: RadioPlayer? = null
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
    private val pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE
    private lateinit var amaradioBrowser: AMARadioBrowser
    
    private var mediaRouter: MediaRouter? = null
    private var lastRouteId: String? = null
    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            if (route.id == lastRouteId) {
                if (Utils.isDebug) Log.d(tag, "MediaRouter: Ignore redundant route selection for ${route.id}")
                return
            }
            Log.d(tag, "MediaRouter: New route selected: ${route.id}, reason: $reason")
            lastRouteId = route.id
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
             Log.d(tag, "MediaRouter: Route unselected: ${route.id}")
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            if (Utils.isDebug) Log.v(tag, "MediaRouter: Route changed (status ping) for ${route.id}")
        }
    }

    private fun sendBroadCast(action: String) {
        val local = Intent()
        local.action = action
        AppEventManager.sendEvent(local)
    }

    private val itsBinder: IPlayerService.Stub = object : IPlayerService.Stub() {
        override fun SetStation(station: DataRadioStation) {
            this@PlayerService.setStation(station)
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
        @Suppress("DEPRECATION")
        override fun getMediaSessionToken(): MediaSessionCompat.Token? = null 
        override fun getIsHls(): Boolean = this@PlayerService.isHls
        override fun isPlaying(): Boolean = this@PlayerService.radioPlayer?.isPlaying() ?: false
        override fun getPlayerState(): PlayState = this@PlayerService.radioPlayer?.playState ?: PlayState.Idle
        override fun getTransferredBytes(): Long = this@PlayerService.radioPlayer?.currentPlaybackTransferredBytes ?: 0
        override fun getBufferedSeconds(): Long = this@PlayerService.radioPlayer?.bufferedSeconds ?: 0
        override fun getLastPlayStartTime(): Long = this@PlayerService.lastPlayStartTime
        override fun getPauseReason(): PauseReason = this@PlayerService.pauseReason
        override fun enableMPD(hostname: String, port: Int) {}
        override fun disableMPD() {}
        override fun warnAboutMeteredConnection(playerType: PlayerType) { this@PlayerService.warnAboutMeteredConnection(playerType) }
        override fun isNotificationActive(): Boolean = this@PlayerService.notificationIsActive
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

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
                if (radioPlayer?.isPlaying() == true) pause(PauseReason.FOCUS_LOSS)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(tag, "audio focus loss transient")
                if (radioPlayer?.isPlaying() == true) pause(PauseReason.FOCUS_LOSS_TRANSIENT)
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
        radioIcon = ResourcesCompat.getDrawable(resources, R.mipmap.ic_elgato_launcher, null) as? BitmapDrawable
        radioPlayer = RadioPlayer(this).apply { setPlayerListener(this@PlayerService) }
        
        mediaRouter = MediaRouter.getInstance(this)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
        mediaRouter?.addCallback(selector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS)

        amaradioBrowser = AMARadioBrowser(application as? AMARadioApp ?: (applicationContext as AMARadioApp))

        val startActivityIntent = Intent(this, ActivityMain::class.java)
        mediaSession = MediaLibrarySession.Builder(this, radioPlayer!!.player!!, MediaSessionCallback(this, itsBinder, amaradioBrowser))
            .setSessionActivity(PendingIntent.getActivity(this, 0, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag))
            .build()

        trackHistoryRepository = (application as AMARadioApp).trackHistoryRepository
        val headsetConnectionFilter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(headsetConnectionReceiver, headsetConnectionFilter)
        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "AMARadio Player", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Channel description"
        channel.enableLights(false)
        channel.enableVibration(false)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        if (Utils.isDebug) Log.d(tag, "PlayService should be destroyed.")
        stop()
        mediaRouter?.removeCallback(mediaRouterCallback)
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
        this.itsCurrentStation = station
    }

    fun playCurrentStation() {
        if (Utils.shouldLoadIcons(this)) downloadRadioIcon()
        
        lastErrorFromPlayer = -1
        this.pauseReason = PauseReason.NONE
        
        if (acquireAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            liveInfo = StreamLiveInfo(null)
            streamInfo = null
            acquireWakeLockAndWifiLock()
            radioPlayer?.setVolume(FULL_VOLUME)
            
            updateNotification(PlayState.PrePlaying)
            
            itsCurrentStation?.let { radioPlayer?.play(it) }
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
        itsCurrentStation?.let {
            val station = it.queue?.getNextById(it.StationUuid) ?: return@let
            if (radioPlayer?.isPlaying() == true) playWithoutWarnings(station) else playAndWarnIfMetered(station)
        }
    }

    fun previous() {
        itsCurrentStation?.let {
            val station = it.queue?.getPreviousById(it.StationUuid) ?: return@let
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
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager?
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
        val contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag)
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentIntent(contentIntent)
            .setContentTitle(theTitle)
            .setContentText(msg)
            .setWhen(System.currentTimeMillis())
            .setTicker(theTicker)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_play_arrow_24dp)
            .setLargeIcon(radioIcon?.bitmap)
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
        try {
            if (((playState == PlayState.Playing) || (playState == PlayState.PrePlaying)) || (playState == PlayState.Paused)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFY_ID, notification)
                }
                notificationIsActive = true
            } else {
                if (notificationIsActive) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notificationIsActive = false
                } else {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFY_ID, notification)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update foreground state", e)
        }
    }

    private fun toastOnUi(messageId: Int) {
        handler?.post { Toast.makeText(this, resources.getString(messageId), Toast.LENGTH_SHORT).show() }
    }

    private fun updateNotification() { radioPlayer?.let { updateNotification(it.playState) } }

    private fun updateNotification(playState: PlayState) {
        if (Looper.myLooper() != radioPlayer?.playerLooper) {
            radioPlayer?.runInPlayerThread { updateNotification(playState) }
            return
        }

        when (playState) {
            PlayState.Idle -> {
                NotificationManagerCompat.from(this).cancel(NOTIFY_ID)
                notificationIsActive = false
            }
            PlayState.PrePlaying -> {
                sendMessage(itsCurrentStation?.Name ?: "", resources.getString(R.string.notify_pre_play), resources.getString(R.string.notify_pre_play), playState)
            }
            PlayState.Playing -> {
                val title = liveInfo.title
                if (title.isNotEmpty()) sendMessage(itsCurrentStation?.Name ?: "", title, title, playState)
                else sendMessage(itsCurrentStation?.Name ?: "", resources.getString(R.string.notify_play), itsCurrentStation?.Name ?: "", playState)
                
                itsCurrentStation?.let { station ->
                    val metadataBuilder = MediaMetadata.Builder()
                        .setAlbumTitle(station.Name)
                        .setDisplayTitle(station.Name)
                    
                    if (liveInfo.hasArtistAndTrack()) {
                        metadataBuilder.setArtist(liveInfo.artist)
                        metadataBuilder.setTitle(liveInfo.track)
                    } else {
                        metadataBuilder.setTitle(liveInfo.title.ifEmpty { station.Name })
                        metadataBuilder.setArtist(station.Name)
                    }
                    
                    radioIcon?.bitmap?.let {
                        val byteStream = java.io.ByteArrayOutputStream()
                        it.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
                        metadataBuilder.setArtworkData(byteStream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                    
                    radioPlayer?.player?.playlistMetadata = metadataBuilder.build()
                }
            }
            PlayState.Paused -> {
                sendMessage(itsCurrentStation?.Name ?: "", resources.getString(R.string.notify_paused), itsCurrentStation?.Name ?: "", playState)
            }
        }
    }

    private fun downloadRadioIcon() {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, resources.displayMetrics).toInt()
        if (itsCurrentStation?.hasIcon() != true) {
            radioIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_radio_24dp, null)?.let { drawable ->
                createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight).applyCanvas {
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(this)
                }.toDrawable(resources)
            }
            updateNotification()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            val request = ImageRequest.Builder(this@PlayerService)
                .data(itsCurrentStation?.IconUrl)
                .placeholder(R.drawable.ic_radio_24dp)
                .error(R.drawable.ic_radio_24dp)
                .size(px, px)
                .build()
            
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                radioIcon = result.drawable as? BitmapDrawable
                updateNotification()
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

    override fun onStateChanged(status: PlayState, audioSessionId: Int) {
        Log.d(tag, "onStateChanged: state=$status, audioSessionId=$audioSessionId")
        lastErrorFromPlayer = -1
        if (status == PlayState.Playing) {
            lastPlayStartTime = System.currentTimeMillis()
            if (audioSessionId > 0) {
                val i = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                }
                sendBroadcast(i)
            }
        } else {
            if (audioSessionId > 0) {
                val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                }
                sendBroadcast(i)
            }
            if (status == PlayState.Idle) stop()
        }
        if ((status != PlayState.Paused) && (status != PlayState.Idle)) startMeteredConnectionListener() else stopMeteredConnectionListener()
        updateNotification(status)
        val intent = Intent(PLAYER_SERVICE_STATE_CHANGE).apply { putExtra(PLAYER_SERVICE_STATE_EXTRA_KEY, status as Parcelable) }
        AppEventManager.sendEvent(intent)
    }

    override fun onPlayerWarning(messageId: Int) { onPlayerError(messageId) }
    override fun onPlayerError(messageId: Int) {
        handler?.post { lastErrorFromPlayer = messageId; toastOnUi(messageId); updateNotification() }
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
            updateNotification()
            val currentTime = Calendar.getInstance().time
            trackHistoryRepository?.getLastInsertedHistoryItem { entry, dao ->
                if ((entry != null) && (entry.title == this.liveInfo.title)) {
                    entry.endTime = Date(0)
                    dao.update(entry)
                } else {
                    dao.setCurrentPlayingTrackEndTime(currentTime)
                    val newEntry = TrackHistoryEntry().apply {
                        stationUuid = itsCurrentStation?.StationUuid ?: ""
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
        }
    }

    companion object {
        const val NOTIFY_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "default"
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
