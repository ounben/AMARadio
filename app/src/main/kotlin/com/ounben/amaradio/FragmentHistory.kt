package com.ounben.amaradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.history.TrackHistoryInfoDialog
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.station.SearchStyle
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.HistoryScreen
import com.ounben.amaradio.ui.LocalStationsViewModel
import androidx.compose.runtime.*

class FragmentHistory : Fragment(), IFragmentSearchable {

    override fun onResume() {
        super.onResume()
        val app = requireActivity().application as AMARadioApp
        val viewModel: LocalStationsViewModel = androidx.lifecycle.ViewModelProvider(requireActivity(), LocalStationsViewModelFactory(app, true)).get("history", LocalStationsViewModel::class.java)
        viewModel.refreshGridMode()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AMARadioTheme {
                    val app = requireActivity().application as AMARadioApp
                    val localStationsViewModel: LocalStationsViewModel = viewModel(
                        key = "history",
                        factory = LocalStationsViewModelFactory(app, true),
                        viewModelStoreOwner = requireActivity()
                    )
                    val trackHistoryViewModel: TrackHistoryViewModel = viewModel()
                    
                    HistoryScreen(
                        localStationsViewModel = localStationsViewModel,
                        trackHistoryViewModel = trackHistoryViewModel,
                        onStationClick = { station -> Utils.showPlaySelection(app, station, parentFragmentManager) },
                        onFavoriteClick = { station ->
                            if (app.favouriteManager.has(station.StationUuid)) {
                                StationActions.removeFromFavourites(requireContext(), null, station)
                            } else {
                                StationActions.markAsFavourite(requireContext(), station)
                            }
                        },
                        onTrackClick = { track ->
                            val dialog = TrackHistoryInfoDialog(track)
                            dialog.show(parentFragmentManager, TrackHistoryInfoDialog.FRAGMENT_TAG)
                        },
                        isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                    )
                }
            }
        }
    }

    override fun search(searchStyle: SearchStyle, query: String) {
        val app = requireActivity().application as AMARadioApp
        val viewModel: LocalStationsViewModel = androidx.lifecycle.ViewModelProvider(requireActivity(), LocalStationsViewModelFactory(app, true)).get("history", LocalStationsViewModel::class.java)
        viewModel.search(query)
    }
}
