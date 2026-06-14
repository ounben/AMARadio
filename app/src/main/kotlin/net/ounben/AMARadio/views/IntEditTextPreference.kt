package net.ounben.AMARadio.views

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference

/**
 * Android doesn't provide a way to have integer preferences. This is a quick hack to have them.
 * User can enter anything in the text edit but only valid integer will be saved.
 */
class IntEditTextPreference : EditTextPreference {
    private var value = 0
    private var summaryFormat: String? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        summaryFormat = summary?.toString()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        summaryFormat = summary?.toString()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        if (defaultValue == null) {
            value = getPersistedInt(0)
        } else {
            val defaultInt = parseInteger(defaultValue as String)
            value = defaultInt ?: 0
        }

        summaryFormat?.let {
            summary = String.format(it, value)
        }
    }

    override fun setText(text: String?) {
        val wasBlocking = shouldDisableDependents()
        val currentValue = parseInteger(text)
        if (currentValue != null) {
            value = currentValue
            persistInt(value)

            summaryFormat?.let {
                summary = String.format(it, value)
            }
        }

        val isBlocking = shouldDisableDependents()
        if (isBlocking != wasBlocking) {
            notifyDependencyChange(isBlocking)
        }
    }

    override fun getText(): String {
        return value.toString()
    }

    override fun getPersistedString(defaultReturnValue: String?): String {
        return getPersistedInt(value).toString()
    }

    private fun parseInteger(text: String?): Int? {
        return text?.toIntOrNull()
    }
}
