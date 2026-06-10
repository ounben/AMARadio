package net.ounben.AMARadio.station

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import jp.wasabeef.transformers.picasso.CropCircleTransformation
import jp.wasabeef.transformers.picasso.CropSquareTransformation
import jp.wasabeef.transformers.picasso.RoundedCornersTransformation
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import net.ounben.AMARadio.R
import net.ounben.AMARadio.StationSaveManager
import net.ounben.AMARadio.Utils
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

@Parcelize
class DataRadioStation : Parcelable {
    var Name: String = ""
    var StationUuid: String = ""
    var ChangeUuid: String = ""
    var StreamUrl: String = ""
    var HomePageUrl: String = ""
    var IconUrl: String = ""
    var Country: String = ""
    var CountryCode: String = ""
    var State: String = ""
    var TagsAll: String = ""
    var Language: String = ""
    var ClickCount: Int = 0
    var ClickTrend: Int = 0
    var Votes: Int = 0
    var RefreshRetryCount: Int = 0
    var Bitrate: Int = 0
    var Codec: String = ""
    var Working: Boolean = true
    var Hls: Boolean = false
    var DeletedOnServer: Boolean = false
    var playableUrl: String? = null

    @IgnoredOnParcel
    var queue: StationSaveManager? = null

    val stationId: String
        get() = StationUuid

    fun getShortDetails(context: Context): String {
        val list: MutableList<String?> = ArrayList()
        if (!TextUtils.isEmpty(Language)) {
            list.add(Language)
        }
        if (Bitrate > 0) {
            list.add("$Bitrate kbps")
        }
        if (!TextUtils.isEmpty(Codec)) {
            list.add(Codec)
        }
        if (ClickCount > 0) {
            list.add(context.getString(R.string.station_details_clicks, ClickCount))
        }
        if (Votes > 0) {
            list.add(context.getString(R.string.station_details_votes, Votes))
        }
        return TextUtils.join(" | ", list)
    }

    fun getLongDetails(context: Context): String {
        val list: MutableList<String?> = ArrayList()
        if (!TextUtils.isEmpty(Language)) {
            list.add(Language)
        }
        if (Bitrate > 0) {
            list.add("$Bitrate kbps")
        }
        if (!TextUtils.isEmpty(Codec)) {
            list.add(Codec)
        }
        if (ClickCount > 0) {
            list.add(context.getString(R.string.station_details_clicks, ClickCount))
        }
        if (Votes > 0) {
            list.add(context.getString(R.string.station_details_votes, Votes))
        }
        if (!TextUtils.isEmpty(Country)) {
            list.add(Country)
        }
        if (!TextUtils.isEmpty(State)) {
            list.add(State)
        }
        return TextUtils.join(" | ", list)
    }

    fun hasIcon(): Boolean {
        return !TextUtils.isEmpty(IconUrl)
    }

