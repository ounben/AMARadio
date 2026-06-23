package com.ounben.amaradio

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.ounben.amaradio.interfaces.IFragmentRefreshable
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.station.FragmentStations
import com.ounben.amaradio.station.StationsFilter

@Suppress("DEPRECATION")
class FragmentTabs : Fragment(), IFragmentRefreshable, IFragmentSearchable {
    private val itsAdressWWWLocal = "json/stations/bycountryexact/internet?order=clickcount&reverse=true"
    private val itsAdressWWWTopClick = "json/stations/topclick/100"
    private val itsAdressWWWTopVote = "json/stations/topvote/100"
    private val itsAdressWWWChangedLately = "json/stations/lastchange/100"
    private val itsAdressWWWCurrentlyHeard = "json/stations/lastclick/100"
    private val itsAdressWWWTags = "json/tags"
    private val itsAdressWWWCountries = "json/countrycodes"
    private val itsAdressWWWLanguages = "json/languages"

    private var queuedSearchQuery: String? = null
    private var queuedSearchStyle: StationsFilter.SearchStyle? = null

    private val fragments = arrayOfNulls<Fragment>(11)
    private var viewPager: ViewPager? = null
    private val addresses = arrayOf(
        itsAdressWWWLocal,
        itsAdressWWWTopClick,
        itsAdressWWWTopVote,
        itsAdressWWWChangedLately,
        itsAdressWWWCurrentlyHeard,
        itsAdressWWWTags,
        itsAdressWWWCountries,
        itsAdressWWWLanguages,
        "",
        "",
        ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val styleIdx = savedInstanceState.getInt("queuedSearchStyle", -1)
            if (styleIdx != -1) {
                queuedSearchStyle = StationsFilter.SearchStyle.entries[styleIdx]
            }
            queuedSearchQuery = savedInstanceState.getString("queuedSearchQuery")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Protection: only save if viewPager is still attached and has an adapter
        if (viewPager != null && viewPager?.adapter != null && !isRemoving) {
            outState.putInt("activeTabPosition", viewPager?.currentItem ?: 0)
        }
        queuedSearchStyle?.let { outState.putInt("queuedSearchStyle", it.ordinal) }
        outState.putString("queuedSearchQuery", queuedSearchQuery)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val x = inflater.inflate(R.layout.layout_tabs, container, false)
        val tabLayout = requireActivity().findViewById<TabLayout>(R.id.tabs)
        val vp = x.findViewById<ViewPager>(R.id.viewpager)
        viewPager = vp

        setupViewPager(vp)

        val activePos = savedInstanceState?.getInt("activeTabPosition", 0) ?: 0
        vp.post {
            if (activePos > 0 && activePos < (vp.adapter?.count ?: 0)) {
                vp.currentItem = activePos
            }
            
            queuedSearchQuery?.let {
                Log.d("TABS", "do queued search: $it")
                Search(queuedSearchStyle ?: StationsFilter.SearchStyle.ByName, it)
                // We keep the query for potential recreates, but clear it if search is complete
            }
        }

        tabLayout.post {
            if (context != null) {
                tabLayout.setupWithViewPager(vp)
            }
        }

        return x
    }

    override fun onResume() {
        super.onResume()
        val tabLayout = requireActivity().findViewById<TabLayout>(R.id.tabs)
        tabLayout.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        val tabLayout = requireActivity().findViewById<TabLayout>(R.id.tabs)
        tabLayout.visibility = View.GONE
    }

    private fun getCountryCode(): String? {
        val ctx = context
        if (ctx != null) {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            var countryCode = tm?.networkCountryIso
            Log.d("MAIN", "Network country code: '$countryCode'")
            if (countryCode != null && countryCode.length == 2) {
                return countryCode
            }
            countryCode = tm?.simCountryIso
            Log.d("MAIN", "Sim country code: '$countryCode'")
            if (countryCode != null && countryCode.length == 2) {
                return countryCode
            }
            countryCode = ctx.resources.configuration.locales[0].country
            addresses[IDX_LOCAL] = "json/stations/bycountrycodeexact/?order=clickcount&reverse=true"
            Log.d("MAIN", "Locale: '$countryCode'")
            if (countryCode != null && countryCode.length == 2) {
                return countryCode
            }
        }
        return null
    }

