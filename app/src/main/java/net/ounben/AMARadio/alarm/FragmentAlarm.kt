package net.ounben.AMARadio.alarm

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import net.ounben.AMARadio.R
import net.ounben.AMARadio.AMARadioApp
import java.util.*

class FragmentAlarm : Fragment(), TimePickerDialog.OnTimeSetListener {
    var ram: RadioAlarmManager? = null
        private set
    private var adapterRadioAlarm: ItemAdapterRadioAlarm? = null
    private var lvAlarms: ListView? = null
    private var alarmsObserver: Observer? = null

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
        adapterRadioAlarm?.addAll(*ram?.getList() ?: emptyArray())
    }

    private var clickedAlarm: DataRadioStationAlarm? = null
    private fun clickOnItem(anObject: DataRadioStationAlarm) {
        clickedAlarm = anObject
        val newFragment = TimePickerFragment(clickedAlarm!!.hour, clickedAlarm!!.minute)
        newFragment.setCallback(this)
        newFragment.show(requireActivity().supportFragmentManager, "timePicker")
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        ram?.changeTime(clickedAlarm!!.id, hourOfDay, minute)
        view.invalidate()
    }
}
