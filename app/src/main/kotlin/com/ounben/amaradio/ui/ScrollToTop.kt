package com.ounben.amaradio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R

/**
 * Coordinates the bottom-right "Scroll to top" button with whichever scrollable list/grid
 * is currently active on screen. Keeps the button alive while the scroll-to-top
 * animation and TalkBack focus transfer complete so accessibility focus is never dropped.
 */
@Stable
class ScrollToTopController {
    private var activeOwner: Any? = null
    private var listCanScrollToTop by mutableStateOf(false)
    private var isScrollingToTop by mutableStateOf(false)
    var extraBottomOffset by mutableStateOf(0.dp)
        private set
    private var scrollAction: (suspend () -> Unit)? = null

    val showScrollToTop: Boolean
        get() = listCanScrollToTop || isScrollingToTop

    fun register(
        owner: Any,
        canScrollToTop: Boolean,
        extraBottomOffset: Dp = 0.dp,
        onScrollToTop: suspend () -> Unit,
    ) {
        activeOwner = owner
        if (listCanScrollToTop != canScrollToTop) {
            listCanScrollToTop = canScrollToTop
        }
        if (this.extraBottomOffset != extraBottomOffset) {
            this.extraBottomOffset = extraBottomOffset
        }
        scrollAction = onScrollToTop
    }

    fun unregister(owner: Any) {
        if (activeOwner === owner) {
            activeOwner = null
            listCanScrollToTop = false
            isScrollingToTop = false
            extraBottomOffset = 0.dp
            scrollAction = null
        }
    }

    suspend fun scrollToTop() {
        val action = scrollAction ?: return
        isScrollingToTop = true
        try {
            action()
        } finally {
            isScrollingToTop = false
        }
    }
}

val LocalScrollToTopController = compositionLocalOf<ScrollToTopController?> { null }

/**
 * Allows HorizontalPager containers to mark only the currently selected page as active
 * so off-screen / settling pages cannot override the scroll-to-top state.
 */
val LocalScrollToTopActive = compositionLocalOf { true }

@Composable
fun ScrollToTopButton(
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
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.action_scroll_to_top),
                tint = AmaradioAmber,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun BindScrollToTop(
    listState: LazyListState,
    firstItemFocusRequester: FocusRequester,
    enabled: Boolean = true,
    extraBottomOffset: Dp = 0.dp,
) {
    val controller = LocalScrollToTopController.current ?: return
    val isActive = LocalScrollToTopActive.current && enabled
    val owner = remember { Any() }
    val canScroll by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 3 }
    }

    DisposableEffect(controller, isActive, owner) {
        if (!isActive) {
            controller.unregister(owner)
        }
        onDispose {
            controller.unregister(owner)
        }
    }

    LaunchedEffect(controller, isActive, owner, canScroll, extraBottomOffset, listState, firstItemFocusRequester) {
        if (isActive) {
            controller.register(
                owner = owner,
                canScrollToTop = canScroll,
                extraBottomOffset = extraBottomOffset,
            ) {
                listState.animateScrollToItem(0)
                withFrameNanos { }
                runCatching { firstItemFocusRequester.requestFocus() }
                withFrameNanos { }
            }
        }
    }
}

@Composable
fun BindScrollToTop(
    gridState: LazyGridState,
    firstItemFocusRequester: FocusRequester,
    enabled: Boolean = true,
    extraBottomOffset: Dp = 0.dp,
) {
    val controller = LocalScrollToTopController.current ?: return
    val isActive = LocalScrollToTopActive.current && enabled
    val owner = remember { Any() }
    val canScroll by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex > 3 }
    }

    DisposableEffect(controller, isActive, owner) {
        if (!isActive) {
            controller.unregister(owner)
        }
        onDispose {
            controller.unregister(owner)
        }
    }

    LaunchedEffect(controller, isActive, owner, canScroll, extraBottomOffset, gridState, firstItemFocusRequester) {
        if (isActive) {
            controller.register(
                owner = owner,
                canScrollToTop = canScroll,
                extraBottomOffset = extraBottomOffset,
            ) {
                gridState.animateScrollToItem(0)
                withFrameNanos { }
                runCatching { firstItemFocusRequester.requestFocus() }
                withFrameNanos { }
            }
        }
    }
}
