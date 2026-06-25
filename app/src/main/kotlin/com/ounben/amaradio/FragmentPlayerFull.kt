package com.ounben.amaradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ounben.amaradio.history.TrackHistoryInfoDialog
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.FullPlayer
import com.ounben.amaradio.ui.PlayerViewModel

class FragmentPlayerFull : Fragment() {
    
    fun interface TouchInterceptListener {
        fun requestDisallowInterceptTouchEvent(disallow: Boolean)
    }

    private var touchInterceptListener: TouchInterceptListener? = null
    
    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val trackHistoryViewModel: TrackHistoryViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AMARadioTheme {
                    FullPlayer(
                        playerViewModel = playerViewModel,
                        trackHistoryViewModel = trackHistoryViewModel,
                        onTrackClick = { track ->
                            val dialog = TrackHistoryInfoDialog(track)
                            dialog.show(parentFragmentManager, TrackHistoryInfoDialog.FRAGMENT_TAG)
                        }
                    )
                }
            }
        }
    }

    fun init() {
        // Nothing special to init in Compose version, state is managed by ViewModel
    }

    fun setTouchInterceptListener(touchInterceptListener: TouchInterceptListener?) {
        this.touchInterceptListener = touchInterceptListener
    }

    fun resetScroll() {
        // Scrolling is managed by TrackList in Compose
    }

    val isScrolled: Boolean
        get() = false // Compose list handles its own nested scrolling
}
