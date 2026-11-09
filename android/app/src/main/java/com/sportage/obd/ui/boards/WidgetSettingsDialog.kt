package com.sportage.obd.ui.boards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.WarningAmber

/**
 * Editor for one widget instance: title/units, thresholds, decimals, grid span,
 * refresh rate, display style and visibility. Invalid input is rejected with the reason,
 * so a persisted config is always renderable.
 */
@Composable
fun WidgetSettingsDialog(
    type: BoardWidgetType,
    initial: WidgetConfig,
    onDismiss: () -> Unit,
    onSave: (WidgetConfig) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial.titleOverride.orEmpty()) }
    var unit by remember(initial) { mutableStateOf(initial.unitOverride.orEmpty()) }
    var minText by remember(initial) { mutableStateOf(initial.minValue?.toString().orEmpty()) }
    var maxText by remember(initial) { mutableStateOf(initial.maxValue?.toString().orEmpty()) }
    var warnText by remember(initial) { mutableStateOf(initial.warnThreshold?.toString().orEmpty()) }
    var criticalText by remember(initial) { mutableStateOf(initial.criticalThreshold?.toString().orEmpty()) }
    var decimals by remember(initial) { mutableStateOf(initial.decimals) }
    var span by remember(initial) { mutableStateOf(initial.span) }
    var refreshMs by remember(initial) { mutableStateOf(initial.refreshMs) }
    var visible by remember(initial) { mutableStateOf(initial.visible) }
    var style by remember(initial) { mutableStateOf(initial.style) }

    fun numberOrNull(text: String): Float? = text.trim().takeIf { it.isNotEmpty() }?.toFloatOrNull()

    val parseError = listOf(minText, maxText, warnText, criticalText).any { text ->
        text.trim().isNotEmpty() && text.trim().toFloatOrNull() == null
    }
    val candidate = WidgetConfig(
        titleOverride = title.takeIf { it.isNotBlank() },
        unitOverride = unit.takeIf { it.isNotBlank() },
        minValue = numberOrNull(minText),
        maxValue = numberOrNull(maxText),
        warnThreshold = numberOrNull(warnText),
        criticalThreshold = numberOrNull(criticalText),
        decimals = decimals,
        span = span,
        refreshMs = refreshMs,
        visible = visible,
        style = style
    ).normalized()
    val errors = candidate.validationErrors().toMutableList()
    if (parseError) errors += "Thresholds and limits must be numbers"

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("widget_settings_dialog"),
        title = { Text("Configure ${type.title}", color = PrimaryText(), style = MaterialTheme.typography.titleMedium) },
        confirmButton = {
            TextButton(
                onClick = { onSave(candidate) },
                enabled = errors.isEmpty(),
                modifier = Modifier.testTag("widget_settings_save")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(WidgetConfig.MAX_TITLE) },
                    label = { Text("Custom title") }, singleLine = true,
                    placeholder = { Text(type.title) }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unit, onValueChange = { unit = it.take(WidgetConfig.MAX_UNIT) },
                    label = { Text("Unit") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText, onValueChange = { minText = it },
                        label = { Text("Min") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxText, onValueChange = { maxText = it },
                        label = { Text("Max") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = warnText, onValueChange = { warnText = it },
                        label = { Text("Warn at") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = criticalText, onValueChange = { criticalText = it },
                        label = { Text("Critical at") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("DECIMALS", color = SecondaryText(), fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..2).forEach { option ->
                        FilterChip(
                            selected = decimals == option,
                            onClick = { decimals = option },
                            label = { Text("$option") }
                        )
                    }
                }

                Text("WIDTH", color = SecondaryText(), fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = span == 1, onClick = { span = 1 }, label = { Text("1 column") })
                    FilterChip(selected = span == 2, onClick = { span = 2 }, label = { Text("Full width") })
                }

                Text("REFRESH", color = SecondaryText(), fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetConfig.REFRESH_CHOICES.forEach { option ->
                        FilterChip(
                            selected = refreshMs == option,
                            onClick = { refreshMs = option },
                            label = { Text("${option}ms") }
                        )
                    }
                }

                Text("DISPLAY STYLE", color = SecondaryText(), fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetConfig.STYLES.forEach { option ->
                        FilterChip(
                            selected = style == option,
                            onClick = { style = option },
                            label = { Text(option) }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show on board", color = PrimaryText(), modifier = Modifier.weight(1f))
                    Switch(
                        checked = visible,
                        onCheckedChange = { visible = it },
                        modifier = Modifier.testTag("widget_visible_switch")
                    )
                }

                if (errors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        errors.forEach { message ->
                            Text(message, color = WarningAmber(), fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    "Thresholds colour the reading: warning at ${warnText.ifBlank { "—" }}, " +
                        "critical at ${criticalText.ifBlank { "—" }}.",
                    color = SecondaryText(), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    )
}
