package net.ounben.AMARadio.alarm

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import net.ounben.AMARadio.Utils
import java.util.*

class TimePickerFragment : DialogFragment, TimePickerDialog.OnTimeSetListener {
    private var callback: TimePickerDialog.OnTimeSetListener? = null
    private var initialHour: Int
    private var initialMinute: Int

    constructor() {
        val c = Calendar.getInstance()
        initialHour = c.get(Calendar.HOUR_OF_DAY)
        initialMinute = c.get(Calendar.MINUTE)
    }

    constructor(initialHour: Int, initialMinute: Int) {
        this.initialHour = initialHour
        this.initialMinute = initialMinute
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        return TimePickerDialog(activity, Utils.getTimePickerThemeResId(activity),
                this, initialHour, initialMinute, DateFormat.is24HourFormat(activity))
    }

    fun setCallback(callback: TimePickerDialog.OnTimeSetListener?) {
        this.callback = callback
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        if (callback != null) {
            callback!!.onTimeSet(view, hourOfDay, minute)
            callback = null
        }
    }
}
