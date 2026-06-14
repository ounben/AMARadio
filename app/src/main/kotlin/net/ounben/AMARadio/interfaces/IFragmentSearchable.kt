package net.ounben.AMARadio.interfaces

import net.ounben.AMARadio.station.StationsFilter

interface IFragmentSearchable {
    fun Search(searchStyle: StationsFilter.SearchStyle, query: String)
}
