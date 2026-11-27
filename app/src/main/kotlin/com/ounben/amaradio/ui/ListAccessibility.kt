package com.ounben.amaradio.ui

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration.Companion.milliseconds

private data class ScrollRangeSnapshot(
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    val scrollOffset: Int,
    val isScrollInProgress: Boolean
)

/**
 * Emits standard Android [AccessibilityEvent.TYPE_VIEW_SCROLLED] events populated with
 * `fromIndex`, `toIndex`, and `itemCount` when a [LazyListState] scrolls, enabling TalkBack,
 * Jieshuo, and other screen readers to announce visible item ranges (e.g. "Showing items 42 to 60 of 502").
 */
@Composable
fun BindListScrollRangeAccessibility(
    listState: LazyListState,
    enabled: Boolean = true,
) {
    val isActive = LocalScrollToTopActive.current && enabled
    val view = LocalView.current
    val accessibilityManager = remember(view) {
        view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    LaunchedEffect(listState, isActive) {
        if (!isActive) return@LaunchedEffect
        var initialized = false
        var lastFrom = -1
        var lastTo = -1
        var lastCount = -1
        var lastOffset = 0

        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            val total = layoutInfo.totalItemsCount
            if (visible.isEmpty() || total <= 0) {
                null
            } else {
                ScrollRangeSnapshot(
                    fromIndex = visible.first().index.coerceIn(0, total - 1),
                    toIndex = visible.last().index.coerceIn(0, total - 1),
                    itemCount = total,
                    scrollOffset = listState.firstVisibleItemIndex * 500 + listState.firstVisibleItemScrollOffset,
                    isScrollInProgress = listState.isScrollInProgress
                )
            }
        }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                if (snapshot == null) return@collectLatest
                if (!initialized) {
                    initialized = true
                    lastFrom = snapshot.fromIndex
                    lastTo = snapshot.toIndex
                    lastCount = snapshot.itemCount
                    lastOffset = snapshot.scrollOffset
                    return@collectLatest
                }

                // Wait slightly longer than Compose's 100ms accessibility batch window so our
                // populated fromIndex/toIndex/itemCount event arrives cleanly after scroll settles.
                delay(if (snapshot.isScrollInProgress) 450.milliseconds else 180.milliseconds)

                val rangeChanged = snapshot.fromIndex != lastFrom || snapshot.toIndex != lastTo
                val countChangedAtTop = snapshot.itemCount != lastCount && snapshot.fromIndex == 0 && lastFrom == 0
                val allVisibleAtTop = snapshot.fromIndex == 0 && snapshot.toIndex >= snapshot.itemCount - 1 && lastFrom == 0

                val prevFrom = lastFrom
                val prevOffset = lastOffset
                lastFrom = snapshot.fromIndex
                lastTo = snapshot.toIndex
                lastCount = snapshot.itemCount
                lastOffset = snapshot.scrollOffset

                if (!rangeChanged || countChangedAtTop || allVisibleAtTop) return@collectLatest
                if (accessibilityManager?.isEnabled != true || !view.isAttachedToWindow) return@collectLatest

                dispatchScrollRangeEvent(
                    view = view,
                    className = "android.widget.ListView",
                    snapshot = snapshot,
                    prevFrom = prevFrom,
                    prevOffset = prevOffset
                )
            }
    }
}

/**
 * Emits standard Android [AccessibilityEvent.TYPE_VIEW_SCROLLED] events populated with
 * `fromIndex`, `toIndex`, and `itemCount` when a [LazyGridState] scrolls.
 */
