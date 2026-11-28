package com.ounben.amaradio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R

/**
 * Coordinates the permanently visible bottom-right "Scroll to top" and "Scroll to bottom"
 * buttons with whichever scrollable list, grid, or column is currently active on screen.
 */
@Stable
class ScrollNavigationController {
    private var activeOwner: Any? = null
    var extraBottomOffset by mutableStateOf(0.dp)
        private set
    private var scrollToTopAction: (suspend () -> Unit)? = null
    private var scrollToBottomAction: (suspend () -> Unit)? = null

    fun register(
        owner: Any,
        extraBottomOffset: Dp = 0.dp,
        onScrollToTop: suspend () -> Unit,
        onScrollToBottom: suspend () -> Unit,
    ) {
        activeOwner = owner
        if (this.extraBottomOffset != extraBottomOffset) {
            this.extraBottomOffset = extraBottomOffset
        }
        scrollToTopAction = onScrollToTop
        scrollToBottomAction = onScrollToBottom
    }

    fun unregister(owner: Any) {
        if (activeOwner === owner) {
            activeOwner = null
            extraBottomOffset = 0.dp
            scrollToTopAction = null
            scrollToBottomAction = null
        }
    }

    suspend fun scrollToTop() {
        scrollToTopAction?.invoke()
    }

    suspend fun scrollToBottom() {
        scrollToBottomAction?.invoke()
    }
}

val LocalScrollNavigationController = compositionLocalOf<ScrollNavigationController?> { null }

/**
 * Allows HorizontalPager containers to mark only the currently selected page as active
 * so off-screen / settling pages cannot override the scroll target.
 */
val LocalScrollNavigationActive = compositionLocalOf { true }

@Composable
fun ScrollNavigationButtons(
    onScrollToTop: () -> Unit,
    onScrollToBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScrollEdgeButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.action_scroll_to_top),
            onClick = onScrollToTop,
        )
        ScrollEdgeButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.action_scroll_to_bottom),
            onClick = onScrollToBottom,
        )
    }
}

@Composable
private fun ScrollEdgeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = AmaradioAmber,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = AmaradioAmber,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun BindScrollNavigation(
    listState: LazyListState,
    firstItemFocusRequester: FocusRequester,
    enabled: Boolean = true,
    extraBottomOffset: Dp = 0.dp,
) {
    val controller = LocalScrollNavigationController.current ?: return
    val isActive = LocalScrollNavigationActive.current && enabled
    val owner = remember { Any() }

    DisposableEffect(controller, isActive, owner) {
        if (!isActive) {
            controller.unregister(owner)
        }
        onDispose {
            controller.unregister(owner)
        }
    }

    LaunchedEffect(controller, isActive, owner, extraBottomOffset, listState, firstItemFocusRequester) {
        if (isActive) {
            controller.register(
                owner = owner,
                extraBottomOffset = extraBottomOffset,
                onScrollToTop = {
                    listState.scrollToItem(0)
                    withFrameNanos { }
                    runCatching { firstItemFocusRequester.requestFocus() }
                },
                onScrollToBottom = {
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) {
                        listState.scrollToItem(lastIndex)
                    }
                },
            )
        }
    }
}

@Composable
fun BindScrollNavigation(
    gridState: LazyGridState,
    firstItemFocusRequester: FocusRequester,
    enabled: Boolean = true,
    extraBottomOffset: Dp = 0.dp,
) {
    val controller = LocalScrollNavigationController.current ?: return
    val isActive = LocalScrollNavigationActive.current && enabled
    val owner = remember { Any() }

    DisposableEffect(controller, isActive, owner) {
        if (!isActive) {
            controller.unregister(owner)
        }
        onDispose {
            controller.unregister(owner)
        }
    }

    LaunchedEffect(controller, isActive, owner, extraBottomOffset, gridState, firstItemFocusRequester) {
        if (isActive) {
            controller.register(
                owner = owner,
                extraBottomOffset = extraBottomOffset,
                onScrollToTop = {
                    gridState.scrollToItem(0)
                    withFrameNanos { }
                    runCatching { firstItemFocusRequester.requestFocus() }
                },
                onScrollToBottom = {
                    val lastIndex = gridState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) {
                        gridState.scrollToItem(lastIndex)
                    }
                },
            )
        }
    }
}

@Composable
fun BindScrollNavigation(
    scrollState: ScrollState,
    enabled: Boolean = true,
    extraBottomOffset: Dp = 0.dp,
) {
    val controller = LocalScrollNavigationController.current ?: return
    val isActive = LocalScrollNavigationActive.current && enabled
    val owner = remember { Any() }

    DisposableEffect(controller, isActive, owner) {
        if (!isActive) {
            controller.unregister(owner)
        }
        onDispose {
            controller.unregister(owner)
        }
    }

    LaunchedEffect(controller, isActive, owner, extraBottomOffset, scrollState) {
        if (isActive) {
            controller.register(
                owner = owner,
                extraBottomOffset = extraBottomOffset,
                onScrollToTop = {
                    scrollState.scrollTo(0)
                },
                onScrollToBottom = {
                    scrollState.scrollTo(scrollState.maxValue)
                },
            )
        }
    }
}
