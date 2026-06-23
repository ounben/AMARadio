package com.ounben.amaradio.interfaces

import com.ounben.amaradio.station.StationsFilter

interface IFragmentSearchable {
    fun search(searchStyle: StationsFilter.SearchStyle, query: String)
}
