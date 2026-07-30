package com.ounben.amaradio.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.action.actionStartActivity
import androidx.datastore.preferences.core.Preferences
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.R

class AMARadioSmallWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("AMARadioWidget", "Small Widget provideGlance triggered")
        provideContent {
            val prefs = currentState<Preferences>()
            val name = (prefs[WidgetState.stationNameKey] ?: context.getString(R.string.app_name)).toString()
            val details = (prefs[WidgetState.stationDetailsKey] ?: "").toString()
            val uuid = (prefs[WidgetState.stationUuidKey] ?: "").toString()
            val iconUrl = (prefs[WidgetState.stationIconUrlKey] ?: "").toString()
            val isPlaying = prefs[WidgetState.isPlayingKey] ?: false
            
            // Observe the counter to ensure Glance recomposes
            val counter = prefs[WidgetState.updateCounterKey] ?: 0

            androidx.glance.GlanceTheme(colors = WidgetTheme.colors) {
                SmallWidgetContent(context, name, details, uuid, iconUrl, isPlaying)
            }
        }
    }
}

@Composable
fun SmallWidgetContent(
    context: Context,
    name: String,
    details: String,
    uuid: String,
    iconUrl: String,
    isPlaying: Boolean
) {
    val backgroundColor = ColorProvider(R.color.widget_bg)
    val textColor = ColorProvider(R.color.widget_on_bg)
    val secondaryColor = ColorProvider(R.color.widget_on_bg_secondary)
    val amber = ColorProvider(WidgetTheme.Amber)

    // Vertical Layout
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .clickable(actionStartActivity<ActivityMain>())
            ) {
                Image(
                    provider = WidgetImageLoader.getStationImage(context, uuid, name, iconUrl),
                    contentDescription = name,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity<ActivityMain>())
            ) {
                Text(
                    text = name,
                    style = TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = TextStyle(color = secondaryColor, fontSize = 10.sp),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_skip_previous_24dp),
                contentDescription = "Previous",
                modifier = GlanceModifier.size(32.dp).padding(4.dp).clickable(
                    actionSendBroadcast(Intent(context, AMARadioWidgetActionReceiver::class.java).apply {
                        action = AMARadioWidgetActionReceiver.ACTION_SKIP_PREV
                    })
                ),
                colorFilter = ColorFilter.tint(textColor)
            )
            val playIcon = if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp
            Image(
                provider = ImageProvider(playIcon),
                contentDescription = "Play/Pause",
                modifier = GlanceModifier.size(36.dp).padding(4.dp).clickable(
                    actionSendBroadcast(Intent(context, AMARadioWidgetActionReceiver::class.java).apply {
                        action = AMARadioWidgetActionReceiver.ACTION_TOGGLE_PLAY_PAUSE
                    })
                ),
                colorFilter = ColorFilter.tint(amber)
            )
            Image(
                provider = ImageProvider(R.drawable.ic_skip_next_24dp),
                contentDescription = "Next",
                modifier = GlanceModifier.size(32.dp).padding(4.dp).clickable(
                    actionSendBroadcast(Intent(context, AMARadioWidgetActionReceiver::class.java).apply {
                        action = AMARadioWidgetActionReceiver.ACTION_SKIP_NEXT
                    })
                ),
                colorFilter = ColorFilter.tint(textColor)
            )
        }
    }
}
