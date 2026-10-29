package com.ounben.amaradio.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ounben.amaradio.station.DataRadioStation

@Entity(tableName = "recordings")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val StationUuid: String = "",
    val Name: String = "",
    val Url: String = "",
    val Homepage: String = "",
    val Favicon: String = "",
    val Country: String = "",
    val CountryCode: String = "",
    val Subcountry: String = "",
    val Tags: String = "",
    val Language: String = "",
    val Codec: String = "",
    val Bitrate: Int = 0,
    val Votes: Int = 0,
    val clickcount: Int = 0,
    val ClickTrend: Int = 0,
    val Hls: Int = 0,
    val LastChangeTime: String = "",
    val Creation: String = "",
    val ChangeUuid: String = "",
    val LastCheckOkTime: String = "",
    val fileUri: String = "",
    val timestamp: Long = 0L,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val format: String
) {
    fun toDataStation(): DataRadioStation {
        return DataRadioStation(
            StationUuid = this.StationUuid,
            Name = this.Name,
            StreamUrl = this.fileUri.ifEmpty { this.Url },
            HomePageUrl = this.Homepage,
            IconUrl = this.Favicon,
            Country = this.Country,
            CountryCode = this.CountryCode,
            State = this.Subcountry,
            TagsAll = this.Tags,
            Language = this.Language,
            Codec = this.Codec,
            Bitrate = this.Bitrate,
            Votes = this.Votes,
            ClickCount = this.clickcount,
            ClickTrend = this.ClickTrend,
            Hls = this.Hls,
            LastChangeTime = this.LastChangeTime,
            Creation = this.Creation,
            ChangeUuid = this.ChangeUuid,
            LastCheckOkTime = this.LastCheckOkTime
        )
    }
}
