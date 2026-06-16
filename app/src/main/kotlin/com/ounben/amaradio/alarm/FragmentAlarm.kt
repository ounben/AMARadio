package com.ounben.amaradio.alarm

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import com.ounben.amaradio.R
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.interfaces.IFragmentSearchable
import com.ounben.amaradio.station.StationsFilter
import java.util.*

class FragmentAlarm : Fragment(), TimePickerDialog.OnTimeSetListener, IFragmentSearchable {
    var ram: RadioAlarmManager? = null
        private set
    private var adapterRadioAlarm: ItemAdapterRadioAlarm? = null
    private var lvAlarms: ListView? = null
    private var alarmsObserver: Observer? = null
    private var lastQuery: String = ""

    override fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        lastQuery = query
        refreshListAndView()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val AMARadioApp = requireActivity().application as AMARadioApp
        ram = AMARadioApp.alarmManager
        val view = inflater.inflate(R.layout.layout_alarms, container, false)
        adapterRadioAlarm = ItemAdapterRadioAlarm(activity)
        lvAlarms = view.findViewById(R.id.listViewAlarms)
        lvAlarms?.adapter = adapterRadioAlarm
        lvAlarms?.isClickable = true
        lvAlarms?.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val anObject = parent.getItemAtPosition(position)
            if (anObject is DataRadioStationAlarm) {
                clickOnItem(anObject)
            }
        }
        alarmsObserver = Observer { _, _ -> refreshListAndView() }
        return view
    }

    override fun onResume() {
        super.onResume()
        refreshListAndView()
        ram?.savedAlarmsObservable?.addObserver(alarmsObserver)
    }

    override fun onPause() {
        super.onPause()
        ram?.savedAlarmsObservable?.deleteObserver(alarmsObserver)
    }

    private fun refreshListAndView() {
        adapterRadioAlarm?.clear()
        val allAlarms = ram?.getList() ?: emptyArray()
        
        if (lastQuery.isEmpty()) {
            adapterRadioAlarm?.addAll(*allAlarms)
        } else {
            val lowerQuery = lastQuery.lowercase(Locale.ROOT)
            val filtered = allAlarms.filter { 
                it.station?.Name?.lowercase(Locale.ROOT)?.contains(lowerQuery) == true
            }
            adapterRadioAlarm?.addAll(*filtered.toTypedArray())
        }
    }

    private var clickedAlarm: DataRadioStationAlarm? = null
    private fun clickOnItem(anObject: DataRadioStationAlarm) {
        clickedAlarm = anObject
        val newFragment = TimePickerFragment(clickedAlarm!!.hour, clickedAlarm!!.minute)
        newFragment.setCallback(this)
        newFragment.show(requireActivity().supportFragmentManager, "timePicker")
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        val alarm = clickedAlarm
        if (alarm != null) {
            ram?.changeTime(alarm.id, hourOfDay, minute)
            clickedAlarm = null
        }
    }
}
