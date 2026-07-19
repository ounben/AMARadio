package com.ounben.amaradio.station

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation
import com.ounben.amaradio.CountryCodeDictionary
import com.ounben.amaradio.R
import com.ounben.amaradio.StationSaveManager
import com.ounben.amaradio.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Serializable
@Parcelize
class DataRadioStation : Parcelable {
    @SerialName("name") var Name: String = ""
    @SerialName("stationuuid") var StationUuid: String = ""
    @SerialName("changeuuid") var ChangeUuid: String = ""
    @SerialName("url") var StreamUrl: String = ""
    @SerialName("homepage") var HomePageUrl: String = ""
    @SerialName("favicon") var IconUrl: String = ""
    @SerialName("country") var Country: String = ""
    @SerialName("countrycode") var CountryCode: String = ""
    @SerialName("state") var State: String = ""
    @SerialName("tags") var TagsAll: String = ""
    @SerialName("language") var Language: String = ""
    @SerialName("clickcount") var ClickCount: Int = 0
    @SerialName("clicktrend") var ClickTrend: Int = 0
    @SerialName("votes") var Votes: Int = 0
    @SerialName("bitrate") var Bitrate: Int = 0
    @SerialName("codec") var Codec: String = ""
    @SerialName("lastchangetime") var LastChangeTime: String = ""
    @SerialName("creation") var Creation: String = ""
    @SerialName("lastcheckoktime") var LastCheckOkTime: String = ""

    @IgnoredOnParcel @Transient var RefreshRetryCount: Int = 0
    @IgnoredOnParcel @Transient var Working: Boolean = true
    @IgnoredOnParcel @Transient var playableUrl: String? = null

    @IgnoredOnParcel
    @Transient var queue: StationSaveManager? = null

    @IgnoredOnParcel
    @Transient private var shortDetailsCache: String? = null
    @IgnoredOnParcel
    @Transient private var longDetailsCache: String? = null

    fun getShortDetails(context: Context): String {
        shortDetailsCache?.let { return it }
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
        val result = TextUtils.join(" | ", list)
        shortDetailsCache = result
        return result
    }

    fun getLongDetails(context: Context): String {
        longDetailsCache?.let { return it }
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
        if (!TextUtils.isEmpty(CountryCode)) {
            val localizedCountry = CountryCodeDictionary.instance.getCountryByCode(CountryCode)
            if (!localizedCountry.isNullOrEmpty()) {
                list.add(localizedCountry)
            } else if (!TextUtils.isEmpty(Country)) {
                list.add(Country)
            }
        } else if (!TextUtils.isEmpty(Country)) {
            list.add(Country)
        }
        if (!TextUtils.isEmpty(State)) {
            list.add(State)
        }
        val result = TextUtils.join(" | ", list)
        longDetailsCache = result
        return result
    }

    fun hasIcon(): Boolean {
        return !TextUtils.isEmpty(IconUrl)
    }

    suspend fun refresh(httpClient: OkHttpClient, context: Context): Boolean {
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

    /**
     * Creates a lightweight copy of the station for IPC (Binder) transport.
     * Prevents TransactionTooLargeException with large metadata or Chinese characters.
     */
    fun getLightweightCopy(): DataRadioStation {
        val copy = DataRadioStation()
        copy.Name = this.Name
        copy.StationUuid = this.StationUuid
        copy.StreamUrl = this.StreamUrl
        copy.IconUrl = this.IconUrl
        copy.CountryCode = this.CountryCode
        // We omit TagsAll and heavy descriptions to save space in Binder buffer
        return copy
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
            Utils.resourceToUri(context.resources, R.mipmap.ic_elgato_launcher).toString()
        } else {
            IconUrl
        }

        CoroutineScope(Dispatchers.IO).launch {
            val request = ImageRequest.Builder(context)
                .data(url)
                .error(R.mipmap.ic_elgato_launcher)
                .size(128, 128)
                .transformations(RoundedCornersTransformation(12f))
                .build()
            
            val imageResult = context.imageLoader.execute(request)
            if (imageResult is SuccessResult) {
                val bitmap = (imageResult.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null && Build.VERSION.SDK_INT >= 25) {
                    val shortcut = Utils.createShortcut(context, this@DataRadioStation, bitmap)
                    withContext(Dispatchers.Main) {
                        cb.onShortcutReadyListener(shortcut)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DATAStation"
        const val MAX_REFRESH_RETRIES = 16
        const val RADIO_STATION_LOCAL_INFO_CHAGED = "com.ounben.amaradio.radiostation.changed"
        const val RADIO_STATION_UUID = "UUID"

        private val jsonConfig = Json { 
            ignoreUnknownKeys = true 
            coerceInputValues = true
            encodeDefaults = true
        }

        @JvmStatic
        fun DecodeJson(json: String?): List<DataRadioStation>? {
            if (json == null) return null
            val trimmedJson = json.trim()
            if (!trimmedJson.startsWith("[")) {
                if (trimmedJson.startsWith("<!DOCTYPE", ignoreCase = true) || trimmedJson.startsWith("<html", ignoreCase = true)) {
                    Log.w(TAG, "DecodeJson: Received HTML instead of JSON.")
                } else {
                    Log.e(TAG, "DecodeJson: Invalid JSON format (not an array)")
                }
                return null
            }
            return try {
                jsonConfig.decodeFromString<List<DataRadioStation>>(trimmedJson)
            } catch (e: Exception) {
                Log.e(TAG, "DecodeJson exception: ", e)
                null
            }
        }
    }
}
