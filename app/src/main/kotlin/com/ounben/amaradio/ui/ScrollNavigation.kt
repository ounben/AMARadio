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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/**
 * Draws an always-visible AMARadio accent-colored vertical scroll indicator on the right edge
 * whenever the [LazyListState] is scrollable. Purely visual (no accessibility nodes added).
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    color: Color = AmaradioAmber,
    width: Dp = 4.dp,
    endMargin: Dp = 2.dp,
    verticalMargin: Dp = 4.dp,
    minThumbHeight: Dp = 28.dp,
): Modifier = drawWithContent {
    drawContent()
    if (!state.canScrollForward && !state.canScrollBackward) return@drawWithContent
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty()) return@drawWithContent

    val marginPx = verticalMargin.toPx()
    val trackHeight = (size.height - marginPx * 2f).coerceAtLeast(1f)
    val widthPx = width.toPx()
    val minThumbPx = minThumbHeight.toPx().coerceAtMost(trackHeight)

    val firstVisible = visibleItems.first()
    val firstItemFraction = if (firstVisible.size > 0) {
        (-firstVisible.offset).coerceAtLeast(0).toFloat() / firstVisible.size.toFloat()
    } else 0f
    val avgItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size.toFloat()
    val viewportHeight = layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
    val visibleCountApprox = if (avgItemSize > 0f) viewportHeight / avgItemSize else visibleItems.size.toFloat()
    val estimatedTotalItems = totalItems.toFloat().coerceAtLeast(visibleCountApprox)

    val thumbHeight = ((visibleCountApprox / estimatedTotalItems) * trackHeight).coerceIn(minThumbPx, trackHeight)
    val maxScrollItems = (estimatedTotalItems - visibleCountApprox).coerceAtLeast(0.001f)
    val scrollFraction = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        else -> ((firstVisible.index.toFloat() + firstItemFraction) / maxScrollItems).coerceIn(0f, 1f)
    }
    val thumbTop = marginPx + scrollFraction * (trackHeight - thumbHeight)
    val thumbLeft = (size.width - widthPx - endMargin.toPx()).coerceAtLeast(0f)

    drawRoundRect(
        color = color,
        topLeft = Offset(thumbLeft, thumbTop),
        size = Size(widthPx, thumbHeight),
        cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f),
    )
}

/**
 * Draws an always-visible AMARadio accent-colored vertical scroll indicator on the right edge
 * whenever the [LazyGridState] is scrollable. Purely visual (no accessibility nodes added).
 */
fun Modifier.verticalScrollbar(
    state: LazyGridState,
    color: Color = AmaradioAmber,
    width: Dp = 4.dp,
    endMargin: Dp = 2.dp,
    verticalMargin: Dp = 4.dp,
    minThumbHeight: Dp = 28.dp,
): Modifier = drawWithContent {
    drawContent()
    if (!state.canScrollForward && !state.canScrollBackward) return@drawWithContent
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty()) return@drawWithContent

    val marginPx = verticalMargin.toPx()
    val trackHeight = (size.height - marginPx * 2f).coerceAtLeast(1f)
    val widthPx = width.toPx()
    val minThumbPx = minThumbHeight.toPx().coerceAtMost(trackHeight)

    val columnCount = (visibleItems.maxOf { it.column } + 1).coerceAtLeast(1)
    val totalRows = ((totalItems + columnCount - 1) / columnCount).toFloat()
    val firstVisible = visibleItems.first()
    val firstRow = (firstVisible.index / columnCount).toFloat()
    val firstRowHeight = firstVisible.size.height.coerceAtLeast(1)
    val firstRowFraction = (-firstVisible.offset.y).coerceAtLeast(0).toFloat() / firstRowHeight.toFloat()
    val avgRowHeight = visibleItems.sumOf { it.size.height }.toFloat() / visibleItems.size.toFloat()
    val viewportHeight = layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
    val visibleRowsApprox = if (avgRowHeight > 0f) viewportHeight / avgRowHeight else 1f
    val estimatedTotalRows = totalRows.coerceAtLeast(visibleRowsApprox)

    val thumbHeight = ((visibleRowsApprox / estimatedTotalRows) * trackHeight).coerceIn(minThumbPx, trackHeight)
    val maxScrollRows = (estimatedTotalRows - visibleRowsApprox).coerceAtLeast(0.001f)
    val scrollFraction = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        else -> ((firstRow + firstRowFraction) / maxScrollRows).coerceIn(0f, 1f)
    }
    val thumbTop = marginPx + scrollFraction * (trackHeight - thumbHeight)
    val thumbLeft = (size.width - widthPx - endMargin.toPx()).coerceAtLeast(0f)

    drawRoundRect(
        color = color,
        topLeft = Offset(thumbLeft, thumbTop),
        size = Size(widthPx, thumbHeight),
        cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f),
    )
}

/**
 * Draws an always-visible AMARadio accent-colored vertical scroll indicator on the right edge
 * whenever the [ScrollState] is scrollable. Must be placed BEFORE [Modifier.verticalScroll].
 */
fun Modifier.verticalScrollbar(
    state: ScrollState,
    color: Color = AmaradioAmber,
    width: Dp = 4.dp,
    endMargin: Dp = 2.dp,
    verticalMargin: Dp = 4.dp,
    minThumbHeight: Dp = 28.dp,
): Modifier = drawWithContent {
    drawContent()
    val maxValue = state.maxValue
    if (maxValue <= 0 || maxValue == Int.MAX_VALUE) return@drawWithContent

    val marginPx = verticalMargin.toPx()
    val trackHeight = (size.height - marginPx * 2f).coerceAtLeast(1f)
    val widthPx = width.toPx()
    val minThumbPx = minThumbHeight.toPx().coerceAtMost(trackHeight)

    val viewportHeight = size.height.coerceAtLeast(1f)
    val totalContentHeight = viewportHeight + maxValue.toFloat()
    val thumbHeight = ((viewportHeight / totalContentHeight) * trackHeight).coerceIn(minThumbPx, trackHeight)
    val scrollFraction = (state.value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    val thumbTop = marginPx + scrollFraction * (trackHeight - thumbHeight)
    val thumbLeft = (size.width - widthPx - endMargin.toPx()).coerceAtLeast(0f)

    drawRoundRect(
        color = color,
        topLeft = Offset(thumbLeft, thumbTop),
        size = Size(widthPx, thumbHeight),
        cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f),
    )
}
