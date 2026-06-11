package net.ounben.AMARadio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import net.ounben.AMARadio.station.ItemAdapterStation
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.station.ItemAdapterIconOnlyStation
import net.ounben.AMARadio.interfaces.IAdapterRefreshable
import net.ounben.AMARadio.station.StationActions
import net.ounben.AMARadio.station.StationsFilter
import net.ounben.AMARadio.BuildConfig
import kotlinx.coroutines.*
import java.util.*

class FragmentStarred : Fragment(), IAdapterRefreshable, Observer {
    private lateinit var rvStations: RecyclerView
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var favouriteManager: FavouriteManager

    private fun onStationClick(theStation: DataRadioStation) {
        val AMARadioApp = requireActivity().application as AMARadioApp
        Utils.showPlaySelection(AMARadioApp, theStation, requireActivity().supportFragmentManager)
    }

    override fun RefreshListGui() {
        if (BuildConfig.DEBUG) Log.d(TAG, "refreshing the stations list.")

        val adapter = rvStations.adapter as? ItemAdapterStation

        if (BuildConfig.DEBUG) Log.d(TAG, "stations count: ${favouriteManager.listStations.size}")

        adapter?.updateList(this, favouriteManager.listStations)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val AMARadioApp = requireActivity().application as AMARadioApp
        favouriteManager = AMARadioApp.favouriteManager
        favouriteManager.addObserver(this)

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_stations, container, false)
        rvStations = view.findViewById(R.id.recyclerViewStations)

        val adapter = Utils.createStationAdapter(requireActivity(), StationsFilter.FilterType.LOCAL)
        Utils.setupStationRecyclerView(requireContext(), rvStations, adapter)
        
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
                    favouriteManager.Save()
                    favouriteManager.notifyObservers()
                }
            }
        }

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        swipeRefreshLayout?.setOnRefreshListener {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            }
            RefreshDownloadList()
        }

        RefreshListGui()

        return view
    }

    private fun RefreshDownloadList() {
        val AMARadioApp = requireActivity().application as AMARadioApp
        val httpClient = AMARadioApp.httpClient
        val listUUids = ArrayList<String>()
        for (station in favouriteManager.listStations) {
            listUUids.add(station.StationUuid)
        }
        Log.d(TAG, "Search for items: " + listUUids.size)

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                Utils.getStationsByUuid(httpClient, requireActivity(), listUUids)
            }

            downloadFinished()
            if (context != null)
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Download finished")
            }

            if (result != null) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Download OK")
                }
                Log.d(TAG, "Found items: " + result.size)
                syncList(result)
                RefreshListGui()
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
        favouriteManager.deleteObserver(this)
        downloadJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun update(o: Observable?, arg: Any?) {
        RefreshListGui()
    }

    companion object {
        private const val TAG = "FragmentStarred"
    }
}
