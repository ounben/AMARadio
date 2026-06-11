package net.ounben.AMARadio.service

import android.app.*
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.*
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.media.audiofx.AudioEffect
import android.net.wifi.WifiManager
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.preference.PreferenceManager
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*
import net.ounben.AMARadio.*
import net.ounben.AMARadio.history.TrackHistoryEntry
import net.ounben.AMARadio.history.TrackHistoryRepository
import net.ounben.AMARadio.players.PlayState
import net.ounben.AMARadio.players.RadioPlayer
import net.ounben.AMARadio.players.selector.PlayerType
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.station.live.ShoutcastInfo
import net.ounben.AMARadio.station.live.StreamLiveInfo
import java.util.*
import android.content.pm.ServiceInfo

class PlayerService : Service(), RadioPlayer.PlayerListener {
    private val TAG = "PLAY"
    private var sharedPref: SharedPreferences? = null
    private var trackHistoryRepository: TrackHistoryRepository? = null
    private var itsContext: Context? = null
    private var handler: Handler? = null
    private var itsCurrentStation: DataRadioStation? = null
    private var radioIcon: BitmapDrawable? = null
    private var radioPlayer: RadioPlayer? = null
    private var audioManager: AudioManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val becomingNoisyReceiver = BecomingNoisyReceiver()
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
    private val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun sendBroadCast(action: String) {
        val local = Intent()
        local.action = action
        LocalBroadcastManager.getInstance(itsContext!!).sendBroadcast(local)
    }

