package com.ounben.amaradio.station

import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.FragmentBase
import com.ounben.amaradio.R
import com.ounben.amaradio.StationSaveManager
import com.ounben.amaradio.Utils
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.utils.CustomFilter

class FragmentStations : FragmentBase(), IFragmentSearchable {
    private var rvStations: RecyclerView? = null
    private var layoutError: ViewGroup? = null
    private var btnRetry: MaterialButton? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    private var sharedPref: SharedPreferences? = null

    private var searchEnabled = false

    private var stationsFilter: StationsFilter? = null
    private var lastSearchStyle = StationsFilter.SearchStyle.ByName
    private var lastQuery: String? = ""
    private var queue: StationSaveManager? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("lastQuery", lastQuery)
        outState.putInt("lastSearchStyle", lastSearchStyle.ordinal)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            lastQuery = savedInstanceState.getString("lastQuery", "")
            val styleIdx = savedInstanceState.getInt("lastSearchStyle", 0)
            lastSearchStyle = StationsFilter.SearchStyle.entries[styleIdx]
        }
    }

    private fun onStationClick(theStation: DataRadioStation) {
        val app = requireActivity().application as AMARadioApp
        Utils.showPlaySelection(app, theStation, requireActivity().supportFragmentManager)
    }

    override fun refreshListGui() {
        val rv = rvStations ?: return
        
        // Caching is disabled for Search fragments to ensure fresh results when selecting different countries/tags
        if (searchEnabled && !hasUrl()) {
            stationsFilter?.filter(lastQuery ?: "")
            return
        }

        if (!hasUrl()) return

        val ctx = context ?: return
        if (Utils.isDebug) Log.d(TAG, "refreshing the stations list.")

        if (sharedPref == null) {
            sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        }

        val showBroken = sharedPref?.getBoolean("show_broken", false) ?: false

        val filteredStationsList = ArrayList<DataRadioStation>()
        val radioStations = DataRadioStation.DecodeJson(getUrlResult()) ?: emptyList()
        queue?.clear()
        queue?.addAll(radioStations)

        if (Utils.isDebug) Log.d(TAG, "station count: ${radioStations.size}")

        for (station in radioStations) {
            if (showBroken || station.Working) {
                filteredStationsList.add(station)
            }
        }

        val adapter = rv.adapter as? ItemAdapterStation
        if (adapter != null) {
            adapter.updateList(null, filteredStationsList)
            if (searchEnabled && lastQuery.isNullOrEmpty()) {
                stationsFilter?.filter("")
            } else if (searchEnabled && !lastQuery.isNullOrEmpty()) {
                stationsFilter?.filter(lastQuery)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        Log.d("STATIONS", "onCreateView()")
        queue = StationSaveManager(requireContext())
        val bundle = arguments
        if (bundle != null) {
            searchEnabled = bundle.getBoolean(KEY_SEARCH_ENABLED, false)
        }

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_stations_remote, container, false)
        rvStations = view.findViewById(R.id.recyclerViewStations)
        layoutError = view.findViewById(R.id.layoutError)
        btnRetry = view.findViewById(R.id.btnRefresh)

        val adapter = Utils.createStationAdapter(requireActivity(), StationsFilter.FilterType.GLOBAL)
        adapter.stationActionsListener = object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                this@FragmentStations.onStationClick(station)
            }

            override fun onStationSwiped(station: DataRadioStation) {}
            override fun onStationMoved(from: Int, to: Int) {}
            override fun onStationMoveFinished() {}
        }

        if (searchEnabled) {
            stationsFilter = adapter.getFilter()

            stationsFilter?.setDelayer(object : CustomFilter.Delayer {
                private var previousLength = 0

                override fun getPostingDelay(constraint: CharSequence?): Long {
                    if (constraint == null) return 0

                    var delay: Long = 0
                    if (constraint.length < previousLength) {
                        delay = 500
                    }
                    previousLength = constraint.length

                    return delay
                }
            })

            adapter.setFilterListener { searchStatus ->
                layoutError?.visibility = if (searchStatus == StationsFilter.SearchStatus.ERROR) View.VISIBLE else View.GONE
                AppEventManager.sendEvent(android.content.Intent(ActivityMain.ACTION_HIDE_LOADING))
                swipeRefreshLayout?.isRefreshing = false
            }

            btnRetry?.setOnClickListener { Search(lastSearchStyle, lastQuery ?: "") }
        }

        rvStations?.let { Utils.setupStationRecyclerView(requireContext(), it, adapter) }

        if (adapter is ItemAdapterIconOnlyStation) {
            adapter.enableItemMove(rvStations!!)
        } else {
            // For remote lists, we don't want swipe-to-delete, but we want drag-to-open-menu
            adapter.enableItemMove(rvStations!!)
        }

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        swipeRefreshLayout?.setOnRefreshListener {
            if (hasUrl()) {
                downloadUrl(forceUpdate = true, displayProgress = false)
            } else if (searchEnabled) {
                // force refresh
                stationsFilter?.clearList()
                Search(lastSearchStyle, lastQuery ?: "")
            }
        }

        refreshListGui()

        if (lastQuery != null && stationsFilter != null) {
            Log.d("STATIONS", "do queued search for: $lastQuery style=$lastSearchStyle")
            stationsFilter?.clearList()
            Search(lastSearchStyle, lastQuery!!)
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rvStations?.adapter = null
    }

    override fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        Log.d("STATIONS", "query = $query searchStyle=$searchStyle")
        lastQuery = query
        lastSearchStyle = searchStyle

        if (rvStations != null && searchEnabled) {
            Log.d("STATIONS", "query a = $query")
            if (!TextUtils.isEmpty(query)) {
                AppEventManager.sendEvent(android.content.Intent(android.content.Intent(ActivityMain.ACTION_SHOW_LOADING)))
            }

            stationsFilter?.setSearchStyle(searchStyle)
            stationsFilter?.filter(query)
        } else {
            Log.d("STATIONS", "query b = $query $searchEnabled ")
        }
    }

    override fun downloadFinished() {
        swipeRefreshLayout?.isRefreshing = false
    }

    companion object {
        private const val TAG = "FragmentStations"
        const val KEY_SEARCH_ENABLED = "SEARCH_ENABLED"
    }
}
