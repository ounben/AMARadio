package net.ounben.AMARadio

import android.content.*
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.preference.*
import net.ounben.AMARadio.interfaces.IApplicationSelected
import net.ounben.AMARadio.proxy.ProxySettingsDialog
import net.ounben.AMARadio.utils.UiScaler
import androidx.core.content.edit

class FragmentSettings : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener,
    IApplicationSelected, PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    override fun onPreferenceStartScreen(preferenceFragmentCompat: PreferenceFragmentCompat,
                                         preferenceScreen: PreferenceScreen): Boolean {
        openNewSettingsSubFragment(activity as ActivityMain, preferenceScreen.key)
        return true
    }

    private fun isToplevel(): Boolean {
        val rootKey = arguments?.getString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT)
        return rootKey == null || rootKey == "pref_toplevel"
    }

    private fun refreshToplevelIcons() {
        findPreference<Preference>("shareapp_package")?.summary = preferenceManager.sharedPreferences?.getString("shareapp_package", "")
        findPreference<Preference>("pref_category_ui")?.setIcon(R.drawable.ic_monitor_24dp)
        findPreference<Preference>("pref_category_startup")?.setIcon(R.drawable.ic_flight_takeoff_24dp)
        findPreference<Preference>("pref_category_interaction")?.setIcon(R.drawable.ic_touch_app_24dp)
        findPreference<Preference>("pref_category_player")?.setIcon(R.drawable.ic_play_arrow_24dp)
        findPreference<Preference>("pref_category_alarm")?.setIcon(R.drawable.ic_query_builder_black_24dp)
        findPreference<Preference>("pref_category_connectivity")?.setIcon(R.drawable.ic_sync_black_24dp)
        findPreference<Preference>("pref_category_recordings")?.setIcon(R.drawable.ic_fiber_manual_record_black_24dp)
        findPreference<Preference>("pref_category_mpd")?.setIcon(R.drawable.ic_volume_up_24dp)
        findPreference<Preference>("pref_category_other")?.setIcon(R.drawable.ic_live_help_24dp)
    }

    private fun refreshToolbar() {
        val activity = activity as? ActivityMain ?: return
        val myToolbar = activity.findViewById<Toolbar>(R.id.my_awesome_toolbar)
        val screen = preferenceScreen

        if (myToolbar == null || screen == null) return

        myToolbar.title = screen.title

        if (Utils.bottomNavigationEnabled(activity)) {
            if (isToplevel()) {
                activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
                activity.supportActionBar?.setDisplayShowHomeEnabled(false)
                myToolbar.setNavigationOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
            } else {
                activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
                activity.supportActionBar?.setDisplayShowHomeEnabled(true)
                myToolbar.setNavigationOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
            }
        }
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        setPreferencesFromResource(R.xml.preferences, s)
        refreshToolbar()
        if (s == null) {
            refreshToplevelIcons()
        } else if (s == "pref_category_player") {
            findPreference<Preference>("equalizer")?.setOnPreferenceClickListener {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                if (requireContext().packageManager.resolveActivity(intent, 0) == null) {
                    Toast.makeText(context, R.string.error_no_equalizer_found, Toast.LENGTH_SHORT).show()
                } else {
                    intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, ActivityMain.LAUNCH_EQUALIZER_REQUEST)
                }
                false
            }
        } else if (s == "pref_category_connectivity") {
            findPreference<Preference>("settings_proxy")?.setOnPreferenceClickListener {
                val proxySettingsDialog = ProxySettingsDialog()
                proxySettingsDialog.setCancelable(true)
                proxySettingsDialog.show(parentFragmentManager, "")
                false
            }
        } else if (s == "pref_category_mpd") {
            findPreference<Preference>("mpd_servers_viewer")?.setOnPreferenceClickListener {
                val AMARadioApp = requireActivity().application as AMARadioApp
                Utils.showMpdServersDialog(AMARadioApp, requireActivity().supportFragmentManager, null)
                false
            }
        } else if (s == "pref_category_other") {
            findPreference<Preference>("show_statistics")?.setOnPreferenceClickListener {
                (activity as ActivityMain).findViewById<Toolbar>(R.id.my_awesome_toolbar)?.setTitle(R.string.settings_statistics)
                val f = FragmentServerInfo()
                val fragmentTransaction = parentFragmentManager.beginTransaction()
                fragmentTransaction.replace(R.id.containerView, f).addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString()).commit()
                false
            }
            findPreference<Preference>("show_about")?.setOnPreferenceClickListener {
                (activity as ActivityMain).findViewById<Toolbar>(R.id.my_awesome_toolbar)?.setTitle(R.string.settings_about)
                val f = FragmentAbout()
                val fragmentTransaction = parentFragmentManager.beginTransaction()
                fragmentTransaction.replace(R.id.containerView, f).addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString()).commit()
                false
            }
        }

        val batPref = preferenceScreen.findPreference<Preference>(getString(R.string.key_ignore_battery_optimization))
        if (batPref != null) {
            updateBatteryPrefDescription(batPref)
            batPref.setOnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
                updateBatteryPrefDescription(batPref)
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        refreshToolbar()
        if (isToplevel()) refreshToplevelIcons()
        findPreference<Preference>("shareapp_package")?.summary = preferenceManager.sharedPreferences?.getString("shareapp_package", "")
        val batPref = preferenceScreen.findPreference<Preference>(getString(R.string.key_ignore_battery_optimization))
        if (batPref != null) {
            updateBatteryPrefDescription(batPref)
        }
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    private fun updateBatteryPrefDescription(batPref: Preference) {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            batPref.setSummary(R.string.settings_ignore_battery_optimization_summary_on)
        } else {
            batPref.setSummary(R.string.settings_ignore_battery_optimization_summary_off)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (BuildConfig.DEBUG) {
            Log.d("AAA", "changed key: $key")
        }
        if (key == "alarm_external") {
            val active = sharedPreferences?.getBoolean(key, false) ?: false
            if (active) {
                val newFragment = ApplicationSelectorDialog()
                newFragment.setCallback(this)
                newFragment.show(requireActivity().supportFragmentManager, "appPicker")
            }
        }
        if (key == "theme_name" || key == "circular_icons" || key == "bottom_navigation" || key == UiScaler.PREF_KEY_UI_SCALE) {
            if (key == "circular_icons") {
                (requireActivity().application as AMARadioApp).favouriteManager.updateShortcuts()
            }
            activity?.recreate()
        }
    }

    override fun onAppSelected(packageName: String, activityName: String) {
        if (BuildConfig.DEBUG) {
            Log.d("SEL", "selected: $packageName/$activityName")
        }
        preferenceManager.sharedPreferences?.edit {
            putString("shareapp_package", packageName)
            putString("shareapp_activity", activityName)
        }
        findPreference<Preference>("shareapp_package")?.summary = packageName
    }

    companion object {
        fun openNewSettingsSubFragment(activity: ActivityMain, key: String): FragmentSettings {
            val f = FragmentSettings()
            val args = Bundle()
            args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, key)
            f.arguments = args
            val fragmentTransaction = activity.supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.containerView, f).addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString()).commit()
            return f
        }
    }
}
