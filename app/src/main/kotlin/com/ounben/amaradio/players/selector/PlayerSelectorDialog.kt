package com.ounben.amaradio.players.selector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.R
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.launch

class PlayerSelectorDialog() : BottomSheetDialogFragment() {

    companion object {
        const val FRAGMENT_TAG = "player_selector_dialog_fragment"
    }

    private var stationToPlay: DataRadioStation? = null
    private var recyclerViewServers: RecyclerView? = null
    private var playerSelectorAdapter: PlayerSelectorAdapter? = null

    constructor(stationToPlay: DataRadioStation?) : this() {
        this.stationToPlay = stationToPlay
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        @Suppress("DEPRECATION")
        retainInstance = true
        val view = inflater.inflate(R.layout.dialog_player_selector, container, false)
        
        recyclerViewServers = view.findViewById(R.id.recyclerViewPlayers)
        val llm = GridLayoutManager(context, 1, RecyclerView.VERTICAL, false)
        recyclerViewServers?.layoutManager = llm
        
        playerSelectorAdapter = PlayerSelectorAdapter(requireContext(), stationToPlay)
        recyclerViewServers?.adapter = playerSelectorAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppEventManager.events.collect { intent ->
                    if (PlayerService.PLAYER_SERVICE_STATE_CHANGE == intent.action) {
                        playerSelectorAdapter?.notifyAMARadioPlaybackStateChanged()
                    }
                }
            }
        }

        return view
    }
}
