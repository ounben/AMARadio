package com.ounben.amaradio.station

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
import com.ounben.amaradio.utils.CustomFilter
import java.net.URLEncoder
import java.util.Locale

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

    private class WeightedStation(val station: DataRadioStation, val weight: Int)

    fun setSearchStyle(searchStyle: SearchStyle) {
        Log.d("FILTER", "Changed search style:$searchStyle")
        this.searchStyle = searchStyle
    }

    private suspend fun searchGlobal(query: String): List<DataRadioStation> {
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
            
            Log.d("FILTER", "searchGlobal 2:$query URL:$searchUrl")
            val resultString = Utils.downloadFeedRelative(httpClient, context, searchUrl, false, p)
            if (resultString != null) {
                val result = DataRadioStation.DecodeJson(resultString) ?: emptyList()
                lastRemoteSearchStatus = SearchStatus.SUCCESS
                result
            } else {
                lastRemoteSearchStatus = SearchStatus.ERROR
                ArrayList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            lastRemoteSearchStatus = SearchStatus.ERROR
            ArrayList()
        }
    }

    fun clearList() {
        Log.d("FILTER", "forced refetch")
        lastRemoteQuery = ""
    }

    override suspend fun performFiltering(constraint: CharSequence?): FilterResults {
        val query = constraint?.toString()?.lowercase(Locale.ROOT) ?: ""
        Log.d("FILTER", "performFiltering() $query")
        
        var results: List<DataRadioStation> = emptyList()
        
        if (searchStyle == SearchStyle.ByName && (query.isEmpty() || (query.length < 2 && filterType == FilterType.GLOBAL))) {
            results = dataProvider.getOriginalStationList()
            lastRemoteQuery = ""
        } else {
            when (filterType) {
                FilterType.LOCAL -> {
                    val stationsToFilter = dataProvider.getOriginalStationList()
                    val filteredStations = ArrayList<WeightedStation>()
                    val lowerQuery = query.lowercase(Locale.ROOT)
                    
                    for (station in stationsToFilter) {
                        val nameLower = station.Name.lowercase(Locale.ROOT)
                        var score = 0
                        
                        if (nameLower == lowerQuery) {
                            score = 10000
                        } else if (nameLower.startsWith(lowerQuery)) {
                            score = 5000
                        } else if (nameLower.contains(" $lowerQuery")) {
                            score = 2500
                        } else if (nameLower.contains(lowerQuery)) {
                            score = 1000
                        }
                        
                        if (score > 0) {
                            filteredStations.add(WeightedStation(station, score))
                        }
                    }
                    
                    filteredStations.sortByDescending { it.weight }
                    results = filteredStations.map { it.station }
                }
                FilterType.GLOBAL -> {
                    // Für globale Suche: Den Server fragen
                    val remoteStations = searchGlobal(query)
                    lastRemoteQuery = query
                    
                    // Ergebnisse lokal nach Relevanz nachsortieren
                    val filteredStations = ArrayList<WeightedStation>()
                    val lowerQuery = query.lowercase(Locale.ROOT)
                    
                    for (station in remoteStations) {
                        val nameLower = station.Name.lowercase(Locale.ROOT)
                        var score = 0
                        
                        if (nameLower == lowerQuery) {
                            score = 10000 // Exakter Treffer
                        } else if (nameLower.startsWith(lowerQuery)) {
                            score = 5000  // Beginnt mit Query
                        } else if (nameLower.contains(" $lowerQuery")) {
                            score = 2500  // Ein Wort beginnt mit Query
                        } else if (nameLower.contains(lowerQuery)) {
                            score = 1000  // Enthält Query irgendwo
                        }
                        
                        // Klicks als Tie-Breaker (max 500 Punkte Bonus)
                        val clickBonus = Math.min(station.ClickCount / 100, 500)
                        filteredStations.add(WeightedStation(station, score + clickBonus))
                    }
                    
                    // Nach Score absteigend sortieren
                    filteredStations.sortByDescending { it.weight }
                    
                    results = filteredStations.map { it.station }
                }
            }
        }

        filteredStationsList = results
        val filterResults = FilterResults()
        filterResults.values = results
        filterResults.count = results.size
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
