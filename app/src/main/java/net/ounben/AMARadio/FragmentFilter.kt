package net.ounben.AMARadio

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import net.ounben.AMARadio.data.DataCategory
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.station.ItemAdapterStation
import net.ounben.AMARadio.station.StationsFilter
import java.net.URLEncoder
import java.util.*

class FragmentFilter : FragmentBase() {
    private lateinit var autoCountry: AutoCompleteTextView
    private lateinit var autoLanguage: AutoCompleteTextView
    private lateinit var autoTag: AutoCompleteTextView
    private lateinit var spinnerSort: Spinner
    private lateinit var switchReverse: SwitchMaterial
    private lateinit var btnApply: Button
    
    private var rvStations: RecyclerView? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var layoutError: View? = null
    private var sharedPref: SharedPreferences? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var searchJob: Job? = null

    private val sortOptions = arrayOf("name", "votes", "clickcount", "lastchangetime")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_filter, container, false)
        
        sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        autoCountry = view.findViewById(R.id.autoCountry)
        autoLanguage = view.findViewById(R.id.autoLanguage)
        autoTag = view.findViewById(R.id.autoTag)
        spinnerSort = view.findViewById(R.id.spinnerSort)
        switchReverse = view.findViewById(R.id.switchReverse)
        btnApply = view.findViewById(R.id.btnApply)
        
        rvStations = view.findViewById(R.id.recyclerViewStations)
        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        layoutError = view.findViewById(R.id.layoutError)
        
        view.findViewById<Button>(R.id.btnRefresh)?.setOnClickListener { performSearch() }
        
        setupSortSpinner()
        loadSavedFilters()
        setupAdapters()

        autoCountry.setOnClickListener { autoCountry.showDropDown() }
        autoLanguage.setOnClickListener { autoLanguage.showDropDown() }
        autoTag.setOnClickListener { autoTag.showDropDown() }
        
        val adapter = ItemAdapterStation(requireActivity(), R.layout.list_item_station, StationsFilter.FilterType.GLOBAL)
        adapter.stationActionsListener = object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                val AMARadioApp = requireActivity().application as AMARadioApp
                Utils.showPlaySelection(AMARadioApp, station, requireActivity().supportFragmentManager)
            }
            override fun onStationSwiped(station: DataRadioStation) {}
            override fun onStationMoved(from: Int, to: Int) {}
            override fun onStationMoveFinished() {}
        }
        
        rvStations?.layoutManager = LinearLayoutManager(context)
        rvStations?.adapter = adapter
        rvStations?.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        
        btnApply.setOnClickListener {
            saveFilters()
            performSearch()
        }
        
        swipeRefreshLayout?.setOnRefreshListener {
            performSearch()
        }
        
        // Initial search if we have saved filters
        if (hasAnyFilter()) {
            performSearch()
        }

        return view
    }

    private fun setupSortSpinner() {
        val sortLabels = arrayOf(
            getString(R.string.sort_name),
            getString(R.string.sort_votes),
            getString(R.string.sort_clicks),
            getString(R.string.sort_lastchange)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = adapter
    }

    private fun loadSavedFilters() {
        autoCountry.setText(sharedPref?.getString("filter_country", ""))
        autoLanguage.setText(sharedPref?.getString("filter_language", ""))
        autoTag.setText(sharedPref?.getString("filter_tag", ""))
        val sortIdx = sortOptions.indexOf(sharedPref?.getString("filter_sort", "clickcount"))
        if (sortIdx != -1) spinnerSort.setSelection(sortIdx)
        switchReverse.isChecked = sharedPref?.getBoolean("filter_reverse", true) ?: true
    }

    private fun saveFilters() {
        sharedPref?.edit()?.apply {
            putString("filter_country", autoCountry.text.toString())
            putString("filter_language", autoLanguage.text.toString())
            putString("filter_tag", autoTag.text.toString())
            putString("filter_sort", sortOptions[spinnerSort.selectedItemPosition])
            putBoolean("filter_reverse", switchReverse.isChecked)
            apply()
        }
    }

    private fun hasAnyFilter(): Boolean {
        return autoCountry.text.isNotEmpty() || autoLanguage.text.isNotEmpty() || autoTag.text.isNotEmpty()
    }

    private fun setupAdapters() {
        scope.launch {
            val countries = withContext(Dispatchers.IO) { fetchCategories("json/countrycodes") }
            val languages = withContext(Dispatchers.IO) { fetchCategories("json/languages") }
            val tags = withContext(Dispatchers.IO) { fetchCategories("json/tags") }
            
            if (isAdded) {
                autoCountry.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, countries))
                autoLanguage.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, languages))
                autoTag.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tags))
            }
        }
    }

    private fun fetchCategories(url: String): List<String> {
        val AMARadioApp = requireActivity().application as AMARadioApp
        val result = Utils.downloadFeedRelative(AMARadioApp.httpClient, requireContext(), url, false, null)
        return DataCategory.DecodeJson(result).map { it.Name }.sorted()
    }

    private fun performSearch() {
        searchJob?.cancel()
        searchJob = scope.launch {
            swipeRefreshLayout?.isRefreshing = true
            layoutError?.visibility = View.GONE
            val AMARadioApp = requireActivity().application as AMARadioApp
            
            val params = mutableMapOf<String, String>()
            val country = autoCountry.text.toString()
            val language = autoLanguage.text.toString()
            val tag = autoTag.text.toString()
            
            if (country.isNotEmpty()) params["countrycode"] = country
            if (language.isNotEmpty()) params["language"] = language
            if (tag.isNotEmpty()) params["tag"] = tag
            
            params["order"] = sortOptions[spinnerSort.selectedItemPosition]
            params["reverse"] = switchReverse.isChecked.toString()
            params["hidebroken"] = (!(sharedPref?.getBoolean("show_broken", false) ?: false)).toString()
            params["limit"] = "100" // Add a limit to prevent timeouts
            
            val resultString = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(AMARadioApp.httpClient, requireContext(), "json/stations/search", true, params)
            }
            
            if (resultString != null) {
                val stations = DataRadioStation.DecodeJson(resultString) ?: emptyList()
                (rvStations?.adapter as? ItemAdapterStation)?.updateList(null, stations)
                
                // Show error layout only if there was a real failure (resultString == null)
                // If stations is empty, maybe show a "no results" message instead of "cannot connect"
                layoutError?.visibility = View.GONE 
                if (stations.isEmpty() && isAdded) {
                    Toast.makeText(context, R.string.searchpreference_no_results, Toast.LENGTH_SHORT).show()
                }
            } else {
                layoutError?.visibility = View.VISIBLE
            }
            
            swipeRefreshLayout?.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "FragmentFilter"
    }
}
