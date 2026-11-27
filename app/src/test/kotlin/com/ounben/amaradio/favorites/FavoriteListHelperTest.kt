package com.ounben.amaradio.favorites

import com.ounben.amaradio.backup.BackupData
import com.ounben.amaradio.database.user.FavoriteListEntity
import com.ounben.amaradio.database.user.FavoriteListItemEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FavoriteListHelperTest {

    private fun station(uuid: String, name: String = "Station $uuid"): DataRadioStation =
        DataRadioStation(StationUuid = uuid, Name = name, StreamUrl = "https://example.com/$uuid")

    @Test
    fun `filterStationsForList returns all favorites in original order when no list selected`() {
        val favs = listOf(station("s1"), station("s2"), station("s3"))
        val items = listOf(FavoriteListItemEntity(listId = "l1", stationUuid = "s3", displayOrder = 0))

        val result = FavoriteListHelper.filterStationsForList(favs, items, null)

        assertEquals(listOf("s1", "s2", "s3"), result.map { it.StationUuid })
    }

    @Test
    fun `filterStationsForList orders by playlist displayOrder and ignores orphaned uuids`() {
        val favs = listOf(station("s1"), station("s2"), station("s3"))
        val items = listOf(
            FavoriteListItemEntity(listId = "l1", stationUuid = "s3", displayOrder = 0, addedAt = 100),
            FavoriteListItemEntity(listId = "l1", stationUuid = "deleted_station", displayOrder = 1, addedAt = 101),
            FavoriteListItemEntity(listId = "l1", stationUuid = "s1", displayOrder = 2, addedAt = 102),
            FavoriteListItemEntity(listId = "l2", stationUuid = "s2", displayOrder = 0, addedAt = 103)
        )

        val result = FavoriteListHelper.filterStationsForList(favs, items, "l1")

        assertEquals(listOf("s3", "s1"), result.map { it.StationUuid })
    }

    @Test
    fun `computeListCounts counts only valid favorites`() {
        val favs = listOf(station("s1"), station("s2"))
        val items = listOf(
            FavoriteListItemEntity(listId = "l1", stationUuid = "s1", displayOrder = 0),
            FavoriteListItemEntity(listId = "l1", stationUuid = "orphan", displayOrder = 1),
            FavoriteListItemEntity(listId = "l1", stationUuid = "s2", displayOrder = 2),
            FavoriteListItemEntity(listId = "l2", stationUuid = "s1", displayOrder = 0)
        )

        val counts = FavoriteListHelper.computeListCounts(favs, items)

        assertEquals(2, counts["l1"])
        assertEquals(1, counts["l2"])
    }

    @Test
    fun `computeReorderedLists moves playlist up and down and normalizes displayOrder`() {
        val lists = listOf(
            FavoriteListEntity(id = "a", name = "Rock", displayOrder = 0, createdAt = 1),
            FavoriteListEntity(id = "b", name = "Jazz", displayOrder = 1, createdAt = 2),
            FavoriteListEntity(id = "c", name = "News", displayOrder = 2, createdAt = 3)
        )

        val movedUp = FavoriteListHelper.computeReorderedLists(lists, "c", -1)!!
        assertEquals(listOf("a", "c", "b"), movedUp.map { it.id })
        assertEquals(listOf(0, 1, 2), movedUp.map { it.displayOrder })

        val movedDown = FavoriteListHelper.computeReorderedLists(lists, "a", 1)!!
        assertEquals(listOf("b", "a", "c"), movedDown.map { it.id })
        assertEquals(listOf(0, 1, 2), movedDown.map { it.displayOrder })

        assertNull(FavoriteListHelper.computeReorderedLists(lists, "a", -1))
        assertNull(FavoriteListHelper.computeReorderedLists(lists, "c", 1))
        assertNull(FavoriteListHelper.computeReorderedLists(lists, "missing", 1))

        val jumpedToTop = FavoriteListHelper.computeMovedList(lists, 2, 0)!!
        assertEquals(listOf("c", "a", "b"), jumpedToTop.map { it.id })
        assertEquals(listOf(0, 1, 2), jumpedToTop.map { it.displayOrder })
        assertNull(FavoriteListHelper.computeMovedList(lists, 1, 1))
    }

    @Test
    fun `computeUpdatedListItems preserves existing order and appends newly selected favorites`() {
        val allFavs = listOf(station("s1"), station("s2"), station("s3"), station("s4"))
        val existing = listOf(
            FavoriteListItemEntity(listId = "l1", stationUuid = "s3", displayOrder = 0, addedAt = 10),
            FavoriteListItemEntity(listId = "l1", stationUuid = "s1", displayOrder = 1, addedAt = 20)
        )

        val updated = FavoriteListHelper.computeUpdatedListItems(
            listId = "l1",
            existingItems = existing,
            allFavoritesOrdered = allFavs,
            selectedUuids = setOf("s1", "s2", "s4"),
            now = 500L
        )

        assertEquals(listOf("s1", "s2", "s4"), updated.map { it.stationUuid })
        assertEquals(listOf(0, 1, 2), updated.map { it.displayOrder })
        assertEquals(20L, updated[0].addedAt)
        assertEquals(500L, updated[1].addedAt)
    }

    @Test
    fun `BackupData v2 json without playlists deserializes cleanly and v3 round-trips`() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val legacyV2Json = """
            {
              "version": 2,
              "createdAt": 1700000000000,
              "favorites": [],
              "customStations": [],
              "filterTabs": [],
              "settings": {}
            }
        """.trimIndent()

        val decodedLegacy = json.decodeFromString<BackupData>(legacyV2Json)
        assertEquals(2, decodedLegacy.version)
        assertTrue(decodedLegacy.favoriteLists.isEmpty())
        assertTrue(decodedLegacy.favoriteListItems.isEmpty())

        val v3 = BackupData(
            version = 3,
            createdAt = 1700000000001,
            favoriteLists = listOf(FavoriteListEntity(id = "l1", name = "Auto", displayOrder = 0, createdAt = 10)),
            favoriteListItems = listOf(FavoriteListItemEntity(listId = "l1", stationUuid = "s1", displayOrder = 0, addedAt = 20))
        )
        val roundTripped = json.decodeFromString<BackupData>(json.encodeToString(v3))
        assertEquals(v3.favoriteLists, roundTripped.favoriteLists)
        assertEquals(v3.favoriteListItems, roundTripped.favoriteListItems)
    }
}
