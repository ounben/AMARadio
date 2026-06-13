package net.ounben.AMARadio

import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.constraintlayout.widget.Group
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import coil.load
import net.ounben.AMARadio.history.*
import net.ounben.AMARadio.recording.*
import net.ounben.AMARadio.service.PauseReason
import net.ounben.AMARadio.service.PlayerService
import net.ounben.AMARadio.service.PlayerServiceUtil
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.station.StationActions
import net.ounben.AMARadio.station.live.StreamLiveInfo
import net.ounben.AMARadio.station.live.metadata.*
import net.ounben.AMARadio.utils.RefreshHandler
import net.ounben.AMARadio.views.RecyclerAwareNestedScrollView
import net.ounben.AMARadio.views.TagsView
import net.ounben.AMARadio.utils.UiScaler
import java.lang.ref.WeakReference
import java.util.*

class FragmentPlayerFull : Fragment() {
    private val TAG = "FragmentPlayerFull"

    fun interface TouchInterceptListener {
        fun requestDisallowInterceptTouchEvent(disallow: Boolean)
    }

    private var touchInterceptListener: TouchInterceptListener? = null
    private var updateUIReceiver: BroadcastReceiver? = null
    private var initialized = false
    private val refreshHandler = RefreshHandler()
    private val timedUpdateTask = TimedUpdateTask(this)
    
    private lateinit var recordingsManager: RecordingsManager
    private var recordingsObserver: Observer? = null

    private lateinit var favouriteManager: FavouriteManager
    private val favouritesObserver = FavouritesObserver()

    private lateinit var trackHistoryRepository: TrackHistoryRepository
    private lateinit var trackHistoryAdapter: TrackHistoryAdapter
    private lateinit var recordingsAdapter: RecordingsAdapter

    private var storagePermissionsDenied = false
    private lateinit var scrollViewContent: RecyclerAwareNestedScrollView
    
    private lateinit var textViewStationDescription: TextView
    private lateinit var viewTags: TagsView

    private lateinit var textViewGeneralInfo: TextView
    private lateinit var textViewTimePlayed: TextView
    private lateinit var textViewNetworkUsageInfo: TextView
    private lateinit var textViewTimeCached: TextView

    private lateinit var groupRecordings: Group
    private lateinit var imgRecordingIcon: ImageView
    private lateinit var textViewRecordingSize: TextView
    private lateinit var textViewRecordingName: TextView

    private lateinit var pagerHistoryAndRecordings: ViewPager
    private lateinit var historyAndRecordsPagerAdapter: HistoryAndRecordsPagerAdapter

    private lateinit var trackHistoryViewModel: TrackHistoryViewModel

    private lateinit var btnPlay: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnFavourite: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val AMARadioApp = requireActivity().application as AMARadioApp
        recordingsManager = AMARadioApp.recordingsManager
        recordingsObserver = Observer { _, _ -> updateRecordings() }
        favouriteManager = AMARadioApp.favouriteManager

