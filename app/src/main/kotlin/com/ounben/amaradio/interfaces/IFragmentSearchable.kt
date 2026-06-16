package com.ounben.amaradio.interfaces

import com.ounben.amaradio.station.StationsFilter

interface IFragmentSearchable {
    fun Search(searchStyle: StationsFilter.SearchStyle, query: String)
}
