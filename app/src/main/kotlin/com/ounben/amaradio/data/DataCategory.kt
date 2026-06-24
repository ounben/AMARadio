package com.ounben.amaradio.data

import android.graphics.drawable.Drawable
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException

class DataCategory : Comparable<DataCategory> {
    var Name: String = ""
    var UsedCount: Int = 0
    var Label: String? = null
    var Icon: Drawable? = null

    val sortField: String
        get() = Label ?: Name

    override fun compareTo(other: DataCategory): Int {
        return sortField.compareTo(other.sortField, ignoreCase = true)
    }

    companion object {
        @JvmStatic
        fun DecodeJson(result: String?): Array<DataCategory> {
            val aList = ArrayList<DataCategory>()
            if (result != null) {
                val trimmedResult = result.trim()
                if (trimmedResult.startsWith("[")) {
                    try {
                        val jsonArray = JSONArray(trimmedResult)
                        for (i in 0 until jsonArray.length()) {
                            val anObject = jsonArray.getJSONObject(i)
                            val aData = DataCategory()
                            aData.Name = anObject.getString("name")
                            aData.UsedCount = anObject.getInt("stationcount")
                            aList.add(aData)
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                } else if (trimmedResult.startsWith("<html", ignoreCase = true) || trimmedResult.startsWith("<!DOCTYPE", ignoreCase = true)) {
                    android.util.Log.w("DataCategory", "DecodeJson: Received HTML instead of JSON.")
                }
            }
            return aList.toTypedArray()
        }
    }
}
