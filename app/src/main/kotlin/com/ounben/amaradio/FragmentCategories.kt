package com.ounben.amaradio

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ounben.amaradio.adapters.ItemAdapterCategory
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.station.StationsFilter

class FragmentCategories : FragmentBase() {
    private var rvCategories: RecyclerView? = null
    private var searchStyle = StationsFilter.SearchStyle.ByName
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var singleUseFilter = false
    private var sharedPref: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val styleIdx = savedInstanceState.getInt("searchStyle", 0)
            searchStyle = StationsFilter.SearchStyle.values()[styleIdx]
        } else {
            arguments?.let {
                val styleIdx = it.getInt("searchStyle", -1)
                if (styleIdx != -1) {
                    searchStyle = StationsFilter.SearchStyle.values()[styleIdx]
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("searchStyle", searchStyle.ordinal)
    }

    fun SetBaseSearchLink(searchStyle: StationsFilter.SearchStyle) {
        this.searchStyle = searchStyle
    }

    private fun clickOnItem(theData: DataCategory) {
        val m = activity as? ActivityMain
        m?.Search(this.searchStyle, theData.Name)
    }

    override fun RefreshListGui() {
        val rv = rvCategories ?: return
        val ctx = context ?: return

        if (Utils.isDebug) Log.d(TAG, "refreshing the categories list.")

        if (sharedPref == null) {
            sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        }

        val showSingleUseTags = sharedPref?.getBoolean("single_use_tags", false) ?: false

        val filteredCategoriesList = ArrayList<DataCategory>()
        val data = DataCategory.DecodeJson(getUrlResult()) ?: emptyArray()

        if (Utils.isDebug) Log.d(TAG, "categories count: ${data.size}")
        val countryDict = CountryCodeDictionary.instance
        val flagsDict = CountryFlagsLoader.instance

        for (aData in data) {
            if (!singleUseFilter || showSingleUseTags || aData.UsedCount > 1) {
                if (searchStyle == StationsFilter.SearchStyle.ByCountryCodeExact) {
                    aData.Label = countryDict.getCountryByCode(aData.Name)
                    context?.let { aData.Icon = flagsDict.getFlag(it, aData.Name) }
                }
                filteredCategoriesList.add(aData)
            }
        }

        if (searchStyle == StationsFilter.SearchStyle.ByCountryCodeExact) {
            filteredCategoriesList.sort()
        }
        val adapter = rv.adapter as? ItemAdapterCategory
        adapter?.updateList(filteredCategoriesList)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        val layoutId = if (isGrid) R.layout.list_item_category_grid else R.layout.list_item_category
        
        val adapterCategory = ItemAdapterCategory(layoutId)
        adapterCategory.setCategoryClickListener { category -> clickOnItem(category) }

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_stations_remote, container, false)

        rvCategories = view.findViewById(R.id.recyclerViewStations)
        rvCategories?.let { Utils.setupStationRecyclerView(requireContext(), it, adapterCategory) }

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        swipeRefreshLayout?.setOnRefreshListener {
            if (Utils.isDebug) {
                Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            }
            DownloadUrl(true, false)
        }

        RefreshListGui()

        return view
    }

    fun EnableSingleUseFilter(b: Boolean) {
        this.singleUseFilter = b
    }

    override fun DownloadFinished() {
        swipeRefreshLayout?.isRefreshing = false
    }

    companion object {
        private const val TAG = "FragmentCategories"
    }
}
