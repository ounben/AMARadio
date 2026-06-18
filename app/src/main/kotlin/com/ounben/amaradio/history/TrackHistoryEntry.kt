package com.ounben.amaradio.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "track_history")
class TrackHistoryEntry {
    @PrimaryKey(autoGenerate = true)
    var uid: Int = 0

    @ColumnInfo(name = "station_uuid")
    var stationUuid: String = ""

    @ColumnInfo(name = "station_icon_url")
    var stationIconUrl: String = ""

    @ColumnInfo(name = "track")
    var track: String = ""

    @ColumnInfo(name = "artist")
    var artist: String = ""

    @ColumnInfo(name = "title")
    var title: String = ""

    @ColumnInfo(name = "art_url")
    var artUrl: String? = null

    @ColumnInfo(name = "start_time")
    var startTime: Date = Date()

    @ColumnInfo(name = "end_time")
    var endTime: Date = Date()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as TrackHistoryEntry
        if (uid != that.uid) return false
        if (stationUuid != that.stationUuid) return false
        if (track != that.track) return false
        if (artist != that.artist) return false
        if (title != that.title) return false
        if (if (artUrl != null) artUrl != that.artUrl else that.artUrl != null) return false
        if (startTime != that.startTime) return false
        return endTime == that.endTime
    }

    override fun hashCode(): Int {
        var result = uid
        result = 31 * result + stationUuid.hashCode()
        result = 31 * result + track.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (artUrl?.hashCode() ?: 0)
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        return result
    }

    companion object {
        const val MAX_HISTORY_ITEMS_IN_TABLE = 1000
        const val MAX_UNKNOWN_TRACK_DURATION = 3 * 60 * 1000 // 3 minutes
    }
}
