package com.ounben.amaradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.station.SearchStyle
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.LocalStationsViewModel
import com.ounben.amaradio.ui.StationList
import com.ounben.amaradio.ui.SingleTabContainer
import androidx.compose.runtime.*

class FragmentStarred : Fragment(), IFragmentSearchable {

    override fun onResume() {
        super.onResume()
        val app = requireActivity().application as AMARadioApp
        val viewModel: LocalStationsViewModel = androidx.lifecycle.ViewModelProvider(requireActivity(), LocalStationsViewModelFactory(app, false)).get("starred", LocalStationsViewModel::class.java)
        viewModel.refreshGridMode()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AMARadioTheme {
                    val app = requireActivity().application as AMARadioApp
                    val viewModel: LocalStationsViewModel = viewModel(
                        key = "starred",
                        factory = LocalStationsViewModelFactory(app, false),
                        viewModelStoreOwner = requireActivity()
                    )
                    val uiState by viewModel.uiState.collectAsState()
                    
                    SingleTabContainer(titleRes = R.string.nav_item_starred) {
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
    }

    override fun search(searchStyle: SearchStyle, query: String) {
        val app = requireActivity().application as AMARadioApp
        val viewModel: LocalStationsViewModel = androidx.lifecycle.ViewModelProvider(requireActivity(), LocalStationsViewModelFactory(app, false)).get("starred", LocalStationsViewModel::class.java)
        viewModel.search(query)
    }
}

class LocalStationsViewModelFactory(private val app: AMARadioApp, isHistory: Boolean) : androidx.lifecycle.ViewModelProvider.Factory {
    private val isHistoryFlag = isHistory
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return LocalStationsViewModel(app, isHistoryFlag) as T
    }
}
