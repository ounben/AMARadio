package net.ounben.AMARadio.alarm

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import net.ounben.AMARadio.BuildConfig
import net.ounben.AMARadio.R
import net.ounben.AMARadio.AMARadioApp
import java.util.*

class ItemAdapterRadioAlarm(context: Context?) : ArrayAdapter<DataRadioStationAlarm>(context!!, R.layout.list_item_alarm) {
    private val ctx: Context = context!!
    private var ram: RadioAlarmManager? = null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val AMARadioApp = ctx.applicationContext as AMARadioApp
        ram = AMARadioApp.alarmManager
        val aData = getItem(position)!!
        var v = convertView
        val vi = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        if (v == null) {
            v = vi.inflate(R.layout.list_item_alarm, null)
        }
        val tvStation = v!!.findViewById<TextView>(R.id.textViewStation)
        val tvTime = v.findViewById<TextView>(R.id.textViewTime)
        val s = v.findViewById<SwitchCompat>(R.id.switch1)
        val b = v.findViewById<ImageButton>(R.id.buttonDeleteAlarm)
        val buttonRepeating = v.findViewById<ImageButton>(R.id.checkboxRepeating)
        val repeatDaysView = v.findViewById<LinearLayout>(R.id.repeatDaysView)
        if (repeatDaysView.childCount < 1) {
            populateWeekDayButtons(aData, vi, repeatDaysView)
        }
        buttonRepeating.setOnClickListener { ram?.toggleRepeating(aData.id) }
        b?.setOnClickListener { ram?.remove(aData.id) }
        tvStation?.text = aData.station?.Name
        tvTime?.text = String.format(Locale.getDefault(), "%02d:%02d", aData.hour, aData.minute)
        if (s != null) {
            s.isChecked = aData.enabled
            s.setOnCheckedChangeListener { _, isChecked ->
                if (BuildConfig.DEBUG) {
                    Log.d("ALARM", "new state:$isChecked")
                }
                ram?.setEnabled(aData.id, isChecked)
            }
        }
        repeatDaysView.visibility = if (aData.repeating) View.VISIBLE else View.GONE
        buttonRepeating.contentDescription = ctx.resources.getString(if (aData.repeating) R.string.image_button_dont_repeat else R.string.image_button_repeat)
        return v
    }

    private fun populateWeekDayButtons(aData: DataRadioStationAlarm, vi: LayoutInflater, repeatDays: LinearLayout) {
        val mShortWeekDayStrings = ctx.resources.getStringArray(R.array.weekdays)
        for (i in 0..6) {
            val viewGroup = vi.inflate(R.layout.day_button, repeatDays, false) as ViewGroup
            val button = viewGroup.getChildAt(0) as ToggleButton
            repeatDays.addView(viewGroup)
            button.id = i
            button.text = mShortWeekDayStrings[i]
            button.textOn = mShortWeekDayStrings[i]
            button.textOff = mShortWeekDayStrings[i]
            button.contentDescription = mShortWeekDayStrings[i]
            if (aData.weekDays.contains(i)) {
                button.isChecked = true
            }
            button.setOnClickListener { view ->
                val bid = view.id
                ram?.changeWeekDays(aData.id, bid)
            }
        }
    }
}