        trackHistoryAdapter = TrackHistoryAdapter(requireActivity())
        trackHistoryAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val lm = historyAndRecordsPagerAdapter.recyclerViewSongHistory.layoutManager as LinearLayoutManager
                if (lm.findFirstVisibleItemPosition() < 2) {
                    historyAndRecordsPagerAdapter.recyclerViewSongHistory.scrollToPosition(0)
                }
            }
        })

        trackHistoryRepository = AMARadioApp.trackHistoryRepository

        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    PlayerService.PLAYER_SERVICE_META_UPDATE -> fullUpdate()
                }
            }
        }

        val view = inflater.inflate(R.layout.layout_player_full, container, false)
        scrollViewContent = view.findViewById(R.id.scrollViewContent)
        
        textViewStationDescription = view.findViewById(R.id.textViewStationDescription)
        viewTags = view.findViewById(R.id.viewTags)

        textViewGeneralInfo = view.findViewById(R.id.textViewGeneralInfo)
        textViewTimePlayed = view.findViewById(R.id.textViewTimePlayed)
        textViewNetworkUsageInfo = view.findViewById(R.id.textViewNetworkUsageInfo)
        textViewTimeCached = view.findViewById(R.id.textViewTimeCached)

        groupRecordings = view.findViewById(R.id.group_recording_info)
        imgRecordingIcon = view.findViewById(R.id.imgRecordingIcon)
        textViewRecordingSize = view.findViewById(R.id.textViewRecordingSize)
        textViewRecordingName = view.findViewById(R.id.textViewRecordingName)

        pagerHistoryAndRecordings = view.findViewById(R.id.pagerHistoryAndRecordings)
        historyAndRecordsPagerAdapter = HistoryAndRecordsPagerAdapter(requireContext(), pagerHistoryAndRecordings)
        pagerHistoryAndRecordings.adapter = historyAndRecordsPagerAdapter

        btnPlay = view.findViewById(R.id.buttonPlay)
        btnPrev = view.findViewById(R.id.buttonPrev)
        btnNext = view.findViewById(R.id.buttonNext)
        btnRecord = view.findViewById(R.id.buttonRecord)
        btnFavourite = view.findViewById(R.id.buttonFavorite)

        historyAndRecordsPagerAdapter.recyclerViewSongHistory.adapter = trackHistoryAdapter
        val llmHistory = LinearLayoutManager(context)
        llmHistory.orientation = RecyclerView.VERTICAL
        historyAndRecordsPagerAdapter.recyclerViewSongHistory.layoutManager = llmHistory

        val dividerItemDecoration = DividerItemDecoration(historyAndRecordsPagerAdapter.recyclerViewSongHistory.context, llmHistory.orientation)
        historyAndRecordsPagerAdapter.recyclerViewSongHistory.addItemDecoration(dividerItemDecoration)

        trackHistoryViewModel = ViewModelProvider(this).get(TrackHistoryViewModel::class.java)
        trackHistoryViewModel.allHistoryPaged.observe(viewLifecycleOwner) { songHistoryEntries ->
            trackHistoryAdapter.submitList(songHistoryEntries)
        }

        recordingsAdapter = RecordingsAdapter(requireContext())
        recordingsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val lm = historyAndRecordsPagerAdapter.recyclerViewRecordings.layoutManager as LinearLayoutManager
                if (lm.findFirstVisibleItemPosition() < 2) {
                    historyAndRecordsPagerAdapter.recyclerViewRecordings.scrollToPosition(0)
                }
            }
        })
        historyAndRecordsPagerAdapter.recyclerViewRecordings.adapter = recordingsAdapter
        val llmRecordings = LinearLayoutManager(context)
        llmRecordings.orientation = RecyclerView.VERTICAL
        historyAndRecordsPagerAdapter.recyclerViewRecordings.layoutManager = llmRecordings
        historyAndRecordsPagerAdapter.recyclerViewRecordings.addItemDecoration(dividerItemDecoration)

        pagerHistoryAndRecordings.viewTreeObserver.let {
            if (it.isAlive) {
                it.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        val layoutParams = pagerHistoryAndRecordings.layoutParams
                        val newHeight = scrollViewContent.height
                        if (newHeight != layoutParams.height) {
                            layoutParams.height = newHeight
                            pagerHistoryAndRecordings.layoutParams = layoutParams
                        }
                    }
                })
            }
        }

        return view
    }

    fun init() {
        if (!initialized) {
            fullUpdate()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnPlay.setOnClickListener {
            if (PlayerServiceUtil.isPlaying()) {
                if (PlayerServiceUtil.isRecording()) {
                    PlayerServiceUtil.stopRecording()
                    updateRunningRecording()
                }
                PlayerServiceUtil.pause(PauseReason.USER)
            } else {
                playLastFromHistory()
            }
            updatePlaybackButtons(PlayerServiceUtil.isPlaying(), PlayerServiceUtil.isRecording())
        }

        btnPrev.setOnClickListener { PlayerServiceUtil.skipToPrevious() }
        btnNext.setOnClickListener { PlayerServiceUtil.skipToNext() }

        btnRecord.setOnClickListener {
            if (PlayerServiceUtil.isPlaying()) {
                if (PlayerServiceUtil.isRecording()) {
                    PlayerServiceUtil.stopRecording()
                } else {
                    if (Utils.verifyStoragePermissions(this, PERM_REQ_STORAGE_RECORD)) {
                        PlayerServiceUtil.startRecording()
                    }
                }
                updateRunningRecording()
                pagerHistoryAndRecordings.setCurrentItem(1, true)
            }
        }

        btnFavourite.setOnClickListener {
            val station = Utils.getCurrentOrLastStation(requireContext()) ?: return@setOnClickListener
            if (favouriteManager.has(station.StationUuid)) {
                StationActions.removeFromFavourites(requireContext(), null, station)
            } else {
                StationActions.markAsFavourite(requireContext(), station)
            }
        }

        applyUiScaling()
    }

    private fun applyUiScaling() {
        val scale = UiScaler.getScaleFactor(requireContext())
        if (scale == UiScaler.SCALE_STANDARD) return

        // Scale buttons
        val buttonSize = (64 * resources.displayMetrics.density * scale).toInt()
        val smallButtonSize = (48 * resources.displayMetrics.density * scale).toInt()

        btnPlay.layoutParams.width = buttonSize
        btnPlay.layoutParams.height = buttonSize
        
        btnPrev.layoutParams.width = smallButtonSize
        btnPrev.layoutParams.height = smallButtonSize
        
        btnNext.layoutParams.width = smallButtonSize
        btnNext.layoutParams.height = smallButtonSize
        
        btnRecord.layoutParams.width = smallButtonSize
        btnRecord.layoutParams.height = smallButtonSize
        
        btnFavourite.layoutParams.width = smallButtonSize
        btnFavourite.layoutParams.height = smallButtonSize
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) stopUpdating() else startUpdating()
        touchInterceptListener?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onResume() {
        super.onResume()
        startUpdating()
    }

    override fun onPause() {
        super.onPause()
        stopUpdating()
    }

    fun setTouchInterceptListener(touchInterceptListener: TouchInterceptListener?) {
        this.touchInterceptListener = touchInterceptListener
    }

    private fun startUpdating() {
        if (!isVisible) return

        fullUpdate()
        refreshHandler.executePeriodically(timedUpdateTask, TIMED_UPDATE_INTERVAL.toLong())

        val filter = IntentFilter()
        filter.addAction(PlayerService.PLAYER_SERVICE_TIMER_UPDATE)
        filter.addAction(PlayerService.PLAYER_SERVICE_STATE_CHANGE)
        filter.addAction(PlayerService.PLAYER_SERVICE_META_UPDATE)

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateUIReceiver!!, filter)
        recordingsManager.savedRecordingsObservable.addObserver(recordingsObserver)
        favouriteManager.addObserver(favouritesObserver)
    }

    private fun stopUpdating() {
        if (view == null) return
        refreshHandler.cancel()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateUIReceiver!!)
        recordingsManager.savedRecordingsObservable.deleteObserver(recordingsObserver)
        favouriteManager.deleteObserver(favouritesObserver)
    }

    fun resetScroll() {
        scrollViewContent.scrollTo(0, 0)
        historyAndRecordsPagerAdapter.recyclerViewSongHistory.scrollToPosition(0)
        historyAndRecordsPagerAdapter.recyclerViewRecordings.scrollToPosition(0)
    }

    val isScrolled: Boolean
        get() = scrollViewContent.scrollY > 0

    private fun playLastFromHistory() {
        val AMARadioApp = requireActivity().application as AMARadioApp
        var station = PlayerServiceUtil.getCurrentStation()

        if (station == null) {
            val historyManager = AMARadioApp.historyManager
            station = historyManager.first
        }

        if (station != null) {
            Utils.showPlaySelection(AMARadioApp, station, requireActivity().supportFragmentManager)
        }
    }

    private fun fullUpdate() {
        val station = Utils.getCurrentOrLastStation(requireContext())
        if (station != null) {
            val liveInfo = PlayerServiceUtil.getMetadataLive()
            val streamTitle = liveInfo.title
            textViewGeneralInfo.text = if (!TextUtils.isEmpty(streamTitle)) streamTitle else station.Name

            val flag = CountryFlagsLoader.instance.getFlag(requireContext(), station.CountryCode)
            flag?.let {
                val k = it.intrinsicWidth.toFloat() / it.intrinsicHeight.toFloat()
                val viewHeight = (textViewStationDescription.textSize * 1.3f).toInt()
                it.setBounds(0, 0, (k * viewHeight).toInt(), viewHeight)
            }
            textViewStationDescription.setCompoundDrawablesRelative(flag, null, null, null)
            textViewStationDescription.text = station.getLongDetails(requireContext())

            val tags = station.TagsAll.split(",").toTypedArray()
            viewTags.setTags(tags.toList())
        }

        updateRecordings()
        updatePlaybackButtons(PlayerServiceUtil.isPlaying(), PlayerServiceUtil.isRecording())
        updateFavouriteButton()
        timedUpdateTask.run()
        initialized = true
    }

    private fun updatePlaybackButtons(playing: Boolean, recording: Boolean) {
        updatePlayButton(playing)
        updateRecordButton(playing, recording)
    }

    private fun updatePlayButton(playing: Boolean) {
        if (playing) {
            btnPlay.setImageResource(R.drawable.ic_pause_circle)
            btnPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            btnPlay.setImageResource(R.drawable.ic_play_circle)
            btnPlay.contentDescription = resources.getString(R.string.detail_play)
        }
    }

    private fun updateRecordButton(playing: Boolean, recording: Boolean) {
        btnRecord.isEnabled = playing
        if (recording) {
            btnRecord.setImageResource(R.drawable.ic_stop_recording)
            btnRecord.contentDescription = resources.getString(R.string.detail_stop)
        } else {
            btnRecord.setImageResource(R.drawable.ic_start_recording)
            btnRecord.contentDescription = resources.getString(if (!storagePermissionsDenied) R.string.image_button_record else R.string.image_button_record_request_permission)
        }
    }

    private fun updateRecordings() {
        recordingsAdapter.setRecordings(recordingsManager.getSavedRecordings())
        updateRunningRecording()
    }

    private fun updateRunningRecording() {
        if (PlayerServiceUtil.isRecording()) {
            val runningRecordings = recordingsManager.getRunningRecordings()
            val recordingInfo = runningRecordings.values.iterator().next()
            groupRecordings.visibility = View.VISIBLE
            imgRecordingIcon.startAnimation(AnimationUtils.loadAnimation(context, R.anim.blink_recording))
            textViewRecordingSize.text = Utils.getReadableBytes(recordingInfo.bytesWritten.toDouble())
            textViewRecordingName.text = recordingInfo.fileName
        } else {
            groupRecordings.visibility = View.GONE
            imgRecordingIcon.clearAnimation()
        }
    }

    private fun updateFavouriteButton() {
        val station = Utils.getCurrentOrLastStation(requireContext())
        if (station != null && favouriteManager.has(station.StationUuid)) {
            btnFavourite.setImageResource(R.drawable.ic_star_24dp)
            btnFavourite.contentDescription = requireContext().applicationContext.getString(R.string.detail_unstar)
        } else {
            btnFavourite.setImageResource(R.drawable.ic_star_border_24dp)
            btnFavourite.contentDescription = requireContext().applicationContext.getString(R.string.detail_star)
        }
    }

    private inner class FavouritesObserver : Observer {
        override fun update(o: Observable?, arg: Any?) {
            updateFavouriteButton()
        }
    }

    private inner class HistoryAndRecordsPagerAdapter(context: Context, parent: ViewGroup) : PagerAdapter() {
        private val layoutSongHistory: ViewGroup
        private val layoutRecordings: ViewGroup
        private val titles: Array<String>
        val recyclerViewSongHistory: RecyclerView
        val recyclerViewRecordings: RecyclerView

        init {
            val inflater = LayoutInflater.from(context)
            layoutSongHistory = inflater.inflate(R.layout.page_player_history, parent, false) as ViewGroup
            layoutRecordings = inflater.inflate(R.layout.page_player_recordings, parent, false) as ViewGroup
            titles = arrayOf(resources.getString(R.string.tab_player_history), resources.getString(R.string.tab_player_recordings))
            recyclerViewSongHistory = layoutSongHistory.findViewById(R.id.recyclerViewSongHistory)
            recyclerViewRecordings = layoutRecordings.findViewById(R.id.recyclerViewRecordings)
        }

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            val layout = if (position == 0) layoutSongHistory else layoutRecordings
            collection.addView(layout)
            return layout
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        override fun getCount(): Int = 2

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`

        override fun getPageTitle(position: Int): CharSequence = titles[position]
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private class TimedUpdateTask(obj: FragmentPlayerFull) : RefreshHandler.ObjectBoundRunnable<FragmentPlayerFull>(obj) {
        override fun run(fragmentPlayerFull: FragmentPlayerFull) {
            val shoutcastInfo = PlayerServiceUtil.getShoutcastInfo()
            if (PlayerServiceUtil.isPlaying()) {
                var networkUsageInfo = Utils.getReadableBytes(PlayerServiceUtil.getTransferredBytes().toDouble())
                if (shoutcastInfo != null && shoutcastInfo.bitrate > 0) {
                    networkUsageInfo += " (${shoutcastInfo.bitrate} kbps)"
                }
                fragmentPlayerFull.textViewNetworkUsageInfo.text = networkUsageInfo
                val now = System.currentTimeMillis()
                val startTime = PlayerServiceUtil.getLastPlayStartTime()
                var deltaSeconds = if (startTime > 0) (now - startTime) / 1000 else 0
                deltaSeconds = Math.max(deltaSeconds, 0)
                fragmentPlayerFull.textViewTimePlayed.text = DateUtils.formatElapsedTime(deltaSeconds)
                fragmentPlayerFull.textViewTimeCached.text = DateUtils.formatElapsedTime(PlayerServiceUtil.getBufferedSeconds())
                fragmentPlayerFull.updateRunningRecording()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERM_REQ_STORAGE_RECORD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                storagePermissionsDenied = false
                PlayerServiceUtil.startRecording()
            } else {
                storagePermissionsDenied = true
                activity?.let { Utils.showModernToast(it, R.string.error_record_needs_write) }
            }
            updatePlaybackButtons(PlayerServiceUtil.isPlaying(), PlayerServiceUtil.isRecording())
            updateRecordings()
        }
    }

    companion object {
        private const val PERM_REQ_STORAGE_RECORD = 1001
        private const val TIMED_UPDATE_INTERVAL = 1000
    }
}
