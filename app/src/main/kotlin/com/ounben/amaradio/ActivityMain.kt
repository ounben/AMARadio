package com.ounben.amaradio

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.players.selector.PlayerType
import com.ounben.amaradio.service.MediaSessionCallback
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.SearchStyle
import com.ounben.amaradio.utils.UiScaler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

class ActivityMain : AppCompatActivity(), NavigationBarView.OnItemSelectedListener,
    NavigationView.OnNavigationItemSelectedListener, SearchView.OnQueryTextListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScaler.wrapContext(newBase))
    }

    private var mSearchView: SearchView? = null
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var tabsView: TabLayout
    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var mNavigationView: NavigationView
    private lateinit var mBottomNavigationView: BottomNavigationView
    private lateinit var mFragmentManager: FragmentManager
    private lateinit var containerView: View
    private lateinit var playerBottomSheet: BottomSheetBehavior<View>
    private var smallPlayerFragment: FragmentPlayerSmall? = null
    private var fullPlayerFragment: FragmentPlayerFull? = null
    private var menuItemSearch: MenuItem? = null
    private var menuItemDelete: MenuItem? = null
    private var menuItemSleepTimer: MenuItem? = null
    private var menuItemSave: MenuItem? = null
    private var menuItemLoad: MenuItem? = null
    private var menuItemIconsView: MenuItem? = null
    private var menuItemListView: MenuItem? = null
    private var menuItemFilter: MenuItem? = null
    private lateinit var sharedPref: SharedPreferences
    private var selectedMenuItem = 0
    private var instanceStateWasSaved = false
    private var lastExitTry: Date? = null
    private var meteredConnectionAlertDialog: AlertDialog? = null
    private var isSearchActive = false
    private var lastSearchQuery: String? = null
    private var preRenderJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Utils.getThemeResId(this))
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            isSearchActive = savedInstanceState.getBoolean("isSearchActive", false)
            lastSearchQuery = savedInstanceState.getString("lastSearchQuery")
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        setContentView(R.layout.layout_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_bar_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setSupportActionBar(findViewById(R.id.my_awesome_toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        if (Utils.isDarkTheme(this)) {
            findViewById<TextView>(R.id.toolbar_custom_title)?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary))
        }

        PlayerServiceUtil.startService(applicationContext)

        selectedMenuItem = sharedPref.getInt("last_selectedMenuItem", -1)
        instanceStateWasSaved = savedInstanceState != null
        mFragmentManager = supportFragmentManager

        appBarLayout = findViewById(R.id.app_bar_layout)
        tabsView = findViewById(R.id.tabs)
        mDrawerLayout = findViewById(R.id.drawerLayout)
        mNavigationView = findViewById(R.id.my_navigation_view)
        mBottomNavigationView = findViewById(R.id.bottom_navigation)
        containerView = findViewById(R.id.containerView)

        if (Utils.bottomNavigationEnabled(this)) {
            mBottomNavigationView.setOnItemSelectedListener(this)
            mNavigationView.visibility = View.GONE
            mNavigationView.layoutParams.width = 0
        } else {
            mNavigationView.setNavigationItemSelectedListener(this)
            mBottomNavigationView.visibility = View.GONE

            val mDrawerToggle = ActionBarDrawerToggle(this, mDrawerLayout, R.string.app_name, R.string.app_name)
            mDrawerLayout.addDrawerListener(mDrawerToggle)
            mDrawerToggle.syncState()

            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
        }

        smallPlayerFragment = mFragmentManager.findFragmentById(R.id.fragment_player_small) as? FragmentPlayerSmall
        fullPlayerFragment = mFragmentManager.findFragmentById(R.id.fragment_player_full) as? FragmentPlayerFull

        if (smallPlayerFragment == null || fullPlayerFragment == null) {
            smallPlayerFragment = FragmentPlayerSmall()
            fullPlayerFragment = FragmentPlayerFull()

            mFragmentManager.beginTransaction()
                .replace(R.id.fragment_player_small, smallPlayerFragment!!)
                .replace(R.id.fragment_player_full, fullPlayerFragment!!)
                .commitAllowingStateLoss()
        }

        smallPlayerFragment?.setCallback { toggleBottomSheetState() }
        fullPlayerFragment?.setTouchInterceptListener { disallow ->
            findViewById<View>(R.id.bottom_sheet).parent.requestDisallowInterceptTouchEvent(disallow)
        }

        val coordinatorLayoutParams = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val appBarLayoutBehavior = object : AppBarLayout.Behavior() {
            override fun onStartNestedScroll(parent: CoordinatorLayout, child: AppBarLayout, directTargetChild: View, target: View, nestedScrollAxes: Int, type: Int): Boolean {
                return playerBottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        coordinatorLayoutParams.behavior = appBarLayoutBehavior

        val bottomSheetView = findViewById<View>(R.id.bottom_sheet)
        playerBottomSheet = BottomSheetBehavior.from(bottomSheetView)
        playerBottomSheet.isFitToContents = false
        
        ViewCompat.setOnApplyWindowInsetsListener(bottomSheetView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            playerBottomSheet.expandedOffset = systemBars.top
            val layoutParams = v.layoutParams
            layoutParams.height = v.rootView.height - mBottomNavigationView.height - systemBars.top
            v.layoutParams = layoutParams
            insets
        }

        playerBottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            private var oldState = BottomSheetBehavior.STATE_COLLAPSED

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING && oldState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (fullPlayerFragment?.isScrolled == true) {
                        playerBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
                        return
                    }
                }

                if (newState != BottomSheetBehavior.STATE_COLLAPSED && oldState == BottomSheetBehavior.STATE_COLLAPSED) {
                    fullPlayerFragment?.init()
                }

                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (smallPlayerFragment?.context != null) {
                        appBarLayout.post { appBarLayout.setExpanded(false, false) }
                        smallPlayerFragment?.setRole(FragmentPlayerSmall.Role.HEADER)
                    }
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    appBarLayout.post { appBarLayout.setExpanded(true, false) }
                    smallPlayerFragment?.setRole(FragmentPlayerSmall.Role.PLAYER)
                    fullPlayerFragment?.resetScroll()
                }

                oldState = newState
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        applyUiScaling()
        setupStartUpFragment()
        preRenderFragments()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(enabled = true) {
            override fun handleOnBackPressed() {
                if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
                    playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                    return
                }

                val backStackCount = mFragmentManager.backStackEntryCount
                if (backStackCount > 0) {
                    val backStackEntry = mFragmentManager.getBackStackEntryAt(backStackCount - 1)
                    try {
                        val parsedId = backStackEntry.name?.toInt() ?: -1
                        if (parsedId == FRAGMENT_FROM_BACKSTACK) {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                            invalidateOptionsMenu()
                            return
                        }
                    } catch (_: NumberFormatException) {}
                }

                if (Utils.bottomNavigationEnabled(this@ActivityMain)) {
                    if (lastExitTry != null && (Date().time < lastExitTry!!.time + 3000)) {
                        PlayerServiceUtil.shutdownService()
                        finish()
                    } else {
                        Utils.showModernToast(this@ActivityMain, R.string.alert_press_back_to_exit)
                        lastExitTry = Date()
                        return
                    }
                }

                if (backStackCount > 1) {
                    val backStackEntry = mFragmentManager.getBackStackEntryAt(backStackCount - 2)
                    selectedMenuItem = backStackEntry.name?.toInt() ?: -1
                    checkMenuItems()
                    invalidateOptionsMenu()
                } else {
                    finish()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun applyUiScaling() {
        val scale = UiScaler.getScaleFactor(this)
        val iconSize = (24 * resources.displayMetrics.density * scale).toInt()
        mBottomNavigationView.itemIconSize = iconSize

        val baseHeightDp = 72f
        val scaledHeight = (baseHeightDp * resources.displayMetrics.density * scale).toInt()
        playerBottomSheet.peekHeight = scaledHeight
        
        findViewById<View>(R.id.fragment_player_small)?.layoutParams?.let { 
            it.height = scaledHeight
            findViewById<View>(R.id.fragment_player_small).layoutParams = it
        }
        
        (containerView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { 
            it.bottomMargin = scaledHeight
            containerView.layoutParams = it
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        if (selectedMenuItem == menuItem.itemId) return true
        selectedMenuItem = menuItem.itemId
        return onNavigationItemSelectedInternal()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isSearchActive", isSearchActive)
        outState.putString("lastSearchQuery", lastSearchQuery)
    }

    override fun onDestroy() {
        preRenderJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onPause() {
        sharedPref.edit { putInt("last_selectedMenuItem", selectedMenuItem) }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        setupBroadcastReceiver()
        PlayerServiceUtil.startService(applicationContext)

        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            appBarLayout.setExpanded(false, false)
        }

        intent?.let {
            val action = it.action
            if (MediaSessionCallback.ACTION_PLAY_STATION_BY_UUID == action) {
                val stationUUID = it.extras?.getString(EXTRA_STATION_UUID)
                if (!TextUtils.isEmpty(stationUUID)) {
                    it.removeExtra(EXTRA_STATION_UUID)
                    scope.launch {
                        val station = withContext(Dispatchers.IO) {
                            Utils.getStationByUuid((application as AMARadioApp).httpClient, applicationContext, stationUUID!!)
                        }
                        if (!isFinishing && station != null) {
                            Utils.showPlaySelection(this@ActivityMain, station, supportFragmentManager)
                        }
                    }
                }
            }
            intent = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_main, menu)

        menuItemSleepTimer = menu.findItem(R.id.action_set_sleep_timer)
        menuItemSearch = menu.findItem(R.id.action_search)
        menuItemDelete = menu.findItem(R.id.action_delete)
        menuItemSave = menu.findItem(R.id.action_save)
        menuItemLoad = menu.findItem(R.id.action_load)
        menuItemListView = menu.findItem(R.id.action_list_view)
        menuItemIconsView = menu.findItem(R.id.action_icons_view)
        menuItemFilter = menu.findItem(R.id.action_filter_global)
        
        mSearchView = menuItemSearch?.actionView as? SearchView
        mSearchView?.maxWidth = Int.MAX_VALUE
        mSearchView?.setOnQueryTextListener(this)
        
        mSearchView?.post {
            val searchPlate = mSearchView?.findViewById<View>(androidx.appcompat.R.id.search_plate)
            searchPlate?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            mSearchView?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            mSearchView?.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.gravity = android.view.Gravity.CENTER_VERTICAL
        }

        if (isSearchActive) {
            menuItemSearch?.expandActionView()
            mSearchView?.setQuery(lastSearchQuery ?: "", false)
            findViewById<View>(R.id.toolbar_title_container)?.visibility = View.GONE
        }
        
        menuItemSearch?.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    isSearchActive = true
                    findViewById<View>(R.id.toolbar_title_container)?.visibility = View.GONE
                    invalidateOptionsMenu()
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    isSearchActive = false
                    lastSearchQuery = ""
                    findViewById<View>(R.id.toolbar_title_container)?.visibility = View.VISIBLE
                    searchStations("")
                    invalidateOptionsMenu()
                    return true
                }
            },
        )

        val isSearching = isSearchActive
        menuItemSleepTimer?.isVisible = !isSearching
        menuItemSearch?.isVisible = true
        menuItemDelete?.isVisible = !isSearching
        menuItemSave?.isVisible = !isSearching
        menuItemLoad?.isVisible = !isSearching
        
        val isIconsStyle = sharedPref.getBoolean("icons_only_favorites_style", false)
        menuItemListView?.isVisible = isIconsStyle && !isSearching
        menuItemIconsView?.isVisible = !isIconsStyle && !isSearching
        menuItemFilter?.isVisible = !isSearching

        val app = application as AMARadioApp
        if (!isSearching) {
            when (selectedMenuItem) {
                R.id.nav_item_starred -> {
                    menuItemDelete?.isVisible = !app.favouriteManager.isEmpty()
                    menuItemDelete?.setTitle(R.string.action_delete_favorites)
                }
                R.id.nav_item_history -> {
                    menuItemDelete?.isVisible = !app.historyManager.isEmpty()
                    menuItemDelete?.setTitle(R.string.action_delete_history)
                }
            }
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        val isSearching = isSearchActive || menu.findItem(R.id.action_search)?.isActionViewExpanded == true
        findViewById<View>(R.id.toolbar_title_container)?.visibility = if (isSearching) View.GONE else View.VISIBLE
        if (isSearching) {
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                if (item.itemId != R.id.action_search) item.isVisible = false
            }
        }
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                return true
            }
            R.id.action_save -> { saveFavourites(); return true }
            R.id.action_load -> { loadFavourites(); return true }
            R.id.action_set_sleep_timer -> { changeTimer(); return true }
            R.id.action_delete -> {
                val app = application as AMARadioApp
                if (selectedMenuItem == R.id.nav_item_history) {
                    AlertDialog.Builder(this).setMessage(R.string.alert_delete_history).setPositiveButton(R.string.yes) { _, _ -> app.historyManager.clear() }.setNegativeButton(R.string.no, null).show()
                }
                if (selectedMenuItem == R.id.nav_item_starred) {
                    AlertDialog.Builder(this).setMessage(R.string.alert_delete_favorites).setPositiveButton(R.string.yes) { _, _ -> app.favouriteManager.clear() }.setNegativeButton(R.string.no, null).show()
                }
                return true
            }
            R.id.action_filter_global -> { openFilterTab(); return true }
            R.id.action_list_view -> { sharedPref.edit(commit = true) { putBoolean("icons_only_favorites_style", false) }; invalidateOptionsMenu(); return true }
            R.id.action_icons_view -> { sharedPref.edit(commit = true) { putBoolean("icons_only_favorites_style", true) }; invalidateOptionsMenu(); return true }
        }
        return super.onOptionsItemSelected(menuItem)
    }

    fun toggleBottomSheetState() {
        playerBottomSheet.state = if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_EXPANDED
    }

    private fun setupStartUpFragment() {
        if (instanceStateWasSaved) { invalidateOptionsMenu(); checkMenuItems(); return }
        val app = application as AMARadioApp
        val startupAction = sharedPref.getString("startup_action", getString(R.string.startup_show_history))
        if (startupAction == getString(R.string.startup_show_history) && app.historyManager.isEmpty()) { selectMenuItem(R.id.nav_item_stations); return }
        if (startupAction == getString(R.string.startup_show_favorites) && app.favouriteManager.isEmpty()) { selectMenuItem(R.id.nav_item_stations); return }
        when (startupAction) {
            getString(R.string.startup_show_history) -> selectMenuItem(R.id.nav_item_history)
            getString(R.string.startup_show_favorites) -> selectMenuItem(R.id.nav_item_starred)
            else -> selectMenuItem(if (selectedMenuItem < 0) R.id.nav_item_stations else selectedMenuItem)
        }
    }

    private fun selectMenuItem(itemId: Int) {
        val menu = if (Utils.bottomNavigationEnabled(this)) mBottomNavigationView.menu else mNavigationView.menu
        val item = menu.findItem(itemId) ?: menu.findItem(R.id.nav_item_stations)
        selectedMenuItem = item.itemId
        onNavigationItemSelectedInternal()
    }
    
    private fun onNavigationItemSelectedInternal(): Boolean {
        preRenderJob?.cancel() // KILL background work IMMEDIATELY on any navigation
        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        mSearchView?.clearFocus()
        mDrawerLayout.closeDrawers()

        val tag = selectedMenuItem.toString()
        val targetFragment = mFragmentManager.findFragmentByTag(tag)
        if (targetFragment?.isVisible == true) return true

        val fragmentTransaction = mFragmentManager.beginTransaction()
        val mainFragmentTags = listOf(R.id.nav_item_stations.toString(), R.id.nav_item_starred.toString(), R.id.nav_item_history.toString(), R.id.nav_item_settings.toString())
        
        mainFragmentTags.forEach { fTag ->
            mFragmentManager.findFragmentByTag(fTag)?.let { 
                if (fTag != tag && it.isVisible) {
                    fragmentTransaction.hide(it)
                    fragmentTransaction.setMaxLifecycle(it, Lifecycle.State.STARTED)
                }
            }
        }

        if (targetFragment == null) {
            val newFragment = when (selectedMenuItem) {
                R.id.nav_item_starred -> FragmentStarred()
                R.id.nav_item_history -> FragmentHistory()
                R.id.nav_item_settings -> FragmentSettings()
                else -> FragmentTabs()
            }
            fragmentTransaction.add(R.id.containerView, newFragment, tag)
        } else {
            fragmentTransaction.show(targetFragment)
            fragmentTransaction.setMaxLifecycle(targetFragment, Lifecycle.State.RESUMED)
        }

        fragmentTransaction.commitAllowingStateLoss()
        checkMenuItems()
        invalidateOptionsMenu()
        appBarLayout.setExpanded(true, false)
        return true
    }

    private fun checkMenuItems() {
        val bItem = mBottomNavigationView.menu.findItem(selectedMenuItem)
        if (bItem != null && !bItem.isChecked) bItem.isChecked = true
        val nItem = mNavigationView.menu.findItem(selectedMenuItem)
        if (nItem != null && !nItem.isChecked) nItem.isChecked = true
    }

    fun search(searchStyle: SearchStyle, query: String) {
        preRenderJob?.cancel()
        val stationsFragment = mFragmentManager.findFragmentByTag(R.id.nav_item_stations.toString())
        if (stationsFragment != null && stationsFragment.isVisible && stationsFragment is FragmentTabs) {
            stationsFragment.search(searchStyle, query)
        } else {
            selectedMenuItem = R.id.nav_item_stations
            onNavigationItemSelectedInternal()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                (mFragmentManager.findFragmentByTag(R.id.nav_item_stations.toString()) as? FragmentTabs)?.search(searchStyle, query)
            }
        }
    }

    private fun openFilterTab() {
        val stationsFragment = mFragmentManager.findFragmentByTag(R.id.nav_item_stations.toString())
        if (stationsFragment is FragmentTabs) {
            stationsFragment.openFilterTab()
        } else {
            selectedMenuItem = R.id.nav_item_stations
            onNavigationItemSelectedInternal()
            containerView.post { (mFragmentManager.findFragmentByTag(R.id.nav_item_stations.toString()) as? FragmentTabs)?.openFilterTab() }
        }
    }

    fun searchStations(query: String) {
        preRenderJob?.cancel() // Kill background work immediately on user input
        listOf(R.id.nav_item_stations.toString(), R.id.nav_item_starred.toString(), R.id.nav_item_history.toString()).forEach { tag ->
            val fragment = mFragmentManager.findFragmentByTag(tag)
            if (fragment != null && fragment.isVisible && fragment is IFragmentSearchable) {
                fragment.search(SearchStyle.ByName, query)
                return
            }
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean = true
    override fun onQueryTextChange(newText: String?): Boolean { lastSearchQuery = newText; searchStations(newText ?: ""); return true }

    private fun setupBroadcastReceiver() {
        scope.launch {
            AppEventManager.events.collect { intent ->
                when (intent.action) {
                    ACTION_HIDE_LOADING -> findViewById<View>(R.id.progressBarLoading).visibility = View.GONE
                    ACTION_SHOW_LOADING -> findViewById<View>(R.id.progressBarLoading).visibility = View.VISIBLE
                    PlayerService.PLAYER_SERVICE_STATE_CHANGE -> if (PlayerServiceUtil.isPlaying()) meteredConnectionAlertDialog?.dismiss()
                }
            }
        }
    }

    private fun changeTimer() {
        val seekView = View.inflate(this, R.layout.layout_timer_chooser, null)
        val seekTextView = seekView.findViewById<TextView>(R.id.timerTextView)
        val seekBar = seekView.findViewById<SeekBar>(R.id.timerSeekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, f: Boolean) { seekTextView.text = p.toString() }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        val cur = PlayerServiceUtil.getTimerSeconds()
        seekBar.progress = if (cur <= 0) sharedPref.getInt("sleep_timer_default_minutes", 10) else (if (cur < 60) 1 else (cur / 60).toInt())
        AlertDialog.Builder(this).setTitle(R.string.sleep_timer_title).setView(seekView)
            .setPositiveButton(R.string.sleep_timer_apply) { _, _ -> PlayerServiceUtil.clearTimer(); PlayerServiceUtil.addTimer(seekBar.progress * 60); sharedPref.edit { putInt("sleep_timer_default_minutes", seekBar.progress) } }
            .setNegativeButton(R.string.sleep_timer_clear) { _, _ -> PlayerServiceUtil.clearTimer() }.show()
    }

    private fun preRenderFragments() {
        preRenderJob = scope.launch {
            delay(3000.milliseconds)
            if (!isFinishing && !isDestroyed) fullPlayerFragment?.init()
            val allTabs = listOf(R.id.nav_item_stations, R.id.nav_item_starred, R.id.nav_item_history, R.id.nav_item_settings)
            for (tabId in allTabs) {
                if (isFinishing || isDestroyed || selectedMenuItem == tabId) continue
                val tag = tabId.toString()
                if (mFragmentManager.findFragmentByTag(tag) == null) {
                    val fragment = when (tabId) {
                        R.id.nav_item_starred -> FragmentStarred()
                        R.id.nav_item_history -> FragmentHistory()
                        R.id.nav_item_settings -> FragmentSettings()
                        else -> FragmentTabs()
                    }
                    mFragmentManager.beginTransaction().add(R.id.containerView, fragment, tag).hide(fragment).setMaxLifecycle(fragment, Lifecycle.State.STARTED).commitAllowingStateLoss()
                    delay(2000.milliseconds)
                }
            }
        }
    }

    private fun saveFavourites() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ACTION_SAVE_FILE)
    }

    private fun loadFavourites() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ACTION_LOAD_FILE)
    }

    companion object {
        const val LAUNCH_EQUALIZER_REQUEST = 1
        const val MAX_DYNAMIC_LAUNCHER_SHORTCUTS = 4
        private const val ACTION_SAVE_FILE = 1
        private const val ACTION_LOAD_FILE = 2
        const val EXTRA_STATION_UUID = "stationuuid"
        const val FRAGMENT_FROM_BACKSTACK = 777
        const val ACTION_SHOW_LOADING = "com.ounben.amaradio.show_loading"
        const val ACTION_HIDE_LOADING = "com.ounben.amaradio.hide_loading"
        const val TAG = "AMARadio"
    }
}
