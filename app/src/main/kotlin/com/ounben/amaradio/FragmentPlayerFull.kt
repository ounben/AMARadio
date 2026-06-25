package com.ounben.amaradio

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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
import com.ounben.amaradio.players.PlayState
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

    private lateinit var cellTowerView: ImageView
    private lateinit var statusErrorText: TextView

    // Cache to prevent redundant UI updates
    private var currentStationUuid: String? = null
    private var currentTitle: String? = null
    private var isCurrentlyPlaying: Boolean = false
    private var currentPlayState: PlayState? = null
    private var isFavoriteState: Boolean? = null

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
                        PlayerService.PLAYER_SERVICE_META_UPDATE -> {
                            if (isVisible) fullUpdate() else initialized = false
                        }
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

        cellTowerView = view.findViewById(R.id.cell_tower_view)
        statusErrorText = view.findViewById(R.id.status_error_text)

        recyclerViewHistory.adapter = trackHistoryAdapter
        val llmHistory = LinearLayoutManager(context)
        llmHistory.orientation = RecyclerView.VERTICAL
        recyclerViewHistory.layoutManager = llmHistory
        recyclerViewHistory.isNestedScrollingEnabled = false

        val dividerItemDecoration = DividerItemDecoration(recyclerViewHistory.context, llmHistory.orientation)
        recyclerViewHistory.addItemDecoration(dividerItemDecoration)

        trackHistoryViewModel = ViewModelProvider(this).get(TrackHistoryViewModel::class.java)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                trackHistoryViewModel.allHistoryPaged.collect { songHistoryEntries ->
                    trackHistoryAdapter.submitData(songHistoryEntries)
                }
            }
        }

        return view
    }

    fun init() {
        if (!isAdded) return
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
        cellTowerView.clearAnimation()
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
        if (!isAdded || view == null) return
        
        val station = Utils.getCurrentOrLastStation(requireContext())
        val liveInfo = PlayerServiceUtil.getMetadataLive()
        val streamTitle = liveInfo.title
        val displayTitle = if (!TextUtils.isEmpty(streamTitle)) streamTitle else station?.Name ?: ""
        val playing = PlayerServiceUtil.isPlaying()
        val state = PlayerServiceUtil.getPlayerState()
        val fav = station?.let { favouriteManager.has(it.StationUuid) } ?: false

        // Optimization: Skip heavy UI updates if data hasn't changed
        if (station?.StationUuid == currentStationUuid && 
            displayTitle == currentTitle && 
            playing == isCurrentlyPlaying &&
            state == currentPlayState &&
            fav == isFavoriteState &&
            initialized) {
            return
        }

        currentStationUuid = station?.StationUuid
        currentTitle = displayTitle
        isCurrentlyPlaying = playing
        currentPlayState = state
        isFavoriteState = fav

        view?.post {
            if (!isAdded) return@post
            
            if (textViewGeneralInfo.text != displayTitle) {
                textViewGeneralInfo.text = displayTitle
            }

            if (station != null) {
                val flag = CountryFlagsLoader.instance.getFlag(requireContext(), station.CountryCode)
                flag?.let {
                    val k = it.intrinsicWidth.toFloat() / it.intrinsicHeight.toFloat()
                    val viewHeight = (textViewStationDescription.textSize * 1.3f).toInt()
                    it.setBounds(0, 0, (k * viewHeight).toInt(), viewHeight)
                }
                textViewStationDescription.setCompoundDrawablesRelative(flag, null, null, null)
                textViewStationDescription.text = station.getLongDetails(requireContext())

                val tags = station.TagsAll.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                viewTags.setTags(tags)
            }

            updatePlaybackButtons(playing)
            updateFavouriteButton(fav)
            updateStatusUi(state)
            initialized = true
        }
    }

    private fun updateStatusUi(state: PlayState) {
        cellTowerView.clearAnimation()
        
        when (state) {
            PlayState.PrePlaying -> {
                cellTowerView.visibility = View.VISIBLE
                cellTowerView.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                statusErrorText.visibility = View.GONE
                
                val anim = android.view.animation.AlphaAnimation(1.0f, 0.2f).apply {
                    duration = 600
                    repeatMode = android.view.animation.Animation.REVERSE
                    repeatCount = android.view.animation.Animation.INFINITE
                }
                cellTowerView.startAnimation(anim)
            }
            PlayState.Playing -> {
                cellTowerView.visibility = View.VISIBLE
                cellTowerView.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                statusErrorText.visibility = View.GONE
            }
            PlayState.Error -> {
                cellTowerView.visibility = View.VISIBLE
                cellTowerView.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                statusErrorText.text = getString(R.string.error_station_load)
                statusErrorText.visibility = View.VISIBLE
            }
            else -> {
                cellTowerView.visibility = View.GONE
                statusErrorText.visibility = View.GONE
            }
        }
    }

    private fun updatePlaybackButtons(playing: Boolean) {
        if (playing) {
            btnPlay.setImageResource(R.drawable.ic_pause_circle)
            btnPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            btnPlay.setImageResource(R.drawable.ic_play_circle)
            btnPlay.contentDescription = resources.getString(R.string.detail_play)
        }
    }

    private fun updateFavouriteButton(isFav: Boolean) {
        if (isFav) {
            btnFavourite.setImageResource(R.drawable.ic_star_24dp)
            btnFavourite.alpha = 1.0f
            btnFavourite.contentDescription = getString(R.string.detail_unstar)
        } else {
            btnFavourite.setImageResource(R.drawable.ic_star_transparent_with_border_24dp)
            btnFavourite.alpha = 0.5f
            btnFavourite.contentDescription = getString(R.string.detail_star)
        }
    }

    private fun updateFavouriteButton() {
        val station = Utils.getCurrentOrLastStation(requireContext())
        val fav = station?.let { favouriteManager.has(it.StationUuid) } ?: false
        updateFavouriteButton(fav)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private class TimedUpdateTask(fragment: FragmentPlayerFull) : RefreshHandler.ObjectBoundRunnable<FragmentPlayerFull>(fragment) {
        override fun run(obj: FragmentPlayerFull) {
            // No periodic updates needed for now
        }
    }

    companion object {
        private const val TIMED_UPDATE_INTERVAL = 1000
    }
}
