package net.ounben.AMARadio

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import net.ounben.AMARadio.alarm.FragmentAlarm
import net.ounben.AMARadio.alarm.TimePickerFragment
import net.ounben.AMARadio.cast.CastAwareActivity
import net.ounben.AMARadio.interfaces.IFragmentSearchable
import net.ounben.AMARadio.players.PlayState
import net.ounben.AMARadio.players.PlayStationTask
import net.ounben.AMARadio.players.mpd.MPDClient
import net.ounben.AMARadio.cast.CastHandler
import net.ounben.AMARadio.players.selector.PlayerType
import net.ounben.AMARadio.service.MediaSessionCallback
import net.ounben.AMARadio.service.PlayerService
import net.ounben.AMARadio.service.PlayerServiceUtil
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.station.StationsFilter
import net.ounben.AMARadio.BuildConfig
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.widget.TimePicker
import androidx.activity.OnBackPressedCallback
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import com.google.android.material.navigation.NavigationBarView
import java.io.*
import java.util.*
import net.ounben.AMARadio.utils.UiScaler

class ActivityMain : AppCompatActivity(), NavigationBarView.OnItemSelectedListener,
    NavigationView.OnNavigationItemSelectedListener, SearchView.OnQueryTextListener,
    TimePickerDialog.OnTimeSetListener,
    CastHandler.CastHandlerListener, CastAwareActivity {

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
    private lateinit var playerBottomSheet: BottomSheetBehavior<View>
    private var smallPlayerFragment: FragmentPlayerSmall? = null
    private var fullPlayerFragment: FragmentPlayerFull? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var menuItemSearch: MenuItem? = null
    private var menuItemDelete: MenuItem? = null
    private var menuItemSleepTimer: MenuItem? = null
    private var menuItemSave: MenuItem? = null
    private var menuItemLoad: MenuItem? = null
    private var menuItemIconsView: MenuItem? = null
    private var menuItemListView: MenuItem? = null
    private var menuItemAddAlarm: MenuItem? = null
    private var menuItemMpd: MenuItem? = null
    private var menuItemFilter: MenuItem? = null
    private var menuItemCast: MenuItem? = null
    private lateinit var sharedPref: SharedPreferences
    private var selectedMenuItem = 0
    private var instanceStateWasSaved = false
    private var lastExitTry: Date? = null
    private var meteredConnectionAlertDialog: AlertDialog? = null
    private var isSearchExpanded = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Utils.getThemeResId(this))
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        setContentView(R.layout.layout_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        Log.d(TAG, "FilesDir: " + filesDir.absolutePath)
        Log.d(TAG, "CacheDir: " + cacheDir.absolutePath)
        try {
            val dir = File(filesDir.absolutePath)
            if (dir.isDirectory) {
                dir.list()?.forEach { aChildren ->
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "delete file: $aChildren")
                    }
                    try {
                        File(dir, aChildren).delete()
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
        }

        val myToolbar: Toolbar = findViewById(R.id.my_awesome_toolbar)
        setSupportActionBar(myToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        PlayerServiceUtil.startService(applicationContext)

        selectedMenuItem = sharedPref.getInt("last_selectedMenuItem", -1)
        instanceStateWasSaved = savedInstanceState != null
        mFragmentManager = supportFragmentManager

        appBarLayout = findViewById(R.id.app_bar_layout)
        tabsView = findViewById(R.id.tabs)
        mDrawerLayout = findViewById(R.id.drawerLayout)
        mNavigationView = findViewById(R.id.my_navigation_view)
        mBottomNavigationView = findViewById(R.id.bottom_navigation)

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

            val fragmentTransaction = mFragmentManager.beginTransaction()
            fragmentTransaction.hide(fullPlayerFragment!!)
            fragmentTransaction.replace(R.id.fragment_player_small, smallPlayerFragment!!)
            fragmentTransaction.replace(R.id.fragment_player_full, fullPlayerFragment!!)
            fragmentTransaction.commit()
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

        playerBottomSheet = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet))
        playerBottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            private var oldState = BottomSheetBehavior.STATE_COLLAPSED

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING && oldState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (fullPlayerFragment?.isScrolled == true) {
                        playerBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
                        return
                    }
                }

                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (smallPlayerFragment?.context == null) return

                    appBarLayout.setExpanded(false)
                    smallPlayerFragment?.setRole(FragmentPlayerSmall.Role.HEADER)

                    val fragmentTransaction = mFragmentManager.beginTransaction()
                    mFragmentManager.findFragmentById(R.id.containerView)?.let { fragmentTransaction.hide(it) }
                    fragmentTransaction.commit()
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    appBarLayout.setExpanded(true)
                    smallPlayerFragment?.setRole(FragmentPlayerSmall.Role.PLAYER)
                    fullPlayerFragment?.resetScroll()

                    val fragmentTransaction = mFragmentManager.beginTransaction()
                    fullPlayerFragment?.let { fragmentTransaction.hide(it) }
                    fragmentTransaction.commit()
                }

                if (oldState == BottomSheetBehavior.STATE_EXPANDED && newState != BottomSheetBehavior.STATE_EXPANDED) {
                    val fragmentTransaction = mFragmentManager.beginTransaction()
                    mFragmentManager.findFragmentById(R.id.containerView)?.let { fragmentTransaction.show(it) }
                    fragmentTransaction.commit()
                }

                if (oldState == BottomSheetBehavior.STATE_COLLAPSED && newState != oldState) {
                    fullPlayerFragment?.init()

                    val fragmentTransaction = mFragmentManager.beginTransaction()
                    fullPlayerFragment?.let { fragmentTransaction.show(it) }
                    fragmentTransaction.commit()
                }

                oldState = newState
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        (application as AMARadioApp).castHandler.setActivity(this)

        applyUiScaling()
        setupStartUpFragment()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
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
                    } catch (ignore: NumberFormatException) {}
                }

                if (Utils.bottomNavigationEnabled(this@ActivityMain)) {
                    if (lastExitTry != null && Date().time < lastExitTry!!.time + 3000) {
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
                    if (!Utils.bottomNavigationEnabled(this@ActivityMain)) {
                        mNavigationView.setCheckedItem(selectedMenuItem)
                    }
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

        // We use a fixed base height of 72dp to ensure it's never too small, 
        // even in standard mode, and scale it from there.
        val baseHeightDp = 72f
        val scaledHeight = (baseHeightDp * resources.displayMetrics.density * scale).toInt()
        
        playerBottomSheet.peekHeight = scaledHeight
        
        val smallPlayerContainer = findViewById<View>(R.id.fragment_player_small)
        val layoutParams = smallPlayerContainer?.layoutParams
        if (layoutParams != null) {
            layoutParams.height = scaledHeight
            smallPlayerContainer.layoutParams = layoutParams
        }
        
        val containerView = findViewById<View>(R.id.containerView)
        val containerParams = containerView.layoutParams as? ViewGroup.MarginLayoutParams
        if (containerParams != null) {
            containerParams.bottomMargin = scaledHeight
            containerView.layoutParams = containerParams
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        selectedMenuItem = menuItem.itemId
        return onNavigationItemSelectedInternal()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "on request permissions result: $requestCode")
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERM_REQ_STORAGE_FAV_LOAD -> {
                LoadFavourites()
            }
            PERM_REQ_STORAGE_FAV_SAVE -> {
                SaveFavourites()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        if (!PlayerServiceUtil.isNotificationActive()) {
            PlayerServiceUtil.shutdownService()
        }
    }

    override fun onPause() {
        sharedPref.edit { putInt("last_selectedMenuItem", selectedMenuItem) }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "PAUSED")
        }
        broadcastReceiver?.let { LocalBroadcastManager.getInstance(this).unregisterReceiver(it) }
        super.onPause()
        if (PlayerServiceUtil.getPlayerState() == PlayState.Idle) {
            PlayerServiceUtil.shutdownService()
        }
        val castHandler = (application as AMARadioApp).castHandler
        castHandler.onPause()
        castHandler.setActivity(null)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val extras = intent.extras ?: return

        if (MediaSessionCallback.ACTION_PLAY_STATION_BY_UUID == action) {
            val stationUUID = extras.getString(EXTRA_STATION_UUID)
            if (TextUtils.isEmpty(stationUUID)) return
            intent.removeExtra(EXTRA_STATION_UUID)
            
            val AMARadioApp = application as AMARadioApp
            val httpClient = AMARadioApp.httpClient
            
            scope.launch {
                val station = withContext(Dispatchers.IO) {
                    Utils.getStationByUuid(httpClient, applicationContext, stationUUID!!)
                }
                if (!isFinishing && station != null) {
                    Utils.showPlaySelection(this@ActivityMain, station, supportFragmentManager)
                    val currentFragment = mFragmentManager.fragments.lastOrNull()
                    if (currentFragment is FragmentHistory) {
                        currentFragment.RefreshListGui()
                    }
                }
            }
        } else {
            val searchTag = extras.getString(EXTRA_SEARCH_TAG)
            Log.d("MAIN", "received search request for tag 1: $searchTag")
            if (searchTag != null) {
                Log.d("MAIN", "received search request for tag 2: $searchTag")
                Search(StationsFilter.SearchStyle.ByTagExact, searchTag)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "RESUMED")
        }
        setupBroadcastReceiver()
        PlayerServiceUtil.startService(applicationContext)
        val castHandler = (application as AMARadioApp).castHandler
        castHandler.onResume()
        castHandler.setActivity(this)

        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            appBarLayout.setExpanded(false)
        }

        intent?.let {
            handleIntent(it)
            setIntent(null)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_main, menu)

        val myToolbar: Toolbar = findViewById(R.id.my_awesome_toolbar)
        menuItemSleepTimer = menu.findItem(R.id.action_set_sleep_timer)
        menuItemSearch = menu.findItem(R.id.action_search)
        menuItemDelete = menu.findItem(R.id.action_delete)
        menuItemSave = menu.findItem(R.id.action_save)
        menuItemLoad = menu.findItem(R.id.action_load)
        menuItemListView = menu.findItem(R.id.action_list_view)
        menuItemIconsView = menu.findItem(R.id.action_icons_view)
        menuItemAddAlarm = menu.findItem(R.id.action_add_alarm)
        menuItemMpd = menu.findItem(R.id.action_mpd)
        menuItemFilter = menu.findItem(R.id.action_filter_global)
        
        mSearchView = menuItemSearch?.actionView as? SearchView
        mSearchView?.maxWidth = Int.MAX_VALUE
        mSearchView?.setOnQueryTextListener(this)
        
        menuItemSearch?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                isSearchExpanded = true
                invalidateOptionsMenu()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                isSearchExpanded = false
                invalidateOptionsMenu()
                return true
            }
        })

        mSearchView?.setOnQueryTextFocusChangeListener { v, hasFocus ->
            if (Utils.bottomNavigationEnabled(this)) {
                mBottomNavigationView.visibility = if (hasFocus) View.GONE else View.VISIBLE
            }
            if (hasFocus) {
                val prevTabsVisibility = tabsView.visibility
                tabsView.visibility = View.GONE
                v.setTag(R.id.tabs, prevTabsVisibility)
            } else {
                val prevTabsVisibility = v.getTag(R.id.tabs) as? Int ?: View.GONE
                tabsView.visibility = prevTabsVisibility
            }
        }

        menuItemSleepTimer?.isVisible = false
        menuItemSearch?.isVisible = !isSearchExpanded
        menuItemDelete?.isVisible = false
        menuItemSave?.isVisible = false
        menuItemLoad?.isVisible = false
        menuItemListView?.isVisible = sharedPref.getBoolean("icons_only_favorites_style", false) && !isSearchExpanded
        menuItemIconsView?.isVisible = !sharedPref.getBoolean("icons_only_favorites_style", false) && !isSearchExpanded
        menuItemAddAlarm?.isVisible = false
        menuItemFilter?.isVisible = !isSearchExpanded // Global filter access

        var mpdIsVisible = false
        val AMARadioApp = application as AMARadioApp
        val mpdClient = AMARadioApp.mpdClient
        val repository = mpdClient.mpdServersRepository
        mpdIsVisible = !repository.isEmpty && !isSearchExpanded

        menuItemMpd?.isVisible = mpdIsVisible

        if (isSearchExpanded) {
            menuItemSearch?.isVisible = true
        } else {
            when (selectedMenuItem) {
                R.id.nav_item_stations -> {
                    menuItemSleepTimer?.isVisible = true
                    menuItemSearch?.isVisible = true
                    setToolbarTitle(R.string.nav_item_stations)
                }
                R.id.nav_item_starred -> {
                    menuItemSleepTimer?.isVisible = true
                    menuItemSave?.isVisible = true
                    menuItemLoad?.isVisible = true
                    menuItemSave?.setTitle(R.string.nav_item_save_playlist)

                    menuItemDelete?.isVisible = !AMARadioApp.favouriteManager.isEmpty()
                    menuItemDelete?.setTitle(R.string.action_delete_favorites)
                    setToolbarTitle(R.string.nav_item_starred)
                }
                R.id.nav_item_history -> {
                    menuItemSleepTimer?.isVisible = true
                    menuItemSave?.isVisible = true
                    menuItemSave?.setTitle(R.string.nav_item_save_history_playlist)

                    menuItemDelete?.isVisible = !AMARadioApp.historyManager.isEmpty()
                    menuItemDelete?.setTitle(R.string.action_delete_history)
                    setToolbarTitle(R.string.nav_item_history)
                }
                R.id.nav_item_alarm -> {
                    menuItemAddAlarm?.isVisible = true
                    setToolbarTitle(R.string.nav_item_alarm)
                }
            }
        }

        menuItemCast = (application as AMARadioApp).castHandler.getRouteItem(applicationContext, menu)
        if (isSearchExpanded) {
            menuItemCast?.isVisible = false
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        if (isSearchExpanded) {
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                if (item.itemId != R.id.action_search) {
                    item.isVisible = false
                }
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == ACTION_SAVE_FILE && resultCode == RESULT_OK) {
            resultData?.data?.let { uri ->
                Log.d(TAG, "Chosen save path: $uri")
                val app = application as AMARadioApp
                scope.launch {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            contentResolver.openOutputStream(uri)?.use { os ->
                                val writer = BufferedWriter(OutputStreamWriter(os))
                                val manager = if (selectedMenuItem == R.id.nav_item_starred) app.favouriteManager else app.historyManager
                                manager.exportM3U(writer)
                            } ?: false
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to write to file $e")
                            false
                        }
                    }
                    if (success) {
                        Utils.showModernToast(this@ActivityMain, R.string.notify_save_playlist_ok)
                    } else {
                        Utils.showModernToast(this@ActivityMain, R.string.notify_save_playlist_nok)
                    }
                }
            }
        }
        if (requestCode == ACTION_LOAD_FILE && resultCode == RESULT_OK) {
            resultData?.data?.let { uri ->
                Log.d(TAG, "Chosen load path: $uri")
                val app = application as AMARadioApp
                Utils.showModernToast(this, R.string.notify_load_playlist_now)
                scope.launch {
                    val loadedStations = withContext(Dispatchers.IO) {
                        try {
                            contentResolver.openInputStream(uri)?.use { isStr ->
                                app.favouriteManager.importM3U(InputStreamReader(isStr))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to load file $e")
                            null
                        }
                    }
                    
                    if (loadedStations != null) {
                        if (loadedStations.isNotEmpty()) {
                            app.favouriteManager.addMultiple(loadedStations)
                            val msg = getString(R.string.notify_load_playlist_ok, loadedStations.size, "", "")
                            Utils.showSnackbar(findViewById(android.R.id.content), msg)
                        } else {
                            Utils.showSnackbar(findViewById(android.R.id.content), "No valid stations found in file")
                        }
                    } else {
                        Utils.showModernToast(this@ActivityMain, R.string.notify_load_playlist_nok)
                    }
                }
            }
        }
    }

    private fun SaveFavourites() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ACTION_SAVE_FILE)
    }

    private fun LoadFavourites() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ACTION_LOAD_FILE)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                return true
            }
            R.id.action_save -> {
                try {
                    SaveFavourites()
                } catch (e: Exception) {
                    Log.e("MAIN", e.toString())
                }
                return true
            }
            R.id.action_load -> {
                try {
                    LoadFavourites()
                } catch (e: Exception) {
                    Log.e("MAIN", e.toString())
                }
                return true
            }
            R.id.action_set_sleep_timer -> {
                changeTimer()
                return true
            }
            R.id.action_mpd -> {
                selectMPDServer()
                return true
            }
            R.id.action_delete -> {
                if (selectedMenuItem == R.id.nav_item_history) {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.alert_delete_history))
                        .setCancelable(true)
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            val AMARadioApp = application as AMARadioApp
                            AMARadioApp.historyManager.clear()
                            Utils.showModernToast(this@ActivityMain, R.string.notify_deleted_history)
                            recreate()
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
                }
                if (selectedMenuItem == R.id.nav_item_starred) {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.alert_delete_favorites))
                        .setCancelable(true)
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            val AMARadioApp = application as AMARadioApp
                            AMARadioApp.favouriteManager.clear()
                            Utils.showModernToast(this@ActivityMain, R.string.notify_deleted_favorites)
                            recreate()
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
                }
                return true
            }
            R.id.action_filter_global -> {
                openFilterTab()
                return true
            }
            R.id.action_list_view -> {
                sharedPref.edit(commit = true) { putBoolean("icons_only_favorites_style", false) }
                recreate()
                return true
            }
            R.id.action_icons_view -> {
                sharedPref.edit(commit = true) { putBoolean("icons_only_favorites_style", true) }
                recreate()
                return true
            }
            R.id.action_add_alarm -> {
                val newFragment = TimePickerFragment()
                newFragment.setCallback(this)
                newFragment.show(supportFragmentManager, "timePicker")
                return true
            }
        }
        return super.onOptionsItemSelected(menuItem)
    }

    fun toggleBottomSheetState() {
        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            playerBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        val AMARadioApp = application as AMARadioApp
        val historyManager = AMARadioApp.historyManager
        val currentFragment = mFragmentManager.fragments.getOrNull(mFragmentManager.fragments.size - 2)
        if (historyManager.size() > 0 && currentFragment is FragmentAlarm) {
            val station = historyManager.getList()[0]
            currentFragment.ram?.add(station, hourOfDay, minute)
        }
    }

    private fun setupStartUpFragment() {
        if (instanceStateWasSaved) {
            invalidateOptionsMenu()
            checkMenuItems()
            return
        }

        val AMARadioApp = application as AMARadioApp
        val hm = AMARadioApp.historyManager
        val fm = AMARadioApp.favouriteManager

        val startupAction = sharedPref.getString("startup_action", getString(R.string.startup_show_history))

        if (startupAction == getString(R.string.startup_show_history) && hm.isEmpty()) {
            selectMenuItem(R.id.nav_item_stations)
            return
        }

        if (startupAction == getString(R.string.startup_show_favorites) && fm.isEmpty()) {
            selectMenuItem(R.id.nav_item_stations)
            return
        }

        when (startupAction) {
            getString(R.string.startup_show_history) -> selectMenuItem(R.id.nav_item_history)
            getString(R.string.startup_show_favorites) -> selectMenuItem(R.id.nav_item_starred)
            getString(R.string.startup_show_all_stations) -> selectMenuItem(R.id.nav_item_stations)
            else -> {
                if (selectedMenuItem < 0) selectMenuItem(R.id.nav_item_stations)
                else selectMenuItem(selectedMenuItem)
            }
        }
    }

    private fun selectMenuItem(itemId: Int) {
        val item = if (Utils.bottomNavigationEnabled(this))
            mBottomNavigationView.menu.findItem(itemId)
        else
            mNavigationView.menu.findItem(itemId)

        if (item != null) {
            onNavigationItemSelected(item)
        } else {
            selectedMenuItem = R.id.nav_item_stations
            onNavigationItemSelectedInternal()
        }
    }
    
    private fun onNavigationItemSelectedInternal(): Boolean {
        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        mSearchView?.clearFocus()
        mDrawerLayout.closeDrawers()
        
        var f: Fragment? = null
        val backStackTag = selectedMenuItem.toString()

        when (selectedMenuItem) {
            R.id.nav_item_stations -> f = FragmentTabs()
            R.id.nav_item_starred -> f = FragmentStarred()
            R.id.nav_item_history -> f = FragmentHistory()
            R.id.nav_item_alarm -> f = FragmentAlarm()
            R.id.nav_item_settings -> f = FragmentSettings()
        }

        f?.let {
            mFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            val fragmentTransaction = mFragmentManager.beginTransaction()
            if (Utils.bottomNavigationEnabled(this)) {
                fragmentTransaction.replace(R.id.containerView, it).commit()
            } else {
                fragmentTransaction.replace(R.id.containerView, it).addToBackStack(backStackTag).commit()
            }
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
        invalidateOptionsMenu()
        checkMenuItems()

        appBarLayout.setExpanded(true)
        return false
    }

    private fun checkMenuItems() {
        mBottomNavigationView.menu.findItem(selectedMenuItem)?.isChecked = true
        mNavigationView.menu.findItem(selectedMenuItem)?.isChecked = true
    }

    fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        Log.d("MAIN", "Search() searchstyle=$searchStyle query=$query")
        val currentFragment = mFragmentManager.fragments.lastOrNull()
        if (currentFragment is FragmentTabs) {
            currentFragment.Search(searchStyle, query)
        } else {
            val backStackTag = R.id.nav_item_stations.toString()
            val f = FragmentTabs()
            val fragmentTransaction = mFragmentManager.beginTransaction()
            if (Utils.bottomNavigationEnabled(this)) {
                fragmentTransaction.replace(R.id.containerView, f).commit()
                mBottomNavigationView.menu.findItem(R.id.nav_item_stations)?.isChecked = true
            } else {
                fragmentTransaction.replace(R.id.containerView, f).addToBackStack(backStackTag).commit()
                mNavigationView.menu.findItem(R.id.nav_item_stations)?.isChecked = true
            }

            f.Search(searchStyle, query)
            selectedMenuItem = R.id.nav_item_stations
            invalidateOptionsMenu()
        }
    }

    private fun openFilterTab() {
        val currentFragment = mFragmentManager.fragments.lastOrNull()
        if (currentFragment is FragmentTabs) {
            currentFragment.openFilterTab()
        } else {
            val backStackTag = R.id.nav_item_stations.toString()
            val f = FragmentTabs()
            val fragmentTransaction = mFragmentManager.beginTransaction()
            if (Utils.bottomNavigationEnabled(this)) {
                fragmentTransaction.replace(R.id.containerView, f).commit()
                mBottomNavigationView.menu.findItem(R.id.nav_item_stations)?.isChecked = true
            } else {
                fragmentTransaction.replace(R.id.containerView, f).addToBackStack(backStackTag).commit()
                mNavigationView.menu.findItem(R.id.nav_item_stations)?.isChecked = true
            }

            // We need to wait for the fragment to be created to switch tabs
            mFragmentManager.executePendingTransactions()
            f.openFilterTab()
            selectedMenuItem = R.id.nav_item_stations
            invalidateOptionsMenu()
        }
    }

    fun SearchStations(query: String) {
        Log.d("MAIN", "SearchStations() $query")
        val currentFragment = mFragmentManager.fragments.lastOrNull()
        if (currentFragment is IFragmentSearchable) {
            currentFragment.Search(StationsFilter.SearchStyle.ByName, query)
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return true
    }

    fun setToolbarTitle(title: CharSequence?) {
        findViewById<TextView>(R.id.toolbar_custom_title)?.text = title
    }

    fun setToolbarTitle(titleRes: Int) {
        findViewById<TextView>(R.id.toolbar_custom_title)?.setText(titleRes)
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        SearchStations(newText ?: "")
        return true
    }

    private fun showMeteredConnectionDialog(playFunc: Runnable) {
        val res = resources
        val title = res.getString(R.string.alert_metered_connection_title)
        val text = res.getString(R.string.alert_metered_connection_message)
        meteredConnectionAlertDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> playFunc.run() }
            .setOnDismissListener { meteredConnectionAlertDialog = null }
            .create()

        meteredConnectionAlertDialog?.show()
    }

    private fun setupBroadcastReceiver() {
        val filter = IntentFilter()
        filter.addAction(ACTION_HIDE_LOADING)
        filter.addAction(ACTION_SHOW_LOADING)
        filter.addAction(PlayerService.PLAYER_SERVICE_STATE_CHANGE)
        filter.addAction(PlayerService.PLAYER_SERVICE_METERED_CONNECTION)
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_HIDE_LOADING -> hideLoadingIcon()
                    ACTION_SHOW_LOADING -> showLoadingIcon()
                    PlayerService.PLAYER_SERVICE_METERED_CONNECTION -> {
                        if (meteredConnectionAlertDialog != null) {
                            meteredConnectionAlertDialog?.cancel()
                            meteredConnectionAlertDialog = null
                        }

                        val playerType: PlayerType? = IntentCompat.getParcelableExtra(intent, PlayerService.PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE, PlayerType::class.java)

                        when (playerType) {
                            PlayerType.AMARadio -> showMeteredConnectionDialog {
                                Utils.play(application as AMARadioApp, PlayerServiceUtil.getCurrentStation()!!)
                            }
                            PlayerType.EXTERNAL -> {
                                val currentStation = PlayerServiceUtil.getCurrentStation()
                                if (currentStation != null) {
                                    showMeteredConnectionDialog {
                                        PlayStationTask.playExternal(currentStation, this@ActivityMain).execute()
                                    }
                                }
                            }
                            else -> Log.e(TAG, "broadcastReceiver unexpected PlayerType '$playerType'")
                        }
                    }
                    PlayerService.PLAYER_SERVICE_STATE_CHANGE -> {
                        if (PlayerServiceUtil.isPlaying()) {
                            if (meteredConnectionAlertDialog != null) {
                                meteredConnectionAlertDialog?.cancel()
                                meteredConnectionAlertDialog = null
                            }
                        }
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver!!, filter)
    }

    private fun showLoadingIcon() {
        findViewById<View>(R.id.progressBarLoading).visibility = View.VISIBLE
    }

    private fun hideLoadingIcon() {
        findViewById<View>(R.id.progressBarLoading).visibility = View.GONE
    }

    private fun changeTimer() {
        val seekDialog = AlertDialog.Builder(this)
        val seekView = View.inflate(this, R.layout.layout_timer_chooser, null)

        seekDialog.setTitle(R.string.sleep_timer_title)
        seekDialog.setView(seekView)

        val seekTextView = seekView.findViewById<TextView>(R.id.timerTextView)
        val seekBar = seekView.findViewById<SeekBar>(R.id.timerSeekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                seekTextView.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val currenTimerSeconds = PlayerServiceUtil.getTimerSeconds()
        val currentTimer: Long = if (currenTimerSeconds <= 0) {
            sharedPref.getInt("sleep_timer_default_minutes", 10).toLong()
        } else if (currenTimerSeconds < 60) {
            1
        } else {
            currenTimerSeconds / 60
        }
        seekBar.progress = currentTimer.toInt()
        seekDialog.setPositiveButton(R.string.sleep_timer_apply) { _, _ ->
            PlayerServiceUtil.clearTimer()
            PlayerServiceUtil.addTimer(seekBar.progress * 60)
            sharedPref.edit { putInt("sleep_timer_default_minutes", seekBar.progress) }
        }

        seekDialog.setNegativeButton(R.string.sleep_timer_clear) { _, _ ->
            PlayerServiceUtil.clearTimer()
        }

        seekDialog.create().show()
    }

    private fun selectMPDServer() {
        val AMARadioApp = application as AMARadioApp
        Utils.showMpdServersDialog(AMARadioApp, supportFragmentManager, null)
    }

    override fun invalidateOptionsMenuForCast() {
        invalidateOptionsMenu()
    }

    companion object {
        const val EXTRA_SEARCH_TAG = "search_tag"
        const val EXTRA_STATION_UUID = "stationuuid" // defined in MediaSessionCallback as well but here for convenience
        const val LAUNCH_EQUALIZER_REQUEST = 1
        const val MAX_DYNAMIC_LAUNCHER_SHORTCUTS = 4
        const val FRAGMENT_FROM_BACKSTACK = 777
        const val ACTION_SHOW_LOADING = "net.ounben.AMARadio.show_loading"
        const val ACTION_HIDE_LOADING = "net.ounben.AMARadio.hide_loading"
        const val TAG = "AMARadio"
        const val PERM_REQ_STORAGE_FAV_SAVE = 1
        const val PERM_REQ_STORAGE_FAV_LOAD = 2
        const val ACTION_SAVE_FILE = 1
        const val ACTION_LOAD_FILE = 2
    }
}
