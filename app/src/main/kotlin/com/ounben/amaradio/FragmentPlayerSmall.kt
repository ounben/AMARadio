package com.ounben.amaradio

import android.app.Activity
import android.app.Application
import android.content.*
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.history.TrackHistoryRepository
import com.ounben.amaradio.players.mpd.MPDClient
import com.ounben.amaradio.service.PauseReason
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.utils.UiScaler

class FragmentPlayerSmall : Fragment() {
    private lateinit var trackHistoryRepository: TrackHistoryRepository

    enum class Role {
        HEADER,
        PLAYER
    }

    fun interface Callback {
        fun onToggle()
    }

    private var mpdClient: MPDClient? = null
    private var callback: Callback? = null
    private var role = Role.PLAYER

    private lateinit var textViewStationName: TextView
    private lateinit var textViewLiveInfo: TextView
    private lateinit var textViewLiveInfoBig: TextView
    private lateinit var imageViewIcon: ImageView
    private lateinit var buttonPlay: ImageButton
    private lateinit var buttonMore: ImageButton

    private var firstPlayAttempted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_player_small, container, false)

        val AMARadioApp = requireActivity().application as AMARadioApp
        mpdClient = AMARadioApp.mpdClient
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
            val AMARadioApp = requireActivity().application as AMARadioApp
            val favouriteManager = AMARadioApp.favouriteManager
            val isInFavorites = favouriteManager.has(station.StationUuid)

            showPlayerMenu(station, isInFavorites)
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
            val baseHeightDp = 72f
            val scaledHeight = (baseHeightDp * resources.displayMetrics.density * scale).toInt()
            layoutParams.height = scaledHeight
            view?.layoutParams = layoutParams
        }

        val iconSize = (36 * resources.displayMetrics.density * scale).toInt()
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
                } catch (e: Exception) {
                }
                PlayerServiceUtil.addTimer(timeout * 60)
            }
            playLastFromHistory()
        }
    }

    private fun fullUpdate() {
        if (PlayerServiceUtil.isPlaying()) {
            buttonPlay.setImageResource(R.drawable.ic_pause_circle)
            buttonPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            buttonPlay.setImageResource(R.drawable.ic_play_circle)
            buttonPlay.contentDescription = resources.getString(R.string.detail_play)
        }

        val station = Utils.getCurrentOrLastStation(requireContext())
        val stationName = station?.Name ?: ""

        textViewStationName.text = stationName

        val liveInfo = PlayerServiceUtil.getMetadataLive()
        val streamTitle = liveInfo.title
        if (!TextUtils.isEmpty(streamTitle)) {
            textViewLiveInfo.visibility = View.VISIBLE
            textViewLiveInfo.text = streamTitle
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
        }
    }

    private fun showPlayerMenu(currentStation: DataRadioStation, stationIsInFavourites: Boolean) {
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
                R.id.action_set_alarm -> {
                    StationActions.setAsAlarm(requireActivity(), currentStation)
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
