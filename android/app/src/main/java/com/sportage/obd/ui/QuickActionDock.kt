package com.sportage.obd.ui

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText

/**
 * Automotive Quick Action Dock for Kia Sportage Cockpit.
 *
 * Implements strict automotive safety guidelines:
 * - 64dp minimum touch targets for all action buttons for reliable in-drive operation.
 * - Clean media, phone, and app launcher access tailored for in-dash head units.
 */
@Composable
fun QuickActionDock(
    onMusic: () -> Unit,
    onMediaKey: (Int) -> Unit,
    onPhone: () -> Unit,
    onApps: () -> Unit,
    onBluetooth: () -> Unit,
    logging: Boolean,
    canLog: Boolean,
    onLogging: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Surface(
        color = DarkSurface(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 620.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Music App Launcher
                DockAction(
                    icon = Icons.Default.Headphones,
                    description = "Open music app",
                    action = onMusic
                )

                // 2. Media Controls
                if (wide) {
                    DockAction(
                        icon = Icons.Default.SkipPrevious,
                        description = "Previous track",
                        action = { onMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                    )
                }

                DockAction(
                    icon = Icons.Default.PlayCircle,
                    description = "Play or pause media",
                    action = { onMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                )

                if (wide) {
                    DockAction(
                        icon = Icons.Default.SkipNext,
                        description = "Next track",
                        action = { onMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
                    )
                }

                // 3. Phone Dialer
                DockAction(
                    icon = Icons.Default.Phone,
                    description = "Open phone dialer",
                    action = onPhone
                )

                // 4. Installed Apps Drawer
                DockAction(
                    icon = Icons.Default.Apps,
                    description = "Open installed apps",
                    action = onApps
                )

                // 5. Quick Actions Overflow
                Box(contentAlignment = Alignment.Center) {
                    DockAction(
                        icon = Icons.Default.MoreVert,
                        description = "Quick actions",
                        action = { menu = true }
                    )
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false }
                    ) {
                        if (!wide) {
                            DropdownMenuItem(
                                text = { Text("Previous track") },
                                onClick = {
                                    menu = false
                                    onMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Next track") },
                                onClick = {
                                    menu = false
                                    onMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("OBD Bluetooth") },
                            onClick = {
                                menu = false
                                onBluetooth()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (logging) "Stop OBD recording" else "Record OBD to CSV") },
                            enabled = canLog || logging,
                            onClick = {
                                menu = false
                                onLogging()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard cockpit dock button maintaining strict 64dp touch target for safe driver interaction.
 */
@Composable
private fun DockAction(
    icon: ImageVector,
    description: String,
    action: () -> Unit,
    active: Boolean = false,
    activeTint: Color = ElectricCyan()
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (active) activeTint.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = action
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) activeTint else SecondaryText(),
            modifier = Modifier.size(26.dp)
        )
    }
}

internal fun openPhoneDialer(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "No phone dialer is installed on this device.", Toast.LENGTH_LONG).show()
    }
}
