package com.ounben.amaradio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
 * Coordinates the permanently visible bottom-right "Scroll to top" button with
 * whichever scrollable list, grid, or column is currently active on screen.
 */
@Stable
class ScrollToTopController {
    private var activeOwner: Any? = null
    var extraBottomOffset by mutableStateOf(0.dp)
        private set
    private var scrollAction: (suspend () -> Unit)? = null

    fun register(
        owner: Any,
        extraBottomOffset: Dp = 0.dp,
        onScrollToTop: suspend () -> Unit,
    ) {
        activeOwner = owner
        if (this.extraBottomOffset != extraBottomOffset) {
            this.extraBottomOffset = extraBottomOffset
        }
        scrollAction = onScrollToTop
    }

    fun unregister(owner: Any) {
        if (activeOwner === owner) {
            activeOwner = null
            extraBottomOffset = 0.dp
            scrollAction = null
        }
    }

    suspend fun scrollToTop() {
        scrollAction?.invoke()
    }
}

val LocalScrollToTopController = compositionLocalOf<ScrollToTopController?> { null }

/**
 * Allows HorizontalPager containers to mark only the currently selected page as active
 * so off-screen / settling pages cannot override the scroll-to-top target.
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
            ) {
                listState.animateScrollToItem(0)
                withFrameNanos { }
                runCatching { firstItemFocusRequester.requestFocus() }
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
            ) {
                gridState.animateScrollToItem(0)
                withFrameNanos { }
                runCatching { firstItemFocusRequester.requestFocus() }
            }
        }
    }
}

@Composable
fun BindScrollToTop(
    scrollState: ScrollState,
    enabled: Boolean = true,
    extraBottomOffset: Dp = 0.dp,
) {
    val controller = LocalScrollToTopController.current ?: return
    val isActive = LocalScrollToTopActive.current && enabled
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
            ) {
                scrollState.animateScrollTo(0)
            }
        }
    }
}
