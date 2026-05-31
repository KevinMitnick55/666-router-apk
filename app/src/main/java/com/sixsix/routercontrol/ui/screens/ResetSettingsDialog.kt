package com.sixsix.routercontrol.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sixsix.routercontrol.ui.theme.*

@Composable
fun ResetSettingsDialog(
    currentMode: String,
    currentDay: Int,
    onDismiss: () -> Unit,
    onSave: (mode: String, day: Int) -> Unit
) {
    var mode by remember { mutableStateOf(currentMode) }
    var day by remember { mutableStateOf(currentDay) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Counter reset", color = TextMain) },
        text = {
            Column {
                Text("Choose how the data counter resets.", color = Text2, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))

                ModeRow("Manual only", "Resets when you tap the reset button",
                    selected = mode == "manual") { mode = "manual" }
                Spacer(Modifier.height(8.dp))
                ModeRow("Monthly", "Auto-resets on a day each month",
                    selected = mode == "monthly") { mode = "monthly" }

                if (mode == "monthly") {
                    Spacer(Modifier.height(16.dp))
                    Text("Reset on day: $day", color = TextMain, fontSize = 14.sp)
                    Slider(
                        value = day.toFloat(), onValueChange = { day = it.toInt() },
                        valueRange = 1f..28f, steps = 26,
                        colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(mode, day) }) { Text("Save", color = Gold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Text2) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Gold.copy(0.12f) else Surface2,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = Gold, unselectedColor = Text3))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, color = TextMain, fontSize = 15.sp)
                Text(subtitle, color = Text3, fontSize = 12.sp)
            }
        }
    }
}
