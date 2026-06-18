package com.ounben.amaradio

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ounben.amaradio.history.TrackHistoryAdapter
import com.ounben.amaradio.history.TrackHistoryRepository
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.service.PauseReason
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.utils.RefreshHandler
import com.ounben.amaradio.utils.UiScaler
import com.ounben.amaradio.views.RecyclerAwareNestedScrollView
import com.ounben.amaradio.views.TagsView
import kotlinx.coroutines.launch

class FragmentPlayerFull : Fragment() {
    private val TAG = "FragmentPlayerFull"

    fun interface TouchInterceptListener {
        fun requestDisallowInterceptTouchEvent(disallow: Boolean)
    }

    private var touchInterceptListener: TouchInterceptListener? = null
    private var initialized = false
    private val refreshHandler = RefreshHandler()
    private val timedUpdateTask = TimedUpdateTask(this)
    
    private lateinit var favouriteManager: FavouriteManager
    private var favouritesJob: kotlinx.coroutines.Job? = null

    private lateinit var trackHistoryRepository: TrackHistoryRepository
    private lateinit var trackHistoryAdapter: TrackHistoryAdapter

    private lateinit var scrollViewContent: RecyclerAwareNestedScrollView
    
    private lateinit var textViewStationDescription: TextView
    private lateinit var viewTags: TagsView

    private lateinit var textViewGeneralInfo: TextView

    private lateinit var recyclerViewHistory: RecyclerView

    private lateinit var trackHistoryViewModel: TrackHistoryViewModel

    private lateinit var btnPlay: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnFavourite: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val AMARadioApp = requireActivity().application as AMARadioApp
        favouriteManager = AMARadioApp.favouriteManager

        trackHistoryAdapter = TrackHistoryAdapter(requireActivity())
        trackHistoryAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (::recyclerViewHistory.isInitialized) {
                    val lm = recyclerViewHistory.layoutManager as LinearLayoutManager
                    if (lm.findFirstVisibleItemPosition() < 2) {
                        recyclerViewHistory.scrollToPosition(0)
                    }
                }
            }
        })

        trackHistoryRepository = AMARadioApp.trackHistoryRepository

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppEventManager.events.collect { intent ->
                    when (intent.action) {
                        PlayerService.PLAYER_SERVICE_STATE_CHANGE,
                        PlayerService.PLAYER_SERVICE_META_UPDATE -> fullUpdate()
                    }
                }
            }
        }

        val view = inflater.inflate(R.layout.layout_player_full, container, false)
        scrollViewContent = view.findViewById(R.id.scrollViewContent)
        
        textViewStationDescription = view.findViewById(R.id.textViewStationDescription)
        viewTags = view.findViewById(R.id.viewTags)

        textViewGeneralInfo = view.findViewById(R.id.textViewGeneralInfo)

        recyclerViewHistory = view.findViewById(R.id.recyclerViewHistory)

        btnPlay = view.findViewById(R.id.buttonPlay)
        btnPrev = view.findViewById(R.id.buttonPrev)
        btnNext = view.findViewById(R.id.buttonNext)
        btnFavourite = view.findViewById(R.id.buttonFavorite)

        recyclerViewHistory.adapter = trackHistoryAdapter
        val llmHistory = LinearLayoutManager(context)
        llmHistory.orientation = RecyclerView.VERTICAL
        recyclerViewHistory.layoutManager = llmHistory

        val dividerItemDecoration = DividerItemDecoration(recyclerViewHistory.context, llmHistory.orientation)
        recyclerViewHistory.addItemDecoration(dividerItemDecoration)

        trackHistoryViewModel = ViewModelProvider(this).get(TrackHistoryViewModel::class.java)
        trackHistoryViewModel.allHistoryPaged.observe(viewLifecycleOwner) { songHistoryEntries ->
            trackHistoryAdapter.submitList(songHistoryEntries)
        }

        recyclerViewHistory.viewTreeObserver.let {
            if (it.isAlive) {
                it.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        val layoutParams = recyclerViewHistory.layoutParams
                        val newHeight = scrollViewContent.height
                        if (newHeight != layoutParams.height) {
                            layoutParams.height = newHeight
                            recyclerViewHistory.layoutParams = layoutParams
                        }
                    }
                })
            }
        }

        return view
    }

    fun init() {
        fullUpdate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnPlay.setOnClickListener {
            if (PlayerServiceUtil.isPlaying()) {
                PlayerServiceUtil.pause(PauseReason.USER)
            } else {
                playLastFromHistory()
            }
            updatePlaybackButtons(PlayerServiceUtil.isPlaying())
        }

        btnPrev.setOnClickListener { PlayerServiceUtil.skipToPrevious() }
        btnNext.setOnClickListener { PlayerServiceUtil.skipToNext() }

        btnFavourite.setOnClickListener {
            val station = Utils.getCurrentOrLastStation(requireContext()) ?: return@setOnClickListener
            if (favouriteManager.has(station.StationUuid)) {
                StationActions.removeFromFavourites(requireContext(), it, station)
            } else {
                StationActions.markAsFavourite(requireContext(), station)
            }
            updateFavouriteButton()
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

        favouritesJob?.cancel()
        favouritesJob = viewLifecycleOwner.lifecycleScope.launch {
            favouriteManager.stationsFlow.collect {
                updateFavouriteButton()
            }
        }
    }

    private fun stopUpdating() {
        if (view == null) return
        refreshHandler.cancel()
        favouritesJob?.cancel()
    }

    fun resetScroll() {
        scrollViewContent.scrollTo(0, 0)
        if (::recyclerViewHistory.isInitialized) {
            recyclerViewHistory.scrollToPosition(0)
        }
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

        updatePlaybackButtons(PlayerServiceUtil.isPlaying())
        updateFavouriteButton()
        timedUpdateTask.run()
        initialized = true
    }

    private fun updatePlaybackButtons(playing: Boolean) {
        updatePlayButton(playing)
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

    private fun updateFavouriteButton() {
        val station = Utils.getCurrentOrLastStation(requireContext())
        if (station != null && favouriteManager.has(station.StationUuid)) {
            btnFavourite.setImageResource(R.drawable.ic_star_24dp)
            btnFavourite.alpha = 1.0f
            btnFavourite.contentDescription = requireContext().applicationContext.getString(R.string.detail_unstar)
        } else {
            btnFavourite.setImageResource(R.drawable.ic_star_transparent_with_border_24dp)
            btnFavourite.alpha = 0.5f
            btnFavourite.contentDescription = requireContext().applicationContext.getString(R.string.detail_star)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private class TimedUpdateTask(obj: FragmentPlayerFull) : RefreshHandler.ObjectBoundRunnable<FragmentPlayerFull>(obj) {
        override fun run(fragmentPlayerFull: FragmentPlayerFull) {
            // No periodic updates needed for now
        }
    }

    companion object {
        private const val TIMED_UPDATE_INTERVAL = 1000
    }
}
