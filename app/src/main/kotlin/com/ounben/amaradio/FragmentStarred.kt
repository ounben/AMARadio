package com.ounben.amaradio

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ounben.amaradio.interfaces.IAdapterRefreshable
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.ItemAdapterIconOnlyStation
import com.ounben.amaradio.station.ItemAdapterStation
import com.ounben.amaradio.station.StationActions
import com.ounben.amaradio.station.StationsFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FragmentStarred : Fragment(), IAdapterRefreshable, IFragmentSearchable {
    private lateinit var rvStations: RecyclerView
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var favouriteManager: FavouriteManager
    private var stationsFilter: StationsFilter? = null
    private var lastQuery: String = ""

    private fun onStationClick(theStation: DataRadioStation) {
        val ctx = context ?: return
        val app = ctx.applicationContext as AMARadioApp
        Utils.showPlaySelection(app, theStation, parentFragmentManager)
    }

    override fun refreshListGui() {
        if (Utils.isDebug) Log.d(TAG, "refreshing the stations list.")

        val adapter = rvStations.adapter as? ItemAdapterStation

        if (Utils.isDebug) Log.d(TAG, "stations count: ${favouriteManager.listStations.size}")

        adapter?.updateList(this, favouriteManager.listStations)
        if (lastQuery.isNotEmpty()) {
            stationsFilter?.filter(lastQuery)
        }
    }

    override fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        lastQuery = query
        stationsFilter?.setDelayer(object : com.ounben.amaradio.utils.CustomFilter.Delayer {
            override fun getPostingDelay(constraint: CharSequence?): Long = 300
        })
        stationsFilter?.filter(query)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val AMARadioApp = requireActivity().application as AMARadioApp
        favouriteManager = AMARadioApp.favouriteManager

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_stations, container, false)
        rvStations = view.findViewById(R.id.recyclerViewStations)

        val adapter = Utils.createStationAdapter(requireActivity(), StationsFilter.FilterType.LOCAL)
        Utils.setupStationRecyclerView(requireContext(), rvStations, adapter)
        
        stationsFilter = adapter.getFilter()

        if (adapter is ItemAdapterIconOnlyStation) {
            adapter.enableItemMove(rvStations)
        } else {
            adapter.enableItemMoveAndRemoval(rvStations)
        }

        adapter.stationActionsListener = object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                this@FragmentStarred.onStationClick(station)
            }

            override fun onStationSwiped(station: DataRadioStation) {
                StationActions.removeFromFavourites(requireContext(), view, station)
            }

            override fun onStationMoved(from: Int, to: Int) {
                favouriteManager.moveWithoutNotify(from, to)
            }

            override fun onStationMoveFinished() {
                // We don't want to update RecyclerView during its layout process
                requireView().post {
                    favouriteManager.save()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favouriteManager.stationsFlow.collect {
                    refreshListGui()
                }
            }
        }

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        swipeRefreshLayout?.setOnRefreshListener {
            if (Utils.isDebug) {
                Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            }
            refreshDownloadList()
        }

        refreshListGui()

        return view
    }

    private fun refreshDownloadList() {
        val ctx = context ?: return
        val app = ctx.applicationContext as AMARadioApp
        val httpClient = app.httpClient
        val listUUids = ArrayList<String>()
        for (station in favouriteManager.listStations) {
            listUUids.add(station.StationUuid)
        }
        Log.d(TAG, "Search for items: " + listUUids.size)

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                Utils.getStationsByUuid(httpClient, ctx, listUUids)
            }

            downloadFinished()
            AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))
            
            if (Utils.isDebug) {
                Log.d(TAG, "Download finished")
            }

            if (result != null) {
                if (Utils.isDebug) {
                    Log.d(TAG, "Download OK")
                }
                Log.d(TAG, "Found items: " + result.size)
                syncList(result)
                refreshListGui()
            } else {
                try {
                    Toast.makeText(context, resources.getText(R.string.error_list_update), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ERR", e.toString())
                }
            }
        }
    }

    private fun syncList(listNew: List<DataRadioStation>) {
        val toRemove = ArrayList<String>()
        for (stationCurrent in favouriteManager.listStations) {
            var found = false
            for (stationNew in listNew) {
                if (stationNew.StationUuid == stationCurrent.StationUuid) {
                    found = true
                    break
                }
            }
            if (!found) {
                Log.d(TAG, "Remove station: " + stationCurrent.StationUuid + " - " + stationCurrent.Name)
                toRemove.add(stationCurrent.StationUuid)
                stationCurrent.DeletedOnServer = true
            }
        }
        Log.d(TAG, "replace items")
        favouriteManager.replaceList(listNew)
        Log.d(TAG, "fin save")

        if (toRemove.size > 0) {
            Toast.makeText(context, resources.getString(R.string.notify_sync_list_deleted_entries, toRemove.size, favouriteManager.size()), Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadFinished() {
        swipeRefreshLayout?.isRefreshing = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rvStations.adapter = null
        downloadJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FragmentStarred"
    }
}