    private val itsBinder: net.ounben.AMARadio.IPlayerService.Stub = object : net.ounben.AMARadio.IPlayerService.Stub() {
        override fun SetStation(station: DataRadioStation) {
            this@PlayerService.setStation(station)
        }
        override fun SkipToNext() { this@PlayerService.next() }
        override fun SkipToPrevious() { this@PlayerService.previous() }
        override fun Play(isAlarm: Boolean) { this@PlayerService.playCurrentStation(isAlarm) }
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
        override fun getMediaSessionToken(): MediaSessionCompat.Token? = this@PlayerService.mediaSession?.sessionToken
        override fun getIsHls(): Boolean = this@PlayerService.isHls
        override fun isPlaying(): Boolean = this@PlayerService.radioPlayer?.isPlaying() ?: false
        override fun getPlayerState(): PlayState = this@PlayerService.radioPlayer?.playState ?: PlayState.Idle
        override fun startRecording() {
            this@PlayerService.radioPlayer?.let {
                val app = this@PlayerService.application as AMARadioApp
                app.recordingsManager.record(this@PlayerService, it)
                this@PlayerService.sendBroadCast(PLAYER_SERVICE_META_UPDATE)
            }
        }
        override fun stopRecording() {
            this@PlayerService.radioPlayer?.let {
                val app = this@PlayerService.application as AMARadioApp
                app.recordingsManager.stopRecording(it)
                this@PlayerService.sendBroadCast(PLAYER_SERVICE_META_UPDATE)
            }
        }
        override fun isRecording(): Boolean = this@PlayerService.radioPlayer?.isRecording() ?: false
        override fun getCurrentRecordFileName(): String? {
            return this@PlayerService.radioPlayer?.let {
                val app = this@PlayerService.application as AMARadioApp
                app.recordingsManager.getRecordingInfo(it)?.fileName
            }
        }
        override fun getTransferredBytes(): Long = this@PlayerService.radioPlayer?.currentPlaybackTransferredBytes ?: 0
        override fun getBufferedSeconds(): Long = this@PlayerService.radioPlayer?.bufferedSeconds ?: 0
        override fun getLastPlayStartTime(): Long = this@PlayerService.lastPlayStartTime
        override fun getPauseReason(): PauseReason = this@PlayerService.pauseReason
        override fun enableMPD(hostname: String, port: Int) {}
        override fun disableMPD() {}
        override fun warnAboutMeteredConnection(playerType: PlayerType) { this@PlayerService.warnAboutMeteredConnection(playerType) }
        override fun isNotificationActive(): Boolean = this@PlayerService.notificationIsActive
    }

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (radioPlayer?.isLocal == false) return@OnAudioFocusChangeListener
        Log.d(TAG, "afChangeListener: focusChange=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "audio focus gain")
                if (pauseReason == PauseReason.FOCUS_LOSS_TRANSIENT) {
                    enableMediaSession()
                    resume()
                }
                radioPlayer?.setVolume(FULL_VOLUME)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "audio focus loss (ignored for testing)")
                // if (radioPlayer?.isPlaying() == true) pause(PauseReason.FOCUS_LOSS)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "audio focus loss transient")
                if (radioPlayer?.isPlaying() == true) pause(PauseReason.FOCUS_LOSS_TRANSIENT)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "audio focus loss transient can duck")
                radioPlayer?.setVolume(DUCK_VOLUME)
            }
        }
    }

    private val connectivityCallback = ConnectivityChecker.ConnectivityCallback { _, connectionType ->
        if (connectionType == ConnectivityChecker.ConnectionType.METERED && sharedPref!!.getBoolean(METERED_CONNECTION_WARNING_KEY, false)) {
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
                if (BuildConfig.DEBUG) Log.d(TAG, "$seconds")
                sendBroadCast(PLAYER_SERVICE_TIMER_UPDATE)
            }
            override fun onFinish() {
                stop()
                timer = null
            }
        }.start()
    }

    override fun onBind(intent: Intent): IBinder? = itsBinder

    override fun onCreate() {
        super.onCreate()
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        handler = Handler(mainLooper)
        itsContext = this
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        radioIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_launcher, null) as BitmapDrawable
        radioPlayer = RadioPlayer(this).apply { setPlayerListener(this@PlayerService) }
        mediaSession = MediaSessionCompat(baseContext, baseContext.packageName).apply {
            setCallback(MediaSessionCallback(this@PlayerService, itsBinder))
            val startActivityIntent = Intent(itsContext, ActivityMain::class.java)
            setSessionActivity(PendingIntent.getActivity(itsContext, 0, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag))
        }
        setMediaPlaybackState(PlaybackStateCompat.STATE_NONE)
        trackHistoryRepository = (application as AMARadioApp).trackHistoryRepository
        val headsetConnectionFilter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(headsetConnectionReceiver, headsetConnectionFilter)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "AMARadio Player", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Channel description"
            channel.enableLights(false)
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "PlayService should be destroyed.")
        stop()
        mediaSession?.release()
        radioPlayer?.destroy()
        unregisterReceiver(headsetConnectionReceiver)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PlayerServiceUtil.bindService(applicationContext)
        if (itsCurrentStation == null) {
            val app = application as AMARadioApp
            itsCurrentStation = app.historyManager.first ?: app.favouriteManager.first
        }
        var showNotification = true
        intent?.let {
            it.action?.let { action ->
                when (action) {
                    ACTION_SKIP_TO_PREVIOUS -> previous()
                    ACTION_SKIP_TO_NEXT -> next()
                    ACTION_STOP -> { stop(); return START_NOT_STICKY }
                    ACTION_PAUSE -> pause(PauseReason.USER)
                    ACTION_RESUME -> resume()
                    Intent.ACTION_MEDIA_BUTTON -> {
                        val key = it.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
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
            MediaButtonReceiver.handleIntent(mediaSession, it)
            showNotification = !it.getBooleanExtra(PLAYER_SERVICE_NO_NOTIFICATION_EXTRA, false)
        }
        if (showNotification && !notificationIsActive) {
            if (itsCurrentStation == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Temporary", NotificationManager.IMPORTANCE_DEFAULT)
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
                    val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle("").setContentText("").build()
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                        } else {
                            startForeground(NOTIFY_ID, notification)
                        }
                        stopForeground(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start temporary foreground service", e)
                    }
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            } else {
                updateNotification(PlayState.Paused)
            }
        }
        return START_STICKY
    }

    private fun playWithoutWarnings(station: DataRadioStation) {
        setStation(station)
        playCurrentStation(false)
    }

    private fun playAndWarnIfMetered(station: DataRadioStation) {
        Utils.playAndWarnIfMetered(application as AMARadioApp, station, PlayerType.AMARadio, { playWithoutWarnings(station) }, object : Utils.MeteredWarningCallback {
            override fun warn(station: DataRadioStation, playerType: PlayerType) {
                setStation(station)
                warnAboutMeteredConnection(playerType)
            }
        })
    }

    fun setStation(station: DataRadioStation) {
        this.itsCurrentStation = station
    }

    fun playCurrentStation(isAlarm: Boolean) {
        if (Utils.shouldLoadIcons(itsContext!!)) downloadRadioIcon()
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioManager?.isStreamMute(AudioManager.STREAM_MUSIC) else false
        Log.d(TAG, "playCurrentStation: current music volume=$currentVol, max volume=$maxVol, isMuted=$isMuted")
        // if (acquireAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            enableMediaSession()
            liveInfo = StreamLiveInfo(null)
            streamInfo = null
            acquireWakeLockAndWifiLock()
            radioPlayer?.setVolume(FULL_VOLUME)
            itsCurrentStation?.let { radioPlayer?.play(it, isAlarm) }
        // }
    }

    fun pause(reason: PauseReason) {
        if (BuildConfig.DEBUG) Log.d(TAG, "pausing playback, reason $reason")
        this.pauseReason = reason
        forceStopAudioWarning()
        if (reason == PauseReason.METERED_CONNECTION) lastMeteredConnectionWarningTime = System.currentTimeMillis()
        releaseWakeLockAndWifiLock()
        if (reason != PauseReason.FOCUS_LOSS_TRANSIENT) releaseAudioFocus()
        radioPlayer?.pause()
    }

    fun next() {
        itsCurrentStation?.let {
            setMediaPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT)
            val station = it.queue?.getNextById(it.StationUuid)
            if (station != null) {
                if (radioPlayer?.isPlaying() == true) playWithoutWarnings(station) else playAndWarnIfMetered(station)
            }
        }
    }

    fun previous() {
        itsCurrentStation?.let {
            val station = it.queue?.getPreviousById(it.StationUuid)
            if (station != null) {
                if (radioPlayer?.isPlaying() == true) playWithoutWarnings(station) else playAndWarnIfMetered(station)
            }
        }
    }

    fun resume() {
        if (BuildConfig.DEBUG) Log.d(TAG, "resuming playback.")
        forceStopAudioWarning()
        var bypass = false
        if (pauseReason == PauseReason.METERED_CONNECTION) {
            val delta = System.currentTimeMillis() - lastMeteredConnectionWarningTime
            bypass = delta in 1 until METERED_CONNECTION_WARNING_COOLDOWN
        }
        pauseReason = PauseReason.NONE
        lastMeteredConnectionWarningTime = 0
        if (radioPlayer?.isPlaying() == false) {
            var station = itsCurrentStation ?: (application as AMARadioApp).historyManager.first
            station?.let {
                if (bypass) {
                    startMeteredConnectionListener()
                    // acquireAudioFocus()
                    playWithoutWarnings(it)
                } else {
                    playAndWarnIfMetered(it)
                }
            }
        }
    }

    fun stop() {
        if (BuildConfig.DEBUG) Log.d(TAG, "stopping playback.")
        pauseReason = PauseReason.NONE
        lastMeteredConnectionWarningTime = 0
        notificationIsActive = false
        liveInfo = StreamLiveInfo(null)
        streamInfo = null
        forceStopAudioWarning()
        releaseAudioFocus()
        disableMediaSession()
        radioPlayer?.stop()
        releaseWakeLockAndWifiLock()
        clearTimer()
        stopForeground(true)
        stopMeteredConnectionListener()
    }

    private fun setMediaPlaybackState(state: Int) {
        mediaSession?.let { session ->
            var actions = PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or PlaybackStateCompat.ACTION_PLAY_PAUSE
            actions = if (state == PlaybackStateCompat.STATE_BUFFERING || state == PlaybackStateCompat.STATE_PLAYING) {
                actions or PlaybackStateCompat.ACTION_PAUSE
            } else {
                actions or PlaybackStateCompat.ACTION_PLAY
            }
            val builder = PlaybackStateCompat.Builder().setActions(actions)
            if (state == PlaybackStateCompat.STATE_ERROR) {
                val error = if ((radioPlayer?.playState == PlayState.Paused || radioPlayer?.playState == PlayState.Idle) && pauseReason == PauseReason.METERED_CONNECTION) {
                    itsContext!!.resources.getString(R.string.notify_metered_connection)
                } else {
                    try { itsContext!!.resources.getString(lastErrorFromPlayer) } catch (e: Exception) { "" }
                }
                builder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED, error)
            }
            builder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            session.setPlaybackState(builder.build())
        }
    }

    private fun enableMediaSession() {
        if (mediaSession?.isActive == false) {
            if (BuildConfig.DEBUG) Log.d(TAG, "enabling media session.")
            registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            mediaSession?.isActive = true
            setMediaPlaybackState(PlaybackStateCompat.STATE_NONE)
        }
    }

    private fun disableMediaSession() {
        if (mediaSession?.isActive == true) {
            if (BuildConfig.DEBUG) Log.d(TAG, "disabling media session.")
            mediaSession?.isActive = false
            unregisterReceiver(becomingNoisyReceiver)
        }
    }

    private fun acquireAudioFocus(): Int {
        if (BuildConfig.DEBUG) Log.d(TAG, "acquiring audio focus.")
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(afChangeListener)
                .build()
            audioManager!!.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager!!.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "audio focus result: $result")
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.e(TAG, "acquiring audio focus failed!")
            toastOnUi(R.string.error_grant_audiofocus)
        }
        return result
    }

    private fun releaseAudioFocus() {
        if (BuildConfig.DEBUG) Log.d(TAG, "releasing audio focus.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(afChangeListener)
        }
    }

    fun acquireWakeLockAndWifiLock() {
        if (BuildConfig.DEBUG) Log.d(TAG, "acquiring wake lock and wifi lock.")
        if (wakeLock == null) wakeLock = powerManager!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlayerService:")
        if (!wakeLock!!.isHeld) wakeLock!!.acquire()
        val wm = itsContext!!.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        wm?.let {
            if (wifiLock == null) {
                wifiLock = it.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PlayerService")
            }
            if (!wifiLock!!.isHeld) wifiLock!!.acquire()
        }
    }

    private fun releaseWakeLockAndWifiLock() {
        if (BuildConfig.DEBUG) Log.d(TAG, "releasing wake lock and wifi lock.")
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun sendMessage(theTitle: String, theMessage: String, theTicker: String) {
        var msg = theMessage
        val notificationIntent = Intent(itsContext, ActivityMain::class.java).apply {
            putExtra("stationid", itsCurrentStation?.StationUuid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val stopIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_STOP }
        val pendingIntentStop = PendingIntent.getService(itsContext, 0, stopIntent, pendingIntentFlag)
        val nextIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_SKIP_TO_NEXT }
        val pendingIntentNext = PendingIntent.getService(itsContext, 0, nextIntent, pendingIntentFlag)
        val previousIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_SKIP_TO_PREVIOUS }
        val pendingIntentPrevious = PendingIntent.getService(itsContext, 0, previousIntent, pendingIntentFlag)
        val playState = radioPlayer?.playState
        if ((playState == PlayState.Paused || playState == PlayState.Idle) && pauseReason == PauseReason.METERED_CONNECTION) {
            msg = itsContext!!.resources.getString(R.string.notify_metered_connection)
        } else if (lastErrorFromPlayer != -1) {
            try { msg = itsContext!!.resources.getString(lastErrorFromPlayer) } catch (e: Exception) {}
        }
        val contentIntent = PendingIntent.getActivity(itsContext, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag)
        val builder = NotificationCompat.Builder(itsContext!!, NOTIFICATION_CHANNEL_ID)
            .setContentIntent(contentIntent)
            .setContentTitle(theTitle)
            .setContentText(msg)
            .setWhen(System.currentTimeMillis())
            .setTicker(theTicker)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_play_arrow_24dp)
            .setLargeIcon(radioIcon?.bitmap)
            .addAction(R.drawable.ic_stop_24dp, getString(R.string.action_stop), pendingIntentStop)
            .addAction(R.drawable.ic_skip_previous_24dp, getString(R.string.action_skip_to_previous), pendingIntentPrevious)
        if (playState == PlayState.Playing || playState == PlayState.PrePlaying) {
            val pauseIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_PAUSE }
            val pendingIntentPause = PendingIntent.getService(itsContext, 0, pauseIntent, pendingIntentFlag)
            builder.addAction(R.drawable.ic_pause_24dp, getString(R.string.action_pause), pendingIntentPause)
            builder.setUsesChronometer(true).setOngoing(true)
        } else if (playState == PlayState.Paused || playState == PlayState.Idle) {
            val resumeIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_RESUME }
            val pendingIntentResume = PendingIntent.getService(itsContext, 0, resumeIntent, pendingIntentFlag)
            builder.addAction(R.drawable.ic_play_arrow_24dp, getString(R.string.action_resume), pendingIntentResume)
            builder.setUsesChronometer(false).setDeleteIntent(pendingIntentStop).setOngoing(false)
        }
        builder.addAction(R.drawable.ic_skip_next_24dp, getString(R.string.action_skip_to_next), pendingIntentNext)
            .setStyle(MediaStyle().setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(1, 2, 3)
                .setCancelButtonIntent(pendingIntentStop)
                .setShowCancelButton(true))
        val notification = builder.build()
        try {
            if (playState == PlayState.Playing || playState == PlayState.PrePlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFY_ID, notification)
                }
            } else {
                if (notificationIsActive) {
                    stopForeground(false)
                }
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFY_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground state", e)
        }
        notificationIsActive = true
    }

    private fun toastOnUi(messageId: Int) {
        handler?.post { Toast.makeText(itsContext, itsContext!!.resources.getString(messageId), Toast.LENGTH_SHORT).show() }
    }

    private fun updateNotification() { radioPlayer?.let { updateNotification(it.playState) } }

    private fun updateNotification(playState: PlayState) {
        when (playState) {
            PlayState.Idle -> {
                NotificationManagerCompat.from(this).cancel(NOTIFY_ID)
                setMediaPlaybackState(PlaybackStateCompat.STATE_NONE)
                notificationIsActive = false
            }
            PlayState.PrePlaying -> {
                sendMessage(itsCurrentStation?.Name ?: "", resources.getString(R.string.notify_pre_play), resources.getString(R.string.notify_pre_play))
                setMediaPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            }
            PlayState.Playing -> {
                val title = liveInfo.title
                if (!TextUtils.isEmpty(title)) sendMessage(itsCurrentStation?.Name ?: "", title!!, title)
                else sendMessage(itsCurrentStation?.Name ?: "", resources.getString(R.string.notify_play), itsCurrentStation?.Name ?: "")
                mediaSession?.let { session ->
                    val builder = MediaMetadataCompat.Builder()
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, itsCurrentStation?.Name)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, radioIcon?.bitmap)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, radioIcon?.bitmap)
                    if (liveInfo.hasArtistAndTrack()) {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, liveInfo.artist)
                        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, liveInfo.track)
                    } else {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, liveInfo.title)
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, itsCurrentStation?.Name)
                    }
                    session.setMetadata(builder.build())
                }
                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }
            PlayState.Paused -> {
                sendMessage(itsCurrentStation?.Name ?: "", resources.getString(R.string.notify_paused), itsCurrentStation?.Name ?: "")
                setMediaPlaybackState(if (lastErrorFromPlayer != -1) PlaybackStateCompat.STATE_ERROR else PlaybackStateCompat.STATE_PAUSED)
            }
        }
    }

    private fun downloadRadioIcon() {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, resources.displayMetrics)
        if (itsCurrentStation?.hasIcon() != true) {
            radioIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_radio_24dp, null)?.let { drawable ->
                val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                BitmapDrawable(resources, bitmap)
            }
            updateNotification()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            val request = ImageRequest.Builder(this@PlayerService)
                .data(itsCurrentStation?.IconUrl)
                .placeholder(R.drawable.ic_radio_24dp)
                .error(R.drawable.ic_radio_24dp)
                .size(px.toInt(), px.toInt())
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
            setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGenerator!!.startTone(ToneGenerator.TONE_SUP_RADIO_NOTAVAIL, AUDIO_WARNING_DURATION)
        }
        toneGeneratorStopRunnable = Runnable {
            toneGenerator?.let { it.stopTone(); it.release() }
            toneGenerator = null
            toneGeneratorStopRunnable = null
            setMediaPlaybackState(PlaybackStateCompat.STATE_ERROR)
        }
        handler?.postDelayed(toneGeneratorStopRunnable!!, AUDIO_WARNING_DURATION.toLong())
        val broadcast = Intent(PLAYER_SERVICE_METERED_CONNECTION).apply { putExtra(PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE, playerType as Parcelable) }
        LocalBroadcastManager.getInstance(itsContext!!).sendBroadcast(broadcast)
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

    override fun onStateChanged(state: PlayState, audioSessionId: Int) {
        handler?.post {
            Log.d(TAG, "onStateChanged: state=$state, audioSessionId=$audioSessionId")
            lastErrorFromPlayer = -1
            when (state) {
                PlayState.Playing -> {
                    enableMediaSession()
                    lastPlayStartTime = System.currentTimeMillis()
                    if (audioSessionId > 0) {
                        val i = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                        }
                        itsContext?.sendBroadcast(i)
                    }
                }
                else -> {
                    if (state != PlayState.PrePlaying) disableMediaSession()
                    if (audioSessionId > 0) {
                        val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                        }
                        itsContext?.sendBroadcast(i)
                    }
                    if (state == PlayState.Idle) stop()
                }
            }
            if (state != PlayState.Paused && state != PlayState.Idle) startMeteredConnectionListener() else stopMeteredConnectionListener()
            updateNotification(state)
            val intent = Intent(PLAYER_SERVICE_STATE_CHANGE).apply { putExtra(PLAYER_SERVICE_STATE_EXTRA_KEY, state as Parcelable) }
            LocalBroadcastManager.getInstance(itsContext!!).sendBroadcast(intent)
        }
    }

    override fun onPlayerWarning(messageId: Int) { onPlayerError(messageId) }
    override fun onPlayerError(messageId: Int) {
        handler?.post { lastErrorFromPlayer = messageId; toastOnUi(messageId); updateNotification() }
    }
    override fun onBufferedTimeUpdate(bufferedMs: Long) {}
    override fun foundShoutcastStream(info: ShoutcastInfo?, isHls: Boolean) {
        this.streamInfo = info; this.isHls = isHls
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
                if (entry != null && entry.title == this.liveInfo.title) {
                    entry.endTime = Date(0)
                    dao.update(entry)
                } else {
                    dao.setCurrentPlayingTrackEndTime(currentTime)
                    val newEntry = TrackHistoryEntry().apply {
                        stationUuid = itsCurrentStation?.StationUuid ?: ""
                        artist = this@PlayerService.liveInfo.artist ?: ""
                        title = this@PlayerService.liveInfo.title ?: ""
                        track = this@PlayerService.liveInfo.track ?: ""
                        stationIconUrl = itsCurrentStation?.IconUrl ?: ""
                        startTime = currentTime
                        endTime = Date(0)
                    }
                    trackHistoryRepository?.insert(newEntry)
                }
            }
        }
    }
    // override fun onHandleWork(intent: Intent) { if (BuildConfig.DEBUG) Log.d(TAG, "onHandleWork: $intent") }

    companion object {
        const val NOTIFY_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "default"
        const val METERED_CONNECTION_WARNING_KEY = "warn_no_wifi"
        const val PLAYER_SERVICE_NO_NOTIFICATION_EXTRA = "no_notification"
        const val PLAYER_SERVICE_TIMER_UPDATE = "net.ounben.AMARadio.timerupdate"
        const val PLAYER_SERVICE_META_UPDATE = "net.ounben.AMARadio.metaupdate"
        const val PLAYER_SERVICE_STATE_CHANGE = "net.ounben.AMARadio.statechange"
        const val PLAYER_SERVICE_STATE_EXTRA_KEY = "state"
        const val PLAYER_SERVICE_METERED_CONNECTION = "net.ounben.AMARadio.metered_connection"
        const val PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE = "PLAYER_TYPE"
        const val PLAYER_SERVICE_BOUND = "net.ounben.AMARadio.playerservicebound"
        const val ACTION_PAUSE = "pause"; const val ACTION_RESUME = "resume"
        const val ACTION_SKIP_TO_NEXT = "next"; const val ACTION_SKIP_TO_PREVIOUS = "previous"
        const val ACTION_STOP = "stop"
        private const val FULL_VOLUME = 100f; private const val DUCK_VOLUME = 40f
        private const val METERED_CONNECTION_WARNING_COOLDOWN = 20 * 1000
        private const val AUDIO_WARNING_DURATION = 2000
    }
}
