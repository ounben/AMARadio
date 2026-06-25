package com.ounben.amaradio

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.ounben.amaradio.history.TrackHistoryRepository
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.service.PauseReason
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.utils.UiScaler
import kotlinx.coroutines.launch

class FragmentPlayerSmall : Fragment() {
    private lateinit var trackHistoryRepository: TrackHistoryRepository

    enum class Role {
        HEADER,
        PLAYER
    }

    fun interface Callback {
        fun onToggle()
    }

    private var callback: Callback? = null
    private var role = Role.PLAYER

    private lateinit var textViewStationName: TextView
    private lateinit var textViewLiveInfo: TextView
    private lateinit var textViewLiveInfoBig: TextView
    private lateinit var imageViewIcon: ImageView
    private lateinit var buttonPlay: ImageButton
    private lateinit var buttonMore: ImageButton
    
    private lateinit var cellTowerView: ImageView
    private lateinit var statusErrorText: TextView

    private var firstPlayAttempted = false
    
    // Cache to prevent redundant UI updates
    private var currentStationUuid: String? = null
    private var currentTitle: String? = null
    private var currentPlayState: PlayState? = null
    private var currentRole: Role? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_player_small, container, false)

        val AMARadioApp = requireActivity().application as AMARadioApp
        trackHistoryRepository = AMARadioApp.trackHistoryRepository

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppEventManager.events.collect { intent ->
                    when (intent.action) {
                        PlayerService.PLAYER_SERVICE_STATE_CHANGE,
                        PlayerService.PLAYER_SERVICE_META_UPDATE -> fullUpdate()
                        PlayerService.PLAYER_SERVICE_BOUND -> tryPlayAtStart()
                    }
                }
            }
        }

        textViewStationName = view.findViewById(R.id.textViewStationName)
        textViewLiveInfo = view.findViewById(R.id.textViewLiveInfo)
        textViewLiveInfoBig = view.findViewById(R.id.textViewLiveInfoBig)
        imageViewIcon = view.findViewById(R.id.playerRadioImage)

        buttonPlay = view.findViewById(R.id.buttonPlay)
        buttonMore = view.findViewById(R.id.buttonMore)
        
        cellTowerView = view.findViewById(R.id.cell_tower_view)
        statusErrorText = view.findViewById(R.id.status_error_text)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().application.registerActivityLifecycleCallbacks(LifecycleCallbacks())

        buttonPlay.setOnClickListener {
            if (PlayerServiceUtil.isPlaying()) {
                PlayerServiceUtil.pause(PauseReason.USER)
            } else {
                playLastFromHistory()
            }
        }

        buttonMore.setOnClickListener {
            val station = Utils.getCurrentOrLastStation(requireContext()) ?: return@setOnClickListener
            showPlayerMenu(station)
        }

        imageViewIcon.setOnClickListener {
            callback?.onToggle()
        }

        view.setOnClickListener {
            callback?.onToggle()
        }

        tryPlayAtStart()
        applyUiScaling()
        fullUpdate()
    }

    private fun applyUiScaling() {
        val scale = UiScaler.getScaleFactor(requireContext())

        val layoutParams = view?.layoutParams
        if (layoutParams != null) {
            // Increased base height to 84dp to accommodate the 3rd line
            val baseHeightDp = 84f
            val scaledHeight = (baseHeightDp * resources.displayMetrics.density * scale).toInt()
            layoutParams.height = scaledHeight
            view?.layoutParams = layoutParams
        }

        val iconSize = (40 * resources.displayMetrics.density * scale).toInt()
        imageViewIcon.layoutParams.width = iconSize
        imageViewIcon.layoutParams.height = iconSize

        val playButtonSize = (48 * resources.displayMetrics.density * scale).toInt()
        buttonPlay.layoutParams.width = playButtonSize
        buttonPlay.layoutParams.height = playButtonSize
        buttonPlay.scaleType = ImageView.ScaleType.FIT_CENTER

        buttonMore.layoutParams.width = iconSize
        buttonMore.layoutParams.height = iconSize
    }

    override fun onResume() {
        super.onResume()
        fullUpdate()
    }

    override fun onPause() {
        super.onPause()
        cellTowerView.clearAnimation()
    }

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    fun setRole(role: Role) {
        this.role = role
        fullUpdate()
    }

    private fun playLastFromHistory() {
        val AMARadioApp = requireActivity().application as AMARadioApp
        var station = PlayerServiceUtil.getCurrentStation()

        if (station == null) {
            val historyManager = AMARadioApp.historyManager
            station = historyManager.first
        }

        if (station != null && !PlayerServiceUtil.isPlaying()) {
            Utils.showPlaySelection(AMARadioApp, station, requireActivity().supportFragmentManager)
        }
    }

    private fun tryPlayAtStart() {
        var play = false
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext)
        if (!firstPlayAttempted && PlayerServiceUtil.isServiceBound()) {
            firstPlayAttempted = true
            if (!PlayerServiceUtil.isPlaying()) {
                play = sharedPreferences.getBoolean("auto_play_on_startup", false)
            }
        }

        if (play) {
            val autoOff = sharedPreferences.getBoolean("auto_off_on_startup", false)
            if (autoOff) {
                var timeout = 10
                try {
                    timeout = sharedPreferences.getString("auto_off_timeout", "10")?.toInt() ?: 10
                } catch (_: Exception) {
                }
                PlayerServiceUtil.addTimer(timeout * 60)
            }
            playLastFromHistory()
        }
    }

    private fun fullUpdate() {
        if (!isAdded) return
        
        val station = Utils.getCurrentOrLastStation(requireContext())
        val stationName = station?.Name ?: ""
        val liveInfo = PlayerServiceUtil.getMetadataLive()
        val streamTitle = liveInfo.title
        val displayTitle = if (!TextUtils.isEmpty(streamTitle)) streamTitle else ""
        val state = PlayerServiceUtil.getPlayerState()

        if (station?.StationUuid == currentStationUuid && 
            displayTitle == currentTitle && 
            state == currentPlayState && 
            role == currentRole) {
            return
        }

        currentStationUuid = station?.StationUuid
        currentTitle = displayTitle
        currentPlayState = state
        currentRole = role

        if (PlayerServiceUtil.isPlaying()) {
            buttonPlay.setImageResource(R.drawable.ic_pause_circle)
            buttonPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            buttonPlay.setImageResource(R.drawable.ic_play_circle)
            buttonPlay.contentDescription = resources.getString(R.string.detail_play)
        }

        textViewStationName.text = stationName

        if (!TextUtils.isEmpty(displayTitle)) {
            textViewLiveInfo.visibility = View.VISIBLE
            textViewLiveInfo.text = displayTitle
            textViewStationName.gravity = Gravity.BOTTOM
        } else {
            textViewLiveInfo.visibility = View.GONE
            textViewStationName.gravity = Gravity.CENTER_VERTICAL
        }

        textViewLiveInfoBig.text = stationName

        if (!Utils.shouldLoadIcons(requireContext())) {
            imageViewIcon.visibility = View.GONE
        } else {
            imageViewIcon.visibility = View.VISIBLE
            PlayerServiceUtil.getStationIcon(imageViewIcon, if (station?.hasIcon() == true) station.IconUrl else null)
        }

        updateStatusUi(state)

        if (role == Role.PLAYER) {
            buttonPlay.visibility = View.VISIBLE
            buttonMore.visibility = View.GONE
            textViewStationName.visibility = View.VISIBLE
            textViewLiveInfoBig.visibility = View.GONE
        } else if (role == Role.HEADER) {
            buttonPlay.visibility = View.GONE
            buttonMore.visibility = View.VISIBLE
            textViewLiveInfo.visibility = View.GONE
            textViewStationName.visibility = View.GONE
            textViewLiveInfoBig.visibility = View.VISIBLE
            cellTowerView.visibility = View.GONE
            statusErrorText.visibility = View.GONE
        }
    }

    private fun updateStatusUi(state: PlayState) {
        cellTowerView.clearAnimation()
        
        if (role == Role.HEADER) {
            cellTowerView.visibility = View.GONE
            statusErrorText.visibility = View.GONE
            return
        }

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

    private fun showPlayerMenu(currentStation: DataRadioStation) {
        val dropDownMenu = PopupMenu(context, buttonMore)
        dropDownMenu.menuInflater.inflate(R.menu.menu_player, dropDownMenu.menu)
        dropDownMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_homepage -> {
                    StationActions.showWebLinks(requireActivity(), currentStation)
                }
                R.id.action_share -> {
                    StationActions.share(requireContext(), currentStation)
                }
                R.id.action_delete_stream_history -> {
                    trackHistoryRepository.deleteHistory()
                }
            }
            true
        }
        dropDownMenu.show()
    }

    inner class LifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {
            if (context == null) return
            tryPlayAtStart()
        }
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
