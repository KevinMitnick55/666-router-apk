package com.sixsix.routercontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sixsix.routercontrol.ui.theme.*

private data class SsidOption(val label: String, val display: String)

private fun bandOf(label: String): String {
    val n = label.removePrefix("SSID").toIntOrNull() ?: return ""
    return if (n in 1..4) "2.4GHz" else "5GHz"
}

private val SSIDS = listOf(
    SsidOption("SSID1", "SSID1 · 2.4GHz"),
    SsidOption("SSID5", "SSID5 · 5GHz"),
    SsidOption("SSID2", "SSID2 · 2.4GHz"),
    SsidOption("SSID3", "SSID3 · 2.4GHz"),
    SsidOption("SSID4", "SSID4 · 2.4GHz"),
    SsidOption("SSID6", "SSID6 · 5GHz"),
    SsidOption("SSID7", "SSID7 · 5GHz"),
    SsidOption("SSID8", "SSID8 · 5GHz"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(
    loading: Boolean,
    ssidNames: Map<String, String>,
    onApply: (ssid: String, name: String?, pass: String?) -> Unit
) {
    // Build the option list, folding in live network names where we have them.
    val options = remember(ssidNames) {
        SSIDS.map { base ->
            val live = ssidNames[base.label]
            if (live != null) base.copy(display = "$live  ·  ${base.label} ${bandOf(base.label)}")
            else base
        }
    }
    var selected by remember(options) { mutableStateOf(options.first()) }
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("Wi-Fi settings", style = MaterialTheme.typography.headlineMedium, color = TextMain)
        Spacer(Modifier.height(6.dp))
        Text("Leave a field blank to keep it unchanged.", color = Text3, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))

        FieldLabel("Which network")
        Box {
            Surface(
                color = Surface1, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(selected.display, color = TextMain, fontSize = 15.sp)
                    Text("▾", color = Gold)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt.display) }, onClick = {
                        selected = opt; expanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("New network name (SSID)")
        DarkField(name, { name = it }, "keep current")
        Spacer(Modifier.height(18.dp))
        FieldLabel("New password")
        DarkField(pass, { pass = it }, "keep current")
        Spacer(Modifier.height(10.dp))
        Text("Password rule: 8+ chars with letters, digits & a symbol.",
            color = Text3, fontSize = 12.sp)

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { onApply(selected.label, name.ifBlank { null }, pass.ifBlank { null }) },
            enabled = !loading && (name.isNotBlank() || pass.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink,
                disabledContainerColor = Surface2, disabledContentColor = Text3)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.dp)
            else Text("Apply changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(16.dp))
        Surface(color = Warn.copy(0.10f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "Changing Wi-Fi disconnects all devices on that network. " +
                "Reconnect with the new settings afterward.",
                color = Warn, modifier = Modifier.padding(14.dp), fontSize = 13.sp
            )
        }
    }
}
