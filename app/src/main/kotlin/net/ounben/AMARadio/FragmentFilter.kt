package net.ounben.AMARadio

import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import net.ounben.AMARadio.data.DataCategory
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.station.ItemAdapterStation
import net.ounben.AMARadio.station.StationsFilter
import net.ounben.AMARadio.utils.UiScaler
import java.net.URLEncoder
import java.util.*

class FragmentFilter : FragmentBase() {
    
    data class FilterItem(val code: String, val label: String, val icon: Drawable? = null) {
        override fun toString(): String = label
    }

    /**
     * A specialized adapter that supports "contains" filtering instead of just "starts with".
     */
    private open class ContainsFilterAdapter<T>(context: android.content.Context, resource: Int, private val allItems: List<T>) 
        : ArrayAdapter<T>(context, resource, allItems) {
        
        private var filteredItems: List<T> = allItems

        override fun getCount(): Int = filteredItems.size
        override fun getItem(position: Int): T? = filteredItems[position]

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val results = FilterResults()
                    if (constraint.isNullOrEmpty()) {
                        results.values = allItems
                        results.count = allItems.size
                    } else {
                        val filterPattern = constraint.toString().lowercase(Locale.ROOT).trim()
                        
                        // Filter and sort by relevance: 
                        val match = allItems.filter { 
                            it.toString().lowercase(Locale.ROOT).contains(filterPattern) 
                        }.sortedByDescending { 
                            val itemStr = it.toString().lowercase(Locale.ROOT)
                            var score = 0
                            if (itemStr == filterPattern) score = 1000
                            else if (itemStr.startsWith(filterPattern)) score = 500
                            else if (itemStr.contains(" $filterPattern")) score = 250
                            else score = 100
                            score
                        }

                        results.values = match
                        results.count = match.size
                    }
                    return results
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    filteredItems = results?.values as? List<T> ?: allItems
                    if (results != null && results.count > 0) {
                        notifyDataSetChanged()
                    } else {
                        notifyDataSetInvalidated()
                    }
                }
            }
        }
    }

    private class FilterDropdownAdapter(context: android.content.Context, items: List<FilterItem>) 
        : ContainsFilterAdapter<FilterItem>(context, R.layout.list_item_filter_dropdown, items) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_filter_dropdown, parent, false)
            val item = getItem(position)
            
            view.findViewById<TextView>(R.id.textLabel).text = item?.label
            val iconView = view.findViewById<ImageView>(R.id.imageIcon)
            if (item?.icon != null) {
                iconView.visibility = View.VISIBLE
                iconView.setImageDrawable(item.icon)
            } else {
                iconView.visibility = View.GONE
            }
            return view
        }
    }

    private lateinit var autoName: AutoCompleteTextView
    private lateinit var autoCountry: AutoCompleteTextView
    private lateinit var autoLanguage: AutoCompleteTextView
    private lateinit var autoTag: AutoCompleteTextView
    private lateinit var spinnerSort: Spinner
    private lateinit var switchReverse: SwitchMaterial
    private lateinit var btnApply: Button
    private lateinit var appBarLayout: AppBarLayout
    
    private var selectedCountryCode: String = ""
    private var selectedLanguage: String = ""
    
    private var rvStations: RecyclerView? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var layoutError: View? = null
    private var sharedPref: SharedPreferences? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var searchJob: Job? = null

    private val sortOptions = arrayOf("name", "votes", "clickcount", "lastchangetime")

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selectedCountryCode", selectedCountryCode)
        outState.putString("selectedLanguage", selectedLanguage)
        // AutoCompleteTextViews usually save their own text if they have an ID, 
        // but we can be explicit if needed.
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (savedInstanceState != null) {
            selectedCountryCode = savedInstanceState.getString("selectedCountryCode", "")
            selectedLanguage = savedInstanceState.getString("selectedLanguage", "")
        }
        val view = inflater.inflate(R.layout.fragment_filter, container, false)
        
        sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        autoName = view.findViewById(R.id.autoName)
        autoCountry = view.findViewById(R.id.autoCountry)
        autoLanguage = view.findViewById(R.id.autoLanguage)
        autoTag = view.findViewById(R.id.autoTag)
        spinnerSort = view.findViewById(R.id.spinnerSort)
        switchReverse = view.findViewById(R.id.switchReverse)
        btnApply = view.findViewById(R.id.btnApply)
        appBarLayout = view.findViewById(R.id.appBarLayout)
        
        rvStations = view.findViewById(R.id.recyclerViewStations)
        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        layoutError = view.findViewById(R.id.layoutError)
        
        view.findViewById<Button>(R.id.btnRefresh)?.setOnClickListener { performSearch() }
        
        setupSortSpinner()
        loadSavedFilters()
        setupAdapters()

        autoCountry.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as FilterItem
            selectedCountryCode = item.code
            autoCountry.setText(item.label, false)
        }
        
        autoLanguage.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as FilterItem
            selectedLanguage = item.code
            autoLanguage.setText(item.label, false)
        }

        val countryWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) selectedCountryCode = ""
            }
        }
        val languageWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) selectedLanguage = ""
            }
        }
        autoCountry.addTextChangedListener(countryWatcher)
        autoLanguage.addTextChangedListener(languageWatcher)
        
        autoCountry.setOnClickListener { autoCountry.showDropDown() }
        autoLanguage.setOnClickListener { autoLanguage.showDropDown() }
        autoTag.setOnClickListener { autoTag.showDropDown() }
        
        val adapter = Utils.createStationAdapter(requireActivity(), StationsFilter.FilterType.GLOBAL)
        adapter.stationActionsListener = object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                val AMARadioApp = requireActivity().application as AMARadioApp
                Utils.showPlaySelection(AMARadioApp, station, requireActivity().supportFragmentManager)
            }
            override fun onStationSwiped(station: DataRadioStation) {}
            override fun onStationMoved(from: Int, to: Int) {}
            override fun onStationMoveFinished() {}
        }
        
        rvStations?.let { Utils.setupStationRecyclerView(requireContext(), it, adapter) }
        
        btnApply.setOnClickListener {
            saveFilters()
            performSearch(true)
        }
        
        swipeRefreshLayout?.setOnRefreshListener {
            performSearch(false)
        }
        
        // Initial search if we have saved filters - but keep menu expanded
        if (hasAnyFilter()) {
            performSearch(false)
        }

        applyUiScaling(view)

        return view
    }

    override fun onResume() {
        super.onResume()
        expandFilter()
    }

    fun expandFilter() {
        view?.post {
            if (::appBarLayout.isInitialized) {
                appBarLayout.setExpanded(true, true)
            }
        }
    }

    private fun applyUiScaling(view: View) {
        val scale = UiScaler.getScaleFactor(requireContext())
        if (scale == UiScaler.SCALE_STANDARD) return

        val buttonSize = (48 * resources.displayMetrics.density * scale).toInt()
        btnApply.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        btnApply.minimumHeight = buttonSize

        // Ensure dropdowns are big enough
        autoName.minimumHeight = buttonSize
        autoCountry.minimumHeight = buttonSize
        autoLanguage.minimumHeight = buttonSize
        autoTag.minimumHeight = buttonSize
        
        spinnerSort.minimumHeight = buttonSize
        switchReverse.minimumHeight = buttonSize
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
        autoName.setText(sharedPref?.getString("filter_name", ""), false)
        selectedCountryCode = sharedPref?.getString("filter_country_code", "") ?: ""
        selectedLanguage = sharedPref?.getString("filter_language_code", "") ?: ""
        
        // Find and set labels for the codes
        // Note: This might need the adapters to be loaded first, but we set technical code as fallback
        autoCountry.setText(sharedPref?.getString("filter_country_label", selectedCountryCode), false)
        autoLanguage.setText(sharedPref?.getString("filter_language_label", selectedLanguage), false)
        
        autoTag.setText(sharedPref?.getString("filter_tag", ""), false)
        val sortIdx = sortOptions.indexOf(sharedPref?.getString("filter_sort", "clickcount"))
        if (sortIdx != -1) spinnerSort.setSelection(sortIdx)
        switchReverse.isChecked = sharedPref?.getBoolean("filter_reverse", true) ?: true
    }

    private fun saveFilters() {
        sharedPref?.edit()?.apply {
            putString("filter_name", autoName.text.toString())
            putString("filter_country_code", selectedCountryCode)
            putString("filter_country_label", autoCountry.text.toString())
            putString("filter_language_code", selectedLanguage)
            putString("filter_language_label", autoLanguage.text.toString())
            putString("filter_tag", autoTag.text.toString())
            putString("filter_sort", sortOptions[spinnerSort.selectedItemPosition])
            putBoolean("filter_reverse", switchReverse.isChecked)
            apply()
        }
    }

    private fun hasAnyFilter(): Boolean {
        return autoName.text.isNotEmpty() || selectedCountryCode.isNotEmpty() || selectedLanguage.isNotEmpty() || autoTag.text.isNotEmpty()
    }

    private fun setupAdapters() {
        scope.launch {
            val countriesData = withContext(Dispatchers.IO) { fetchCategoriesRaw("json/countrycodes") }
            val languagesData = withContext(Dispatchers.IO) { fetchCategoriesRaw("json/languages") }
            // Load a very large number of tags to ensure specialized genres like "phonk" are included
            val tags = withContext(Dispatchers.IO) { fetchCategoriesRaw("json/tags?limit=10000").map { it.Name }.sorted() }
            
            val countryItems = countriesData.map { 
                val countryName = CountryCodeDictionary.instance.getCountryByCode(it.Name) ?: it.Name
                val flag = CountryFlagsLoader.instance.getFlag(requireContext(), it.Name)
                FilterItem(it.Name, "$countryName (${it.Name})", flag)
            }.sortedBy { it.label }
            
            val languageItems = languagesData.map {
                FilterItem(it.Name, it.Name.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() })
            }.sortedBy { it.label }
            
            if (isAdded) {
                autoCountry.setAdapter(FilterDropdownAdapter(requireContext(), countryItems))
                autoLanguage.setAdapter(FilterDropdownAdapter(requireContext(), languageItems))
                
                // Use the custom "contains" adapter for tags too
                val tagAdapter = ContainsFilterAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tags)
                autoTag.setAdapter(tagAdapter)
                autoTag.threshold = 1
                
                // Refresh labels if they were just codes
                if (selectedCountryCode.isNotEmpty()) {
                    countryItems.find { it.code == selectedCountryCode }?.let { autoCountry.setText(it.label, false) }
                }
                if (selectedLanguage.isNotEmpty()) {
                    languageItems.find { it.code == selectedLanguage }?.let { autoLanguage.setText(it.label, false) }
                }
            }
        }
    }

    private fun fetchCategoriesRaw(url: String): List<DataCategory> {
        val AMARadioApp = requireActivity().application as AMARadioApp
        val result = Utils.downloadFeedRelative(AMARadioApp.httpClient, requireContext(), url, false, null)
        return DataCategory.DecodeJson(result).toList()
    }

    private fun performSearch(collapseMenu: Boolean = false) {
        searchJob?.cancel()
        searchJob = scope.launch {
            // Programmatically collapse the filter menu only if requested (e.g. by Apply button)
            if (collapseMenu && ::appBarLayout.isInitialized) {
                appBarLayout.setExpanded(false, true)
            }

            swipeRefreshLayout?.isRefreshing = true
            layoutError?.visibility = View.GONE
            val AMARadioApp = requireActivity().application as AMARadioApp
            
            val params = mutableMapOf<String, String>()
            val name = autoName.text.toString()
            val country = selectedCountryCode
            val language = selectedLanguage
            val tag = autoTag.text.toString()
            
            if (name.isNotEmpty()) params["name"] = name
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
