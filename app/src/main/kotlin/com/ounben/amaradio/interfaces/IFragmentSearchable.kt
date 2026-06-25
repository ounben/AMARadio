package com.ounben.amaradio.interfaces

import com.ounben.amaradio.station.SearchStyle

interface IFragmentSearchable {
    fun search(searchStyle: SearchStyle, query: String)
}