    fun fixStationFields() {
        if (TagsAll == null) TagsAll = ""
        if (playableUrl == null) playableUrl = ""
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("name", Name)
            json.put("stationuuid", StationUuid)
            json.put("changeuuid", ChangeUuid)
            json.put("url", StreamUrl)
            json.put("homepage", HomePageUrl)
            json.put("favicon", IconUrl)
            json.put("country", Country)
            json.put("countrycode", CountryCode)
            json.put("state", State)
            json.put("tags", TagsAll)
            json.put("language", Language)
            json.put("clickcount", ClickCount)
            json.put("clicktrend", ClickTrend)
            json.put("votes", Votes)
            json.put("bitrate", Bitrate)
            json.put("codec", Codec)
        } catch (e: JSONException) {
            Log.e(TAG, "toJson: ", e)
        }
        return json
    }

    fun refresh(httpClient: OkHttpClient, context: Context): Boolean {
        RefreshRetryCount++
        val result = Utils.getStationByUuid(httpClient, context, StationUuid)
        if (result != null) {
            copyPropertiesFrom(result)
            RefreshRetryCount = 0
            return true
        }
        return false
    }

    fun hasValidUuid(): Boolean {
        return !TextUtils.isEmpty(StationUuid) && StationUuid != "null"
    }

    fun copyPropertiesFrom(other: DataRadioStation) {
        Name = other.Name
        StationUuid = other.StationUuid
        ChangeUuid = other.ChangeUuid
        StreamUrl = other.StreamUrl
        HomePageUrl = other.HomePageUrl
        IconUrl = other.IconUrl
        Country = other.Country
        CountryCode = other.CountryCode
        State = other.State
        TagsAll = other.TagsAll
        Language = other.Language
        ClickCount = other.ClickCount
        ClickTrend = other.ClickTrend
        Votes = other.Votes
        Bitrate = other.Bitrate
        Codec = other.Codec
    }

    fun interface ShortcutReadyListener {
        fun onShortcutReadyListener(shortcut: ShortcutInfo)
    }

    fun prepareShortcut(context: Context, cb: ShortcutReadyListener) {
        val url = if (!hasIcon()) {
            Utils.resourceToUri(context.resources, R.drawable.ic_launcher).toString()
        } else {
            IconUrl
        }

        Picasso.get()
            .load(url)
            .error(R.drawable.ic_launcher)
            .transform(CropSquareTransformation())
            .transform(RoundedCornersTransformation(12))
            .into(RadioIconTarget(context, this, cb))
    }

    private class RadioIconTarget(private val ctx: Context, private val station: DataRadioStation, private val cb: ShortcutReadyListener) : Target {
        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
            if (Build.VERSION.SDK_INT >= 25) {
                cb.onShortcutReadyListener(Utils.createShortcut(ctx, station, bitmap))
            }
        }

        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
            if (errorDrawable is BitmapDrawable) {
                onBitmapLoaded(errorDrawable.bitmap, Picasso.LoadedFrom.DISK)
            }
        }

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
    }

    companion object {
        private const val TAG = "DATAStation"
        const val MAX_REFRESH_RETRIES = 16
        const val RADIO_STATION_LOCAL_INFO_CHAGED = "net.ounben.AMARadio.radiostation.changed"
        const val RADIO_STATION_UUID = "UUID"

        @JvmStatic
        fun DecodeJson(json: String?): List<DataRadioStation>? {
            if (json == null) return null
            val list: MutableList<DataRadioStation> = ArrayList()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(DecodeJsonObject(obj))
                }
            } catch (e: JSONException) {
                Log.e(TAG, "DecodeJson: ", e)
            }
            return list
        }

        @JvmStatic
        fun DecodeJsonSingle(json: String?): DataRadioStation? {
            if (json == null) return null
            try {
                val obj = JSONObject(json)
                return DecodeJsonObject(obj)
            } catch (e: JSONException) {
                Log.e(TAG, "DecodeJsonSingle: ", e)
            }
            return null
        }

        private fun DecodeJsonObject(obj: JSONObject): DataRadioStation {
            val station = DataRadioStation()
            station.Name = obj.optString("name")
            station.StationUuid = obj.optString("stationuuid")
            station.ChangeUuid = obj.optString("changeuuid")
            station.StreamUrl = obj.optString("url")
            station.HomePageUrl = obj.optString("homepage")
            station.IconUrl = obj.optString("favicon")
            station.Country = obj.optString("country")
            station.CountryCode = obj.optString("countrycode")
            station.State = obj.optString("state")
            station.TagsAll = obj.optString("tags")
            station.Language = obj.optString("language")
            station.ClickCount = obj.optInt("clickcount")
            station.ClickTrend = obj.optInt("clicktrend")
            station.Votes = obj.optInt("votes")
            station.Bitrate = obj.optInt("bitrate")
            station.Codec = obj.optString("codec")
            return station
        }
    }
}
