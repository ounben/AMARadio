package com.ounben.amaradio.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.*
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.datastore.preferences.core.Preferences
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.R
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.*
import com.ounben.amaradio.station.DataRadioStation

class AMARadioFullWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val userDb = AMARadioUserDatabase.getDatabase(context)
        
        // 1. FETCH BOTH LISTS (30 each) before content. 
        val favoriteStations = userDb.favoriteDao().getAllFavorites().take(30).map { it.toDataStation() }
        val historyStations = userDb.historyDao().getAllHistory().take(30).map { it.toDataStation() }

        provideContent {
            val prefs = currentState<Preferences>()
            val activeTab = (prefs[WidgetState.activeTabKey] ?: "favorites").toString()
            val playingUuid = (prefs[WidgetState.stationUuidKey] ?: "").toString()
            val playingIconUrl = (prefs[WidgetState.stationIconUrlKey] ?: "").toString()
            val isPlaying = prefs[WidgetState.isPlayingKey] ?: false
            val currentName = (prefs[WidgetState.stationNameKey] ?: context.getString(R.string.app_name)).toString()
            val currentDetails = (prefs[WidgetState.stationDetailsKey] ?: "").toString()

            // 2. REACTIVE SWITCH
            val stationsToShow = if (activeTab == "favorites") favoriteStations else historyStations

            FullWidgetContent(context, currentName, currentDetails, playingUuid, playingIconUrl, isPlaying, activeTab, stationsToShow)
        }
    }
}

@Composable
fun FullWidgetContent(
    context: Context,
    name: String,
    details: String,
    playingUuid: String,
    playingIconUrl: String,
    isPlaying: Boolean,
    activeTab: String,
    stations: List<DataRadioStation>
) {
    val backgroundColor = ColorProvider(R.color.widget_bg)
    val textColor = ColorProvider(R.color.widget_on_bg)
    val secondaryColor = ColorProvider(R.color.widget_on_bg_secondary)
    val surfaceVariantColor = ColorProvider(R.color.widget_surface_variant)
    val amber = ColorProvider(WidgetTheme.Amber)

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
                Text(text = details, style = TextStyle(color = secondaryColor, fontSize = 11.sp), maxLines = 1)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_skip_previous_24dp),
                    contentDescription = "Prev",
                    modifier = GlanceModifier.size(32.dp).padding(4.dp).clickable(actionRunCallback<PreviousActionCallback>()),
                    colorFilter = ColorFilter.tint(textColor)
                )
                val playIcon = if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp
                Image(
                    provider = ImageProvider(playIcon),
                    contentDescription = "Play/Pause",
                    modifier = GlanceModifier.size(36.dp).padding(4.dp).clickable(actionRunCallback<PlayPauseActionCallback>()),
                    colorFilter = ColorFilter.tint(amber)
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_skip_next_24dp),
                    contentDescription = "Next",
                    modifier = GlanceModifier.size(32.dp).padding(4.dp).clickable(actionRunCallback<NextActionCallback>()),
                    colorFilter = ColorFilter.tint(textColor)
                )
            }
        }

        // 2. Tab Bar
        Row(modifier = GlanceModifier.fillMaxWidth().height(40.dp).background(surfaceVariantColor)) {
            TabItem(
                label = context.getString(R.string.nav_item_starred),
                isActive = activeTab == "favorites",
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                onClick = { actionRunCallback<SwitchTabActionCallback>(actionParametersOf(SwitchTabActionCallback.tabKey to "favorites")) },
                activeColor = amber,
                inactiveColor = secondaryColor
            )
            TabItem(
                label = context.getString(R.string.nav_item_history),
                isActive = activeTab == "history",
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                onClick = { actionRunCallback<SwitchTabActionCallback>(actionParametersOf(SwitchTabActionCallback.tabKey to "history")) },
                activeColor = amber,
                inactiveColor = secondaryColor
            )
        }

        // 3. Body (List)
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(stations) { station ->
                StationRow(context, station, station.StationUuid == playingUuid && isPlaying, textColor, secondaryColor, amber)
            }
            if (stations.isEmpty()) {
                item {
                    Box(modifier = GlanceModifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Empty", style = TextStyle(color = secondaryColor, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

@Composable
fun TabItem(label: String, isActive: Boolean, modifier: GlanceModifier, onClick: () -> androidx.glance.action.Action, activeColor: ColorProvider, inactiveColor: ColorProvider) {
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
fun StationRow(context: Context, station: DataRadioStation, isActive: Boolean, textColor: ColorProvider, secondaryColor: ColorProvider, amber: ColorProvider) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(actionRunCallback<PlayStationActionCallback>(actionParametersOf(PlayStationActionCallback.stationUuidKey to station.StationUuid))),
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
