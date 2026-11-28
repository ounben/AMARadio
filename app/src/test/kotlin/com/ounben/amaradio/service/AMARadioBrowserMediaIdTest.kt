package com.ounben.amaradio.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AMARadioBrowserMediaIdTest {

    @Test
    fun `playlist station mediaId round-trips playlistId and stationUuid`() {
        val playlistId = "list_uuid_123"
        val stationUuid = "station-uuid-abc-456"

        val mediaId = AMARadioBrowser.buildPlaylistStationMediaId(playlistId, stationUuid)

        assertEquals(playlistId, AMARadioBrowser.playlistIdFromMediaId(mediaId))
        assertEquals(stationUuid, AMARadioBrowser.stationIdFromMediaId(mediaId))
    }

    @Test
    fun `stationIdFromMediaId preserves stationUuid containing underscores inside playlist mediaId`() {
        val playlistId = "my_playlist_1"
        val stationUuid = "custom_station_uuid_99"

        val mediaId = AMARadioBrowser.buildPlaylistStationMediaId(playlistId, stationUuid)

        assertEquals(playlistId, AMARadioBrowser.playlistIdFromMediaId(mediaId))
        assertEquals(stationUuid, AMARadioBrowser.stationIdFromMediaId(mediaId))
    }

    @Test
    fun `regular station mediaId returns null playlistId and valid stationUuid`() {
        val mediaId = AMARadioBrowser.LEAF_PREFIX + "960e57c5-0601-11e8-ae97-52543be04c81"

        assertNull(AMARadioBrowser.playlistIdFromMediaId(mediaId))
        assertEquals("960e57c5-0601-11e8-ae97-52543be04c81", AMARadioBrowser.stationIdFromMediaId(mediaId))
    }

    @Test
    fun `playlist folder mediaId returns null playlistId for leaf parser`() {
        val folderId = AMARadioBrowser.MEDIA_ID_PLAYLIST_PREFIX + "list-1"

        assertNull(AMARadioBrowser.playlistIdFromMediaId(folderId))
    }

    @Test
    fun `null or malformed mediaIds are handled safely`() {
        assertNull(AMARadioBrowser.playlistIdFromMediaId(null))
        assertNull(AMARadioBrowser.playlistIdFromMediaId(""))
        assertNull(AMARadioBrowser.playlistIdFromMediaId(AMARadioBrowser.PLAYLIST_LEAF_PREFIX + "no-separator"))
        assertEquals("", AMARadioBrowser.stationIdFromMediaId(null))
    }
}
