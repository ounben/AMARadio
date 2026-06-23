package com.ounben.amaradio.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.ounben.amaradio.R
import com.ounben.amaradio.data.DataStatistics

class ItemAdapterStatistics(context: Context, private val resourceId: Int) : ArrayAdapter<DataStatistics>(context, resourceId) {
    private val ctx: Context = context

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val aData = getItem(position)!!
        var v = convertView
        if (v == null) {
            val vi = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as? LayoutInflater
            v = vi?.inflate(resourceId, null)
        }
        if (v == null) return View(ctx) // Fallback
        val aTextViewTop = v.findViewById<TextView>(R.id.stats_name)
        val aTextViewBottom = v.findViewById<TextView>(R.id.stats_value)
        aTextViewTop?.text = aData.Name
        aTextViewBottom?.text = aData.Value
        return v
    }
}