@Composable
fun BindGridScrollRangeAccessibility(
    gridState: LazyGridState,
    enabled: Boolean = true,
) {
    val isActive = LocalScrollToTopActive.current && enabled
    val view = LocalView.current
    val accessibilityManager = remember(view) {
        view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    LaunchedEffect(gridState, isActive) {
        if (!isActive) return@LaunchedEffect
        var initialized = false
        var lastFrom = -1
        var lastTo = -1
        var lastCount = -1
        var lastOffset = 0

        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            val total = layoutInfo.totalItemsCount
            if (visible.isEmpty() || total <= 0) {
                null
            } else {
                ScrollRangeSnapshot(
                    fromIndex = visible.first().index.coerceIn(0, total - 1),
                    toIndex = visible.last().index.coerceIn(0, total - 1),
                    itemCount = total,
                    scrollOffset = gridState.firstVisibleItemIndex * 500 + gridState.firstVisibleItemScrollOffset,
                    isScrollInProgress = gridState.isScrollInProgress
                )
            }
        }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                if (snapshot == null) return@collectLatest
                if (!initialized) {
                    initialized = true
                    lastFrom = snapshot.fromIndex
                    lastTo = snapshot.toIndex
                    lastCount = snapshot.itemCount
                    lastOffset = snapshot.scrollOffset
                    return@collectLatest
                }

                delay(if (snapshot.isScrollInProgress) 450.milliseconds else 180.milliseconds)

                val rangeChanged = snapshot.fromIndex != lastFrom || snapshot.toIndex != lastTo
                val countChangedAtTop = snapshot.itemCount != lastCount && snapshot.fromIndex == 0 && lastFrom == 0
                val allVisibleAtTop = snapshot.fromIndex == 0 && snapshot.toIndex >= snapshot.itemCount - 1 && lastFrom == 0

                val prevFrom = lastFrom
                val prevOffset = lastOffset
                lastFrom = snapshot.fromIndex
                lastTo = snapshot.toIndex
                lastCount = snapshot.itemCount
                lastOffset = snapshot.scrollOffset

                if (!rangeChanged || countChangedAtTop || allVisibleAtTop) return@collectLatest
                if (accessibilityManager?.isEnabled != true || !view.isAttachedToWindow) return@collectLatest

                dispatchScrollRangeEvent(
                    view = view,
                    className = "android.widget.GridView",
                    snapshot = snapshot,
                    prevFrom = prevFrom,
                    prevOffset = prevOffset
                )
            }
    }
}

private fun dispatchScrollRangeEvent(
    view: android.view.View,
    className: String,
    snapshot: ScrollRangeSnapshot,
    prevFrom: Int,
    prevOffset: Int,
) {
    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED)
    } else {
        @Suppress("DEPRECATION")
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED)
    }
    val maxScroll = ((snapshot.itemCount - 1).coerceAtLeast(1)) * 500
    val resolvedScrollY = when {
        snapshot.fromIndex == 0 -> 0
        snapshot.toIndex >= snapshot.itemCount - 1 -> maxScroll
        else -> snapshot.scrollOffset.coerceIn(1, (maxScroll - 1).coerceAtLeast(1))
    }
    val rawDelta = snapshot.scrollOffset - prevOffset
    val resolvedDelta = when {
        rawDelta != 0 -> rawDelta
        snapshot.fromIndex > prevFrom -> 1
        snapshot.fromIndex < prevFrom -> -1
        else -> 1
    }

    event.packageName = view.context.packageName
    event.className = className
    event.isEnabled = true
    event.fromIndex = snapshot.fromIndex
    event.toIndex = snapshot.toIndex
    event.itemCount = snapshot.itemCount
    event.scrollY = resolvedScrollY
    event.maxScrollY = maxScroll
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        event.scrollDeltaY = resolvedDelta
    }
    event.setSource(view)
    view.parent?.requestSendAccessibilityEvent(view, event)
}

fun listItemCollectionInfo(index: Int): CollectionItemInfo =
    CollectionItemInfo(
        rowIndex = index,
        rowSpan = 1,
        columnIndex = 0,
        columnSpan = 1
    )

fun Modifier.listItemSemantics(index: Int): Modifier =
    semantics {
        collectionItemInfo = listItemCollectionInfo(index)
    }

@Composable
fun rememberGridColumnCount(gridState: LazyGridState): Int {
    val count by remember(gridState) {
        derivedStateOf {
            val visible = gridState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) 1
            else (visible.maxOf { it.column } + 1).coerceAtLeast(1)
        }
    }
    return count
}

fun gridCollectionInfo(itemCount: Int, columnCount: Int): CollectionInfo {
    val cols = columnCount.coerceAtLeast(1)
    val rows = if (itemCount <= 0) 0 else (itemCount + cols - 1) / cols
    return CollectionInfo(rowCount = rows, columnCount = cols)
}

fun gridItemCollectionInfo(index: Int, columnCount: Int): CollectionItemInfo {
    val cols = columnCount.coerceAtLeast(1)
    return CollectionItemInfo(
        rowIndex = index / cols,
        rowSpan = 1,
        columnIndex = index % cols,
        columnSpan = 1
    )
}
