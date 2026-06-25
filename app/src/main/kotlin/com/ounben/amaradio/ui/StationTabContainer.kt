package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun TabHeader(titleRes: Int) {
    ScrollableTabRow(
        selectedTabIndex = 0,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        indicator = { tabPositions ->
            if (tabPositions.isNotEmpty()) {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[0]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        divider = {}
    ) {
        Tab(
            selected = true,
            onClick = { },
            text = { Text(text = stringResource(id = titleRes)) }
        )
    }
}

@Composable
fun SingleTabContainer(
    titleRes: Int,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabHeader(titleRes = titleRes)
        content()
    }
}
