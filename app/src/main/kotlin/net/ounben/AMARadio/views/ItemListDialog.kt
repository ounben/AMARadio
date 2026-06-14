package net.ounben.AMARadio.views

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import net.ounben.AMARadio.R

object ItemListDialog {

    fun interface Callback {
        fun onItemSelected(resourceId: Int)
    }

    @JvmStatic
    fun create(activity: Activity, resourceIds: IntArray, callback: Callback): BottomSheetDialog {
        val bottomSheetDialog = BottomSheetDialog(activity)
        val inflater = activity.layoutInflater
        val sheetView = inflater.inflate(R.layout.dialog_generic_item_list, null)
        val viewItemsList = sheetView.findViewById<ViewGroup>(R.id.layout_items_list)

        for (resourceId in resourceIds) {
            val itemView = inflater.inflate(R.layout.dialog_generic_item, null)
            val textView = itemView.findViewById<TextView>(R.id.text)
            textView.setText(resourceId)
            textView.isClickable = true
            textView.setOnClickListener {
                callback.onItemSelected(resourceId)
                bottomSheetDialog.hide()
            }
            viewItemsList.addView(textView)
        }

        bottomSheetDialog.setContentView(sheetView)
        return bottomSheetDialog
    }
}
