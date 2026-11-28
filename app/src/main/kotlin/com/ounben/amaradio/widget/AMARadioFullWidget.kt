package com.ounben.amaradio.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.lazy.*
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.R
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.serialization.json.Json

class AMARadioFullWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val activeTab = (prefs[WidgetState.activeTabKey] ?: "favorites").toString()
            val playingUuid = (prefs[WidgetState.stationUuidKey] ?: "").toString()
            val playingIconUrl = (prefs[WidgetState.stationIconUrlKey] ?: "").toString()
            val isPlaying = prefs[WidgetState.isPlayingKey] ?: false
            val currentName = (prefs[WidgetState.stationNameKey] ?: context.getString(R.string.app_name)).toString()
            val currentDetails = (prefs[WidgetState.stationDetailsKey] ?: "").toString()
            val currentTrack = (prefs[WidgetState.currentTrackKey] ?: "").toString()
            val selectedPlaylistId = prefs[WidgetState.selectedPlaylistIdKey] ?: ""
            val playlistPickerOpen = prefs[WidgetState.playlistPickerOpenKey] ?: false
            val totalFavoritesCount = prefs[WidgetState.totalFavoritesCountKey] ?: 0
            
            @Suppress("UNUSED_VARIABLE")
            val counter = prefs[WidgetState.updateCounterKey] ?: 0

            // Choose the correct JSON list based on active tab
            val jsonStr = if (activeTab == "favorites") {
                prefs[WidgetState.favoritesJsonKey]
            } else {
                prefs[WidgetState.historyJsonKey]
            } ?: "[]"

            val stations = try {
                json.decodeFromString<List<DataRadioStation>>(jsonStr)
            } catch (e: Exception) {
                Log.e("FullWidget", "JSON decode error", e)
                emptyList()
            }

            val playlistsJson = prefs[WidgetState.playlistsJsonKey] ?: "[]"
            val playlists = try {
                json.decodeFromString<List<WidgetPlaylistSummary>>(playlistsJson)
            } catch (e: Exception) {
                Log.e("FullWidget", "Playlists JSON decode error", e)
                emptyList()
            }

            GlanceTheme(colors = WidgetTheme.colors(context)) {
                FullWidgetContent(
                    context = context,
                    name = currentName,
                    details = currentDetails,
                    trackInfo = currentTrack,
                    playingUuid = playingUuid,
                    playingIconUrl = playingIconUrl,
                    isPlaying = isPlaying,
                    activeTab = activeTab,
                    stations = stations,
                    playlists = playlists,
                    selectedPlaylistId = selectedPlaylistId,
                    playlistPickerOpen = playlistPickerOpen,
                    totalFavoritesCount = totalFavoritesCount
                )
            }
        }
    }

    @Composable
    private fun FullWidgetContent(
        context: Context,
        name: String,
        details: String,
        trackInfo: String,
        playingUuid: String,
        playingIconUrl: String,
        isPlaying: Boolean,
        activeTab: String,
        stations: List<DataRadioStation>,
        playlists: List<WidgetPlaylistSummary>,
        selectedPlaylistId: String,
        playlistPickerOpen: Boolean,
        totalFavoritesCount: Int
    ) {
        val backgroundColor = GlanceTheme.colors.background
        val textColor = GlanceTheme.colors.onBackground
        val secondaryColor = GlanceTheme.colors.onSurfaceVariant
        val surfaceVariantColor = GlanceTheme.colors.surfaceVariant
        val outlineColor = GlanceTheme.colors.outline
        val amber = GlanceTheme.colors.primary
        val onAmber = GlanceTheme.colors.onPrimary

        val activePlaylist = playlists.find { it.id == selectedPlaylistId }
        val effectiveSelectedId = activePlaylist?.id ?: ""
        val showPlaylistFilter = activeTab == "favorites" && playlists.isNotEmpty()
        val showInlinePicker = showPlaylistFilter && playlistPickerOpen

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            // 1. Header (Player)
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = WidgetImageLoader.getStationImage(context, playingUuid, name, playingIconUrl),
                    contentDescription = name,
                    modifier = GlanceModifier.size(40.dp).clickable(actionStartActivity<ActivityMain>()),
                    contentScale = ContentScale.FillBounds
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<ActivityMain>())) {
                    Text(text = name, style = TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    val subTitle = if (isPlaying && trackInfo.isNotEmpty()) trackInfo else details
                    Text(text = subTitle, style = TextStyle(color = secondaryColor, fontSize = 11.sp), maxLines = 1)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_skip_previous_24dp),
                        contentDescription = context.getString(R.string.action_skip_to_previous),
                        modifier = GlanceModifier.size(32.dp).padding(4.dp).clickable(
                            actionStartService(Intent(context, PlayerService::class.java).apply {
                                action = PlayerService.ACTION_SKIP_TO_PREVIOUS
                            }, isForegroundService = true)
                        ),
                        colorFilter = ColorFilter.tint(textColor)
                    )
                    val playIcon = if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp
                    Image(
                        provider = ImageProvider(playIcon),
                        contentDescription = "Play/Pause",
                        modifier = GlanceModifier.size(36.dp).padding(4.dp).clickable(
                            actionStartService(Intent(context, PlayerService::class.java).apply {
                                action = PlayerService.ACTION_TOGGLE_PLAY_PAUSE
                            }, isForegroundService = true)
                        ),
                        colorFilter = ColorFilter.tint(amber)
                    )
                    Image(
                        provider = ImageProvider(R.drawable.ic_skip_next_24dp),
                        contentDescription = context.getString(R.string.action_skip_to_next),
                        modifier = GlanceModifier.size(32.dp).padding(4.dp).clickable(
                            actionStartService(Intent(context, PlayerService::class.java).apply {
                                action = PlayerService.ACTION_SKIP_TO_NEXT
                            }, isForegroundService = true)
                        ),
                        colorFilter = ColorFilter.tint(textColor)
                    )
                }
            }

            // 2. Tab Bar (per-widget-instance via Glance ActionCallback)
            Row(modifier = GlanceModifier.fillMaxWidth().height(38.dp).background(surfaceVariantColor)) {
                TabItem(
                    label = context.getString(R.string.nav_item_starred),
                    isActive = activeTab == "favorites",
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    onClick = {
                        actionRunCallback<SwitchTabActionCallback>(
                            actionParametersOf(SwitchTabActionCallback.tabKey to "favorites")
                        )
                    },
                    activeColor = amber,
                    inactiveColor = secondaryColor
                )
                TabItem(
                    label = context.getString(R.string.nav_item_history),
                    isActive = activeTab == "history",
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    onClick = {
                        actionRunCallback<SwitchTabActionCallback>(
                            actionParametersOf(SwitchTabActionCallback.tabKey to "history")
                        )
                    },
                    activeColor = amber,
                    inactiveColor = secondaryColor
                )
            }

            // 2b. Playlist Filter Bar (Favorites tab only, when playlists exist)
            if (showPlaylistFilter) {
                PlaylistFilterBar(
                    context = context,
                    activePlaylist = activePlaylist,
                    totalFavoritesCount = totalFavoritesCount,
                    pickerOpen = showInlinePicker,
                    backgroundColor = backgroundColor,
                    surfaceVariantColor = surfaceVariantColor,
                    outlineColor = outlineColor,
                    textColor = textColor,
                    amber = amber,
                    onAmber = onAmber
                )
            }

            // 3. Body (LazyColumn) - Inline Playlist Picker OR Filtered Station List
            if (showInlinePicker) {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    item(itemId = "picker:__all__".hashCode().toLong()) {
                        PlaylistPickerRow(
                            label = "${context.getString(R.string.favorite_list_all)} ($totalFavoritesCount)",
                            isSelected = effectiveSelectedId.isEmpty(),
                            playlistId = "",
                            textColor = textColor,
                            amber = amber,
                            surfaceVariantColor = surfaceVariantColor
                        )
                    }
                    items(
                        items = playlists,
                        itemId = { "picker:${it.id}".hashCode().toLong() }
                    ) { playlist ->
                        PlaylistPickerRow(
                            label = "${playlist.name} (${playlist.count})",
                            isSelected = playlist.id == effectiveSelectedId,
                            playlistId = playlist.id,
                            textColor = textColor,
                            amber = amber,
                            surfaceVariantColor = surfaceVariantColor
                        )
                    }
                }
            } else {
                val activeListForQueue = if (activeTab == "favorites") effectiveSelectedId else ""
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items = stations, itemId = { it.StationUuid.hashCode().toLong() }) { station ->
                        StationRow(
                            context = context,
                            station = station,
                            playlistId = activeListForQueue,
                            isActive = station.StationUuid == playingUuid && isPlaying,
                            textColor = textColor,
                            secondaryColor = secondaryColor,
                            amber = amber
                        )
                    }
                    if (stations.isEmpty()) {
                        item {
                            val emptyText = if (activeTab == "favorites" && effectiveSelectedId.isNotEmpty()) {
                                context.getString(R.string.favorite_list_empty)
                            } else {
                                context.getString(R.string.searchpreference_no_results)
                            }
                            Box(modifier = GlanceModifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = emptyText,
                                    style = TextStyle(color = secondaryColor, fontSize = 13.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PlaylistFilterBar(
        context: Context,
        activePlaylist: WidgetPlaylistSummary?,
        totalFavoritesCount: Int,
        pickerOpen: Boolean,
        backgroundColor: ColorProvider,
        surfaceVariantColor: ColorProvider,
        outlineColor: ColorProvider,
        textColor: ColorProvider,
        amber: ColorProvider,
        onAmber: ColorProvider
    ) {
        val isFiltered = activePlaylist != null
        val filterLabel = if (activePlaylist != null) {
            "${activePlaylist.name} (${activePlaylist.count})"
        } else {
            "${context.getString(R.string.favorite_list_all)} ($totalFavoritesCount)"
        }
        val blockBorderColor = if (isFiltered) amber else outlineColor
        val blockBg = if (isFiltered) amber else surfaceVariantColor
        val blockTextColor = if (isFiltered) onAmber else textColor
        val blockIconTint = if (isFiltered) onAmber else amber
        val expandIcon = if (pickerOpen) R.drawable.ic_expand_less_black_24dp else R.drawable.ic_expand_more_black_24dp

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(backgroundColor)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick cycle backward (block button)
                Box(
                    modifier = GlanceModifier
                        .width(30.dp)
                        .height(30.dp)
                        .background(outlineColor)
                        .cornerRadius(4.dp)
                        .padding(1.dp)
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(surfaceVariantColor)
                            .cornerRadius(3.dp)
                            .clickable(
                                actionRunCallback<CyclePlaylistActionCallback>(
                                    actionParametersOf(CyclePlaylistActionCallback.deltaKey to -1)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_chevron_left_24dp),
                            contentDescription = context.getString(R.string.action_skip_to_previous),
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(textColor)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(4.dp))

                // Center Filter Block (minimal corner radius + 1dp border)
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(30.dp)
                        .background(blockBorderColor)
                        .cornerRadius(4.dp)
                        .padding(1.dp)
                ) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(blockBg)
                            .cornerRadius(3.dp)
                            .padding(horizontal = 8.dp)
                            .clickable(actionRunCallback<TogglePlaylistPickerActionCallback>()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_filter_24dp),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(blockIconTint)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = filterLabel,
                            style = TextStyle(
                                color = blockTextColor,
                                fontSize = 12.sp,
                                fontWeight = if (isFiltered || pickerOpen) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Image(
                            provider = ImageProvider(expandIcon),
                            contentDescription = filterLabel,
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(blockTextColor)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(4.dp))

                // Quick cycle forward (block button)
                Box(
                    modifier = GlanceModifier
                        .width(30.dp)
                        .height(30.dp)
                        .background(outlineColor)
                        .cornerRadius(4.dp)
                        .padding(1.dp)
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(surfaceVariantColor)
                            .cornerRadius(3.dp)
                            .clickable(
                                actionRunCallback<CyclePlaylistActionCallback>(
                                    actionParametersOf(CyclePlaylistActionCallback.deltaKey to 1)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_chevron_right_24dp),
                            contentDescription = context.getString(R.string.action_skip_to_next),
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(textColor)
                        )
                    }
                }
            }

            // Crisp 1dp divider between filter bar and station list / picker
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(outlineColor)
            )
        }
    }

    @Composable
    private fun PlaylistPickerRow(
        label: String,
        isSelected: Boolean,
        playlistId: String,
        textColor: ColorProvider,
        amber: ColorProvider,
        surfaceVariantColor: ColorProvider
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(40.dp)
                .then(
                    if (isSelected) GlanceModifier.background(surfaceVariantColor) else GlanceModifier
                )
                .clickable(
                    actionRunCallback<SelectPlaylistActionCallback>(
                        actionParametersOf(SelectPlaylistActionCallback.playlistIdKey to playlistId)
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Spacer(
                    modifier = GlanceModifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(amber)
                )
            }
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(horizontal = if (isSelected) 9.dp else 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = if (isSelected) amber else textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (isSelected) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_check_24dp),
                        contentDescription = null,
                        modifier = GlanceModifier.size(18.dp),
                        colorFilter = ColorFilter.tint(amber)
                    )
                }
            }
        }
    }

    @Composable
    private fun TabItem(label: String, isActive: Boolean, modifier: GlanceModifier, onClick: () -> Action, activeColor: ColorProvider, inactiveColor: ColorProvider) {
        Box(modifier = modifier.clickable(onClick()), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label.uppercase(),
                    style = TextStyle(
                        color = if (isActive) activeColor else inactiveColor,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                )
                if (isActive) {
                    Spacer(modifier = GlanceModifier.height(2.dp).width(40.dp).background(activeColor))
                }
            }
        }
    }

    @Composable
    private fun StationRow(
        context: Context,
        station: DataRadioStation,
        playlistId: String,
        isActive: Boolean,
        textColor: ColorProvider,
        secondaryColor: ColorProvider,
        amber: ColorProvider
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable(
                    actionSendBroadcast(Intent(context, AMARadioWidgetActionReceiver::class.java).apply {
                        action = AMARadioWidgetActionReceiver.ACTION_PLAY_STATION
                        putExtra(AMARadioWidgetActionReceiver.EXTRA_STATION_UUID, station.StationUuid)
                        if (playlistId.isNotEmpty()) {
                            putExtra(AMARadioWidgetActionReceiver.EXTRA_PLAYLIST_ID, playlistId)
                        }
                    })
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = WidgetImageLoader.getStationImage(context, station.StationUuid, station.Name, station.IconUrl),
                contentDescription = station.Name,
                modifier = GlanceModifier.size(36.dp),
                contentScale = ContentScale.FillBounds
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = station.Name,
                    style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = station.TagsAll,
                    style = TextStyle(color = secondaryColor, fontSize = 10.sp),
                    maxLines = 1
                )
            }
            if (isActive) {
                Image(
                    provider = ImageProvider(R.drawable.ic_play_arrow_24dp),
                    contentDescription = "Playing",
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(amber)
                )
            }
        }
    }
}
