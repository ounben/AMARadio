package net.ounben.AMARadio.station

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import me.xdrop.fuzzywuzzy.FuzzySearch
import net.ounben.AMARadio.AMARadioApp
import net.ounben.AMARadio.Utils
import net.ounben.AMARadio.utils.CustomFilter
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*

class StationsFilter(private val context: Context, private val filterType: FilterType, private val dataProvider: DataProvider) : CustomFilter() {
    enum class FilterType {
        LOCAL, GLOBAL
    }

    enum class SearchStatus {
        SUCCESS, ERROR
    }

    enum class SearchStyle {
        ByName, ByLanguageExact, ByCountryCodeExact, ByTagExact
    }

    interface DataProvider {
        fun getOriginalStationList(): List<DataRadioStation>
        fun notifyFilteredStationsChanged(status: SearchStatus, filteredStations: List<DataRadioStation>)
    }

    private var lastRemoteQuery = ""
    private var filteredStationsList: List<DataRadioStation>? = null
    private var lastRemoteSearchStatus = SearchStatus.SUCCESS
    private var searchStyle = SearchStyle.ByName

    private inner class WeightedStation(val station: DataRadioStation, val weight: Int)

    fun setSearchStyle(searchStyle: SearchStyle) {
        Log.d("FILTER", "Changed search style:$searchStyle")
        this.searchStyle = searchStyle
    }

    private fun searchGlobal(query: String): List<DataRadioStation> {
        Log.d("FILTER", "searchGlobal 1:$query")
        val AMARadioApp = context.applicationContext as AMARadioApp
        val httpClient = AMARadioApp.httpClient
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.context)
        val showBroken = sharedPref.getBoolean("show_broken", false)
        val p = HashMap<String, String>()
        p["order"] = "clickcount"
        p["reverse"] = "true"
        p["hidebroken"] = (!showBroken).toString()

        return try {
            val queryEncoded = URLEncoder.encode(query, "utf-8").replace("+", "%20")
            val searchUrl = when (searchStyle) {
                SearchStyle.ByName -> "json/stations/byname/$queryEncoded"
                SearchStyle.ByCountryCodeExact -> "json/stations/bycountrycodeexact/$queryEncoded"
                SearchStyle.ByLanguageExact -> "json/stations/bylanguageexact/$queryEncoded"
                SearchStyle.ByTagExact -> "json/stations/bytagexact/$queryEncoded"
            }
            Log.d("FILTER", "searchGlobal 2:$query")
            val resultString = Utils.downloadFeedRelative(httpClient, context, searchUrl, false, p)
            if (resultString != null) {
                Log.d("FILTER", "searchGlobal 3a:$query")
                val result = DataRadioStation.DecodeJson(resultString) ?: emptyList()
                lastRemoteSearchStatus = SearchStatus.SUCCESS
                result
            } else {
                Log.d("FILTER", "searchGlobal 3b:$query")
                lastRemoteSearchStatus = SearchStatus.ERROR
                ArrayList()
            }
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            lastRemoteSearchStatus = SearchStatus.ERROR
            ArrayList()
        }
    }

    fun clearList() {
        Log.d("FILTER", "forced refetch")
        lastRemoteQuery = ""
    }

    override fun performFiltering(constraint: CharSequence?): FilterResults {
        val query = constraint?.toString()?.lowercase(Locale.ROOT) ?: ""
        Log.d("FILTER", "performFiltering() $query")
        if (searchStyle == SearchStyle.ByName && (query.isEmpty() || (query.length < 2 && filterType == FilterType.GLOBAL))) {
            Log.d("FILTER", "performFiltering() 2 $query")
            filteredStationsList = dataProvider.getOriginalStationList()
            lastRemoteQuery = ""
        } else {
            Log.d("FILTER", "performFiltering() 3 $query")
            val stationsToFilter: List<DataRadioStation>
            var needsFiltering = false
            if (lastRemoteQuery.isNotEmpty() && query.startsWith(lastRemoteQuery) && lastRemoteSearchStatus != SearchStatus.ERROR) {
                Log.d("FILTER", "performFiltering() 3a $query lastRemoteQuery=$lastRemoteQuery")
                stationsToFilter = filteredStationsList ?: emptyList()
                needsFiltering = true
            } else {
                Log.d("FILTER", "performFiltering() 3b $query")
                when (filterType) {
                    FilterType.LOCAL -> {
                        stationsToFilter = dataProvider.getOriginalStationList()
                        needsFiltering = true
                    }
                    FilterType.GLOBAL -> {
                        stationsToFilter = searchGlobal(query)
                        needsFiltering = false
                        lastRemoteQuery = query
                    }
                }
            }
            if (needsFiltering) {
                Log.d("FILTER", "performFiltering() 4a $query")
                val filteredStations = ArrayList<WeightedStation>()
                for (station in stationsToFilter) {
                    val weight = FuzzySearch.partialRatio(query, station.Name.lowercase(Locale.ROOT))
                    if (weight > FUZZY_SEARCH_THRESHOLD) {
                        val compressedWeight = weight / 4
                        filteredStations.add(WeightedStation(station, compressedWeight))
                    }
                }
                filteredStations.sortWith { x, y ->
                    if (x.weight == y.weight) {
                        return@sortWith y.station.ClickCount.compareTo(x.station.ClickCount)
                    }
                    y.weight.compareTo(x.weight)
                }
                val resultList = ArrayList<DataRadioStation>()
                for (weightedStation in filteredStations) {
                    resultList.add(weightedStation.station)
                }
                filteredStationsList = resultList
            } else {
                Log.d("FILTER", "performFiltering() 4b $query")
                filteredStationsList = stationsToFilter
            }
        }
        val filterResults = FilterResults()
        filterResults.values = filteredStationsList
        return filterResults
    }

    @Suppress("UNCHECKED_CAST")
    override fun publishResults(constraint: CharSequence?, results: FilterResults) {
        dataProvider.notifyFilteredStationsChanged(lastRemoteSearchStatus, results.values as List<DataRadioStation>)
    }

    companion object {
        private const val TAG = "StationsFilter"
        private const val FUZZY_SEARCH_THRESHOLD = 55
    }
}
