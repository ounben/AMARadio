package net.ounben.AMARadio.data

import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject

class DataStatistics {
    var Name: String = ""
    var Value: String = ""

    companion object {
        @JvmStatic
        fun DecodeJson(result: String?): Array<DataStatistics> {
            val aList = ArrayList<DataStatistics>()
            if (result != null && TextUtils.isGraphic(result)) {
                try {
                    val jsonObject = JSONObject(result)
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next() as String
                        val aData = DataStatistics()
                        aData.Name = key
                        aData.Value = jsonObject.getString(key)
                        aList.add(aData)
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            return aList.toTypedArray()
        }
    }
}
