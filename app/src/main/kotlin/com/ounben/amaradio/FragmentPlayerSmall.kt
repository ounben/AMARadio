package com.ounben.amaradio

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.MiniPlayer
import com.ounben.amaradio.ui.PlayerViewModel

class FragmentPlayerSmall : Fragment() {

    enum class Role {
        HEADER,
        PLAYER
    }

    fun interface Callback {
        fun onToggle()
    }

    private var callback: Callback? = null
    private var roleState = mutableStateOf(Role.PLAYER)
    private val playerViewModel: PlayerViewModel by activityViewModels()

    private var firstPlayAttempted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AMARadioTheme {
                    val role by roleState
                    MiniPlayer(
                        viewModel = playerViewModel,
                        isHeaderRole = role == Role.HEADER,
                        onToggleBottomSheet = { callback?.onToggle() },
                        onMoreClick = {
                            val station = PlayerServiceUtil.getCurrentStation() ?: return@MiniPlayer
                            showPlayerMenu(this, station)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().application.registerActivityLifecycleCallbacks(LifecycleCallbacks())
        tryPlayAtStart()
    }

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    fun setRole(role: Role) {
        roleState.value = role
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
            playerViewModel.togglePlayPause()
        }
    }

    private fun showPlayerMenu(anchor: View, currentStation: DataRadioStation) {
        val dropDownMenu = PopupMenu(context, anchor)
        dropDownMenu.menuInflater.inflate(R.menu.menu_player, dropDownMenu.menu)
        dropDownMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_homepage -> StationActions.showWebLinks(requireActivity(), currentStation)
                R.id.action_share -> StationActions.share(requireContext(), currentStation)
                R.id.action_delete_stream_history -> {
                    val app = requireActivity().application as AMARadioApp
                    app.trackHistoryRepository.deleteHistory()
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
