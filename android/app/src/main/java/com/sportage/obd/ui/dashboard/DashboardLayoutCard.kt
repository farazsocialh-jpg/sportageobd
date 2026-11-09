package com.sportage.obd.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.DarkBackground
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText

/**
 * Settings entry for whole-screen layouts. Tapping a card switches the home screen
 * immediately and persists the choice; no separate Apply step, unlike color themes.
 */
@Composable
fun DashboardLayoutCard(
    selectedId: String,
    onSelect: (DashboardLayoutTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = DashboardLayoutTheme.fromId(selectedId)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("dashboard_layout_card"),
        color = DarkBackground(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ElectricCyan().copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    "DASHBOARD LAYOUT",
                    color = ElectricCyan(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Active: ${selected.title}",
                    color = PrimaryText(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("dashboard_layout_active_title")
                )
                Text(
                    "Swaps the whole home screen, not just the colors. Live speed, RPM, clock, media and Bluetooth status feed every layout.",
                    color = SecondaryText(),
                    fontSize = 11.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardLayoutTheme.all.forEach { theme ->
                    LayoutPreviewCard(
                        theme = theme,
                        selected = theme.id == selected.id,
                        onSelect = { onSelect(theme) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutPreviewCard(
    theme: DashboardLayoutTheme,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .testTag("dashboard_layout_${theme.id}")
            .width(176.dp)
            .clip(shape)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ElectricCyan() else DarkSurfaceElevated()),
                shape
            )
            .background(DarkSurface())
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LayoutPreview(theme)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                theme.title,
                color = if (selected) ElectricCyan() else PrimaryText(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text("ACTIVE", color = ElectricCyan(), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            theme.description,
            color = SecondaryText(),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Small wireframe of each layout; same drawing code for every card, no screenshots. */
@Composable
private fun LayoutPreview(theme: DashboardLayoutTheme) {
    val surface = DarkSurfaceElevated()
    val accent = ElectricCyan()
    val background = DarkBackground()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(background, DarkSurface())))
            .padding(7.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 4.dp.toPx()
            val w = size.width
            val h = size.height
            val cardRadius = CornerRadius(3.dp.toPx())
            fun card(left: Float, top: Float, width: Float, height: Float, color: Color = surface) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(width.coerceAtLeast(0f), height.coerceAtLeast(0f)),
                    cornerRadius = cardRadius
                )
            }

            when (theme.id) {
                DashboardLayoutTheme.Classic.id -> {
                    val mapWidth = w * 0.60f
                    card(0f, 0f, mapWidth, h, accent.copy(alpha = 0.22f))
                    card(mapWidth + gap, 0f, w - mapWidth - gap, h * 0.55f)
                    card(mapWidth + gap, h * 0.55f + gap, w - mapWidth - gap, h * 0.45f - gap)
                }
                DashboardLayoutTheme.CircularGauge.id -> {
                    val dial = h
                    drawCircle(accent.copy(alpha = 0.20f), radius = dial / 2f, center = Offset(dial / 2f, h / 2f))
                    drawCircle(accent, radius = dial * 0.34f, center = Offset(dial / 2f, h / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    card(dial + gap, h * 0.16f, w - dial * 2f - gap * 2f, h * 0.68f)
                    drawCircle(accent.copy(alpha = 0.20f), radius = dial / 2f, center = Offset(w - dial / 2f, h / 2f))
                    drawCircle(accent, radius = dial * 0.34f, center = Offset(w - dial / 2f, h / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    card(0f, h - 4.dp.toPx(), w, 4.dp.toPx(), accent.copy(alpha = 0.55f))
                }
                else -> {
                    val columnWidth = (w - gap) / 2f
                    card(0f, 0f, columnWidth, h * 0.5f - gap / 2f, accent.copy(alpha = 0.22f))
                    card(0f, h * 0.5f + gap / 2f, columnWidth, h * 0.5f - gap / 2f)
                    card(columnWidth + gap, 0f, columnWidth, h * 0.42f)
                    card(columnWidth + gap, h * 0.42f + gap, columnWidth, h * 0.58f - gap, accent.copy(alpha = 0.22f))
                    card(0f, h - 4.dp.toPx(), w, 4.dp.toPx(), accent.copy(alpha = 0.55f))
                }
            }
        }
    }
}
