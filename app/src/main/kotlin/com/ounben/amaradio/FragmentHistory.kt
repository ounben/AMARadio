package com.ounben.amaradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.station.StationsFilter
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.LocalStationsViewModel
import com.ounben.amaradio.ui.StationList
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
                    val viewModel: LocalStationsViewModel = viewModel(
                        key = "history",
                        factory = LocalStationsViewModelFactory(app, true),
                        viewModelStoreOwner = requireActivity()
                    )
                    val uiState by viewModel.uiState.collectAsState()
                    
                    StationList(
                        stations = uiState.filteredStations,
                        isGrid = uiState.isGrid,
                        onStationClick = { station -> Utils.showPlaySelection(app, station, parentFragmentManager) },
                        onFavoriteClick = { station ->
                            if (app.favouriteManager.has(station.StationUuid)) {
                                StationActions.removeFromFavourites(requireContext(), null, station)
                            } else {
                                StationActions.markAsFavourite(requireContext(), station)
                            }
                        },
                        isFavorite = { uuid -> app.favouriteManager.has(uuid) }
                    )
                }
            }
        }
    }

    override fun search(searchStyle: StationsFilter.SearchStyle, query: String) {
        val app = requireActivity().application as AMARadioApp
        val viewModel: LocalStationsViewModel = androidx.lifecycle.ViewModelProvider(requireActivity(), LocalStationsViewModelFactory(app, true)).get("history", LocalStationsViewModel::class.java)
        viewModel.search(query)
    }
}