    private fun setupViewPager(viewPager: ViewPager) {
        val countryCode = getCountryCode()
        if (countryCode != null) {
            addresses[IDX_LOCAL] = "json/stations/bycountrycodeexact/$countryCode?order=clickcount&reverse=true"
        }

        // Create new instances first
        val newFragments = arrayOfNulls<Fragment>(11)
        newFragments[IDX_LOCAL] = FragmentStations()
        newFragments[IDX_TOP_CLICK] = FragmentStations()
        newFragments[IDX_TOP_VOTE] = FragmentStations()
        newFragments[IDX_CHANGED_LATELY] = FragmentStations()
        newFragments[IDX_CURRENTLY_HEARD] = FragmentStations()
        newFragments[IDX_TAGS] = FragmentCategories()
        newFragments[IDX_COUNTRIES] = FragmentCategories()
        newFragments[IDX_LANGUAGES] = FragmentCategories()
        newFragments[IDX_SEARCH] = FragmentStations()
        newFragments[IDX_FILTER] = FragmentFilter()

        // Setup arguments for all possible fragments
        for (i in newFragments.indices) {
            if (i == IDX_FILTER) continue
            val bundle = Bundle()
            bundle.putString("url", addresses[i])
            if (i == IDX_SEARCH) bundle.putBoolean(FragmentStations.KEY_SEARCH_ENABLED, true)
            if (i == IDX_TAGS) bundle.putInt("searchStyle", StationsFilter.SearchStyle.ByTagExact.ordinal)
            if (i == IDX_COUNTRIES) bundle.putInt("searchStyle", StationsFilter.SearchStyle.ByCountryCodeExact.ordinal)
            if (i == IDX_LANGUAGES) bundle.putInt("searchStyle", StationsFilter.SearchStyle.ByLanguageExact.ordinal)
            newFragments[i]?.arguments = bundle
        }

        val activeTabs = mutableListOf<Int>()
        if (countryCode != null) activeTabs.add(IDX_LOCAL)
        activeTabs.add(IDX_FILTER)
        activeTabs.add(IDX_TOP_CLICK)
        activeTabs.add(IDX_CURRENTLY_HEARD)
        activeTabs.add(IDX_COUNTRIES)
        activeTabs.add(IDX_SEARCH)

        val fm = childFragmentManager
        val adapter = ViewPagerAdapter(fm)

        for (tabId in activeTabs) {
            // ViewPager uses positions 0, 1, 2... for its tags in the adapter
            val position = adapter.count
            val tag = "android:switcher:${R.id.viewpager}:$position"
            val existing = fm.findFragmentByTag(tag)
            val fragment = existing ?: newFragments[tabId]!!
            fragments[tabId] = fragment
            
            val titleRes = when (tabId) {
                IDX_LOCAL -> R.string.action_local
                IDX_TOP_CLICK -> R.string.action_top_click
                IDX_TOP_VOTE -> R.string.action_top_vote
                IDX_CHANGED_LATELY -> R.string.action_changed_lately
                IDX_CURRENTLY_HEARD -> R.string.action_currently_playing
                IDX_TAGS -> R.string.action_tags
                IDX_COUNTRIES -> R.string.action_countries
                IDX_LANGUAGES -> R.string.action_languages
                IDX_SEARCH -> R.string.action_search
                IDX_FILTER -> R.string.action_filter
                else -> 0
            }
            adapter.addFragment(fragment, titleRes, tabId)
        }

        (fragments[IDX_TAGS] as? FragmentCategories)?.EnableSingleUseFilter(true)
        (fragments[IDX_TAGS] as? FragmentCategories)?.SetBaseSearchLink(StationsFilter.SearchStyle.ByTagExact)
        (fragments[IDX_COUNTRIES] as? FragmentCategories)?.SetBaseSearchLink(StationsFilter.SearchStyle.ByCountryCodeExact)
        (fragments[IDX_LANGUAGES] as? FragmentCategories)?.SetBaseSearchLink(StationsFilter.SearchStyle.ByLanguageExact)

        viewPager.adapter = adapter
    }

    override fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        Log.d("TABS", "Search = $query searchStyle=$searchStyle")
        if (viewPager != null && viewPager?.adapter is ViewPagerAdapter) {
            val adapter = viewPager?.adapter as ViewPagerAdapter
            val searchPosition = adapter.getPositionForTabId(IDX_SEARCH)

            if (searchPosition != -1) {
                viewPager?.currentItem = searchPosition
                (fragments[IDX_SEARCH] as IFragmentSearchable).Search(searchStyle, query)
            }
        } else {
            Log.d("TABS", "b Search = $query")
            queuedSearchQuery = query
            queuedSearchStyle = searchStyle
        }
    }

    fun openFilterTab() {
        if (viewPager != null && viewPager?.adapter is ViewPagerAdapter) {
            val adapter = viewPager?.adapter as ViewPagerAdapter
            val filterPosition = adapter.getPositionForTabId(IDX_FILTER)

            if (filterPosition != -1) {
                viewPager?.currentItem = filterPosition
                // Explicitly tell the fragment to expand when the menu icon is clicked
                (fragments[IDX_FILTER] as? FragmentFilter)?.expandFilter()
            }
        }
    }

    override fun Refresh() {
        val fragment = fragments[viewPager?.currentItem ?: 0]
        if (fragment is FragmentBase) {
            fragment.DownloadUrl(true)
        }
    }

    @Suppress("DEPRECATION")
    internal inner class ViewPagerAdapter(manager: FragmentManager) : FragmentPagerAdapter(manager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        private val mFragmentList: MutableList<Fragment> = ArrayList()
        private val mFragmentTitleList: MutableList<Int> = ArrayList()
        private val mTabsMapping: MutableList<Int> = ArrayList()

        override fun getItem(position: Int): Fragment {
            return mFragmentList[position]
        }

        override fun getCount(): Int {
            return mFragmentList.size
        }

        fun addFragment(fragment: Fragment, title: Int, tabId: Int) {
            mFragmentList.add(fragment)
            mFragmentTitleList.add(title)
            mTabsMapping.add(tabId)
        }

        override fun getPageTitle(position: Int): CharSequence {
            return resources.getString(mFragmentTitleList[position])
        }

        fun getPositionForTabId(tabId: Int): Int {
            return mTabsMapping.indexOf(tabId)
        }
    }

    companion object {
        private const val IDX_LOCAL = 0
        private const val IDX_TOP_CLICK = 1
        private const val IDX_TOP_VOTE = 2
        private const val IDX_CHANGED_LATELY = 3
        private const val IDX_CURRENTLY_HEARD = 4
        private const val IDX_TAGS = 5
        private const val IDX_COUNTRIES = 6
        private const val IDX_LANGUAGES = 7
        private const val IDX_SEARCH = 8
        private const val IDX_FILTER = 9
    }
}