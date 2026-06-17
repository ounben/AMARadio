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
import com.google.android.material.snackbar.Snackbar
import com.ounben.amaradio.station.ItemAdapterStation
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.ItemAdapterIconOnlyStation
import com.ounben.amaradio.interfaces.IAdapterRefreshable
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.station.StationsFilter
import kotlinx.coroutines.*
import java.util.*

class FragmentHistory : Fragment(), IAdapterRefreshable, IFragmentSearchable {
    private lateinit var rvStations: RecyclerView
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var historyManager: HistoryManager
    private var stationsFilter: StationsFilter? = null
    private var lastQuery: String = ""

    private fun onStationClick(theStation: DataRadioStation) {
        val ctx = context ?: return
        val AMARadioApp = ctx.applicationContext as AMARadioApp
        Utils.showPlaySelection(AMARadioApp, theStation, parentFragmentManager)

        RefreshListGui()
        rvStations.smoothScrollToPosition(0)
    }

    override fun RefreshListGui() {
        if (Utils.isDebug) Log.d(TAG, "refreshing the stations list.")

        val adapter = rvStations.adapter as? ItemAdapterStation

        if (Utils.isDebug) Log.d(TAG, "stations count: ${historyManager.listStations.size}")

        adapter?.updateList(null, historyManager.listStations)
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
        historyManager = AMARadioApp.historyManager

        val adapter = Utils.createStationAdapter(requireActivity(), StationsFilter.FilterType.LOCAL)
        stationsFilter = adapter.getFilter()
        adapter.stationActionsListener = object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                this@FragmentHistory.onStationClick(station)
            }

            override fun onStationSwiped(station: DataRadioStation) {
                val removedIdx = historyManager.remove(station.StationUuid)

                val snackbar = Snackbar
                        .make(rvStations, R.string.notify_station_removed_from_list, 6000)
                snackbar.anchorView = requireView().rootView.findViewById(R.id.bottom_sheet)
                snackbar.setAction(R.string.action_station_removed_from_list_undo) {
                    historyManager.restore(station, removedIdx)
                }
                snackbar.show()
            }

            override fun onStationMoved(from: Int, to: Int) {}
            override fun onStationMoveFinished() {}
        }

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_stations, container, false)

        rvStations = view.findViewById(R.id.recyclerViewStations)
        Utils.setupStationRecyclerView(requireContext(), rvStations, adapter)

        if (adapter is ItemAdapterIconOnlyStation) {
            adapter.enableItemMove(rvStations)
        } else {
            adapter.enableItemMoveAndRemoval(rvStations)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyManager.stationsFlow.collect {
                    RefreshListGui()
                }
            }
        }

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        swipeRefreshLayout?.setOnRefreshListener {
            if (Utils.isDebug) {
                Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            }
            RefreshDownloadList()
        }

        RefreshListGui()

        return view
    }

    private fun RefreshDownloadList() {
        val ctx = context ?: return
        val AMARadioApp = ctx.applicationContext as AMARadioApp
        val httpClient = AMARadioApp.httpClient
        val listUUids = ArrayList<String>()
        for (station in historyManager.listStations) {
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
        for (stationCurrent in historyManager.listStations) {
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
        historyManager.replaceList(listNew)
        Log.d(TAG, "fin save")

        if (toRemove.size > 0) {
            Toast.makeText(context, resources.getString(R.string.notify_sync_list_deleted_entries, toRemove.size, historyManager.size()), Toast.LENGTH_LONG).show()
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
        private const val TAG = "FragmentHistory"
    }
}
