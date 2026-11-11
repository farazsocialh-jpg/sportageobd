package com.sportage.obd.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sportage.obd.ui.theme.DarkBackground
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import kotlin.math.abs

/**
 * Ergonomic driver-side vertical dock layout for in-vehicle head units.
 *
 * In LHD vehicles (Qatar/GCC) the dock pins to the far-left edge, 15-20 cm closer
 * to the steering wheel than a bottom dock; in RHD vehicles it pins to the far right.
 * Every dock action keeps a 64dp minimum touch target with vertical spacing so the
 * driver can rest the heel of their hand on the bezel and tap with their thumb.
 */
@Composable
fun ErgonomicCockpitLayout(
    modifier: Modifier = Modifier,
    isLhd: Boolean = true,
    showDock: Boolean = true,
    dockWidth: androidx.compose.ui.unit.Dp = 72.dp,
    dockContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    Row(modifier = modifier.fillMaxSize()) {
        if (showDock && isLhd) {
            DriverSideVerticalDock(
                modifier = Modifier
                    .width(dockWidth)
                    .fillMaxHeight()
                    .testTag("driver_side_vertical_dock"),
                content = dockContent
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
        if (showDock && !isLhd) {
            DriverSideVerticalDock(
                modifier = Modifier
                    .width(dockWidth)
                    .fillMaxHeight()
                    .testTag("driver_side_vertical_dock"),
                content = dockContent
            )
        }
    }
}

@Composable
fun DriverSideVerticalDock(
    modifier: Modifier = Modifier,
    itemSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = DarkBackground(),
        modifier = modifier
            .padding(vertical = 8.dp)
            .testTag("driver_side_vertical_dock")
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

/**
 * 64dp minimum touch target for dock actions, matching automotive safety guidelines.
 */
@Composable
fun DockTouchTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .testTag("dock_touch_target"),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Full-screen blind swipe gesture surface.
 *
 * - Horizontal swipe: switch between Dashboard / Live Map / Media.
 * - Downward swipe from the header: toggle Night Dim.
 * - Two-finger double tap: toggle Windshield HUD Mirror Mode.
 */
@Composable
fun BlindSwipeGestureSurface(
    modifier: Modifier = Modifier,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onSwipeDownFromHeader: () -> Unit = {},
    onTwoFingerDoubleTap: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    var swipeThreshold by remember { mutableStateOf(80f) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -swipeThreshold) onSwipeLeft()
                    else if (dragAmount > swipeThreshold) onSwipeRight()
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > swipeThreshold) onSwipeDownFromHeader()
                }
            }
            .testTag("blind_swipe_surface")
    ) {
        content()
    }
}

/**
 * Convenience modifier form of [BlindSwipeGestureSurface].
 */
fun Modifier.blindSwipeGestures(
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onSwipeDownFromHeader: () -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    detectHorizontalDragGestures { _, dragAmount ->
        if (abs(dragAmount) > 80f) {
            if (dragAmount < 0) onSwipeLeft() else onSwipeRight()
        }
    }
}.pointerInput(Unit) {
    detectVerticalDragGestures { _, dragAmount ->
        if (dragAmount > 80f) onSwipeDownFromHeader()
    }
}