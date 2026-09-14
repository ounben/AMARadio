package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.cast.MediaRouteButton

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    MediaRouteButton(
        modifier = modifier.size(48.dp)
    )
}
