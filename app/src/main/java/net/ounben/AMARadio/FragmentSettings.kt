package net.ounben.AMARadio

import android.content.*
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.bytehamster.lib.preferencesearch.SearchPreference
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import net.ounben.AMARadio.interfaces.IApplicationSelected
import net.ounben.AMARadio.proxy.ProxySettingsDialog
import net.ounben.AMARadio.utils.UiScaler
import net.ounben.AMARadio.BuildConfig
import androidx.core.content.edit

class FragmentSettings : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener,
    IApplicationSelected, PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    override fun onPreferenceStartScreen(preferenceFragmentCompat: PreferenceFragmentCompat,
                                         preferenceScreen: PreferenceScreen): Boolean {
        openNewSettingsSubFragment(activity as ActivityMain, preferenceScreen.key)
        return true
    }

    private fun isToplevel(): Boolean {
        return preferenceScreen == null || preferenceScreen.key == "pref_toplevel"
    }

    private fun refreshToplevelIcons() {
        findPreference<Preference>("shareapp_package")?.summary = preferenceManager.sharedPreferences?.getString("shareapp_package", "")
        findPreference<Preference>("pref_category_ui")?.setIcon(Utils.IconicsIcon(requireContext(), CommunityMaterial.Icon2.cmd_monitor))
        findPreference<Preference>("pref_category_startup")?.setIcon(Utils.IconicsIcon(requireContext(), GoogleMaterial.Icon.gmd_flight_takeoff))
        findPreference<Preference>("pref_category_interaction")?.setIcon(Utils.IconicsIcon(requireContext(), CommunityMaterial.Icon.cmd_gesture_tap))
        findPreference<Preference>("pref_category_player")?.setIcon(Utils.IconicsIcon(requireContext(), CommunityMaterial.Icon2.cmd_play))
        findPreference<Preference>("pref_category_alarm")?.setIcon(Utils.IconicsIcon(requireContext(), CommunityMaterial.Icon.cmd_clock_outline))
        findPreference<Preference>("pref_category_connectivity")?.setIcon(Utils.IconicsIcon(requireContext(), GoogleMaterial.Icon.gmd_import_export))
        findPreference<Preference>("pref_category_recordings")?.setIcon(Utils.IconicsIcon(requireContext(), CommunityMaterial.Icon2.cmd_record_rec))
        findPreference<Preference>("pref_category_mpd")?.setIcon(Utils.IconicsIcon(requireContext(), CommunityMaterial.Icon2.cmd_speaker_wireless))
        findPreference<Preference>("pref_category_other")?.setIcon(Utils.IconicsIcon(requireContext(), CommunityMaterial.Icon2.cmd_information_outline))
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
            val searchPreference = findPreference<SearchPreference>("searchPreference")
            val config = searchPreference?.searchConfiguration
            config?.setActivity(activity as AppCompatActivity)
            config?.index(R.xml.preferences)
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
