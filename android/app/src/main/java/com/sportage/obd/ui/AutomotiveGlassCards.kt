package com.sportage.obd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText

/**
 * Modern technical glassmorphism card.
 *
 * Smoked acrylic surface (#12141A at 88% opacity) with a 1dp top-to-bottom
 * white-to-transparent highlight stroke.
 */
@Composable
fun TechnicalGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color(0xFF12141A).copy(alpha = 0.88f),
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
            )
        ),
        modifier = modifier.testTag("technical_glass_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

@Composable
fun AutomotiveGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = TechnicalGlassCard(modifier = modifier, content = content)

/**
 * Torque converter lockup indicator.
 *
 * When cruising in 5th or 6th gear and engine slip RPM < 50 RPM, render a glowing
 * mint-green [T-LOCK] pill beside the gear indicator.
 */
fun isTorqueConverterLocked(transmissionGear: Int?, slipRpm: Int?): Boolean {
    val gear = transmissionGear ?: 0
    return gear in 5..6 && (slipRpm ?: Int.MAX_VALUE) < 50
}

@Composable
fun TorqueConverterLockupPill(
    transmissionGear: Int?,
    slipRpm: Int?,
    modifier: Modifier = Modifier
) {
    if (!isTorqueConverterLocked(transmissionGear, slipRpm)) return
    Surface(
        color = Color(0xFF00E676).copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF00E676)),
        modifier = modifier.testTag("t_lock_badge")
    ) {
        Text(
            text = "[T-LOCK]",
            color = Color(0xFF00E676),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TLockBadge(
    transmissionGear: Int?,
    slipRpm: Int?,
    modifier: Modifier = Modifier
) = TorqueConverterLockupPill(transmissionGear, slipRpm, modifier)
@Composable
fun AutomotiveGlassCards(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = TechnicalGlassCard(modifier = modifier, content = content)
