package com.sixsix.routercontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sixsix.routercontrol.data.Device
import com.sixsix.routercontrol.data.UsageTotals
import com.sixsix.routercontrol.ui.humanBytes
import com.sixsix.routercontrol.ui.humanDuration
import com.sixsix.routercontrol.ui.theme.*

@Composable
fun DashboardScreen(
    devices: List<Device>,
    usage: UsageTotals?,
    busyMac: String?,
    onToggle: (Device) -> Unit,
    onRefresh: () -> Unit,
    onResetCounter: () -> Unit,
    onOpenResetSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { UsageCard(usage, onResetCounter, onOpenResetSettings) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Devices", style = MaterialTheme.typography.titleLarge, color = TextMain)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${devices.size} online", fontFamily = FontFamily.Monospace,
                        color = Text2, fontSize = 13.sp)
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "refresh", tint = Gold)
                    }
                }
            }
        }
        items(devices, key = { it.mac }) { d ->
            DeviceCard(d, busy = busyMac == d.mac, onToggle = { onToggle(d) })
        }
        if (devices.isEmpty()) {
            item {
                Text("No devices yet — pull refresh.", color = Text3,
                    modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
private fun UsageCard(usage: UsageTotals?, onReset: () -> Unit, onSettings: () -> Unit) {
    Surface(
        color = Surface1, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DATA USED", fontFamily = FontFamily.Monospace, color = Text2,
                    fontSize = 12.sp, letterSpacing = 2.sp)
                Row {
                    IconButton(onClick = onSettings, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Schedule, "reset schedule", tint = Text3,
                            modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.RestartAlt, "reset counter", tint = Text3,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                humanBytes(usage?.networkBytes ?: 0),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 40.sp, color = Gold
            )
            Text(
                "network total" + (usage?.let { " · ${it.days} day(s) tracked" } ?: ""),
                color = Text3, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DeviceCard(d: Device, busy: Boolean, onToggle: () -> Unit) {
    Surface(color = Surface1, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (d.isBlocked) Danger.copy(0.15f) else Surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (d.isBlocked) Icons.Default.Block else Icons.Default.Wifi,
                    null, tint = if (d.isBlocked) Danger else Gold,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${d.ip}  ·  ${humanDuration(d.linkSecs)}",
                    color = Text2, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
                Row(Modifier.padding(top = 3.dp)) {
                    Text("↓ ${humanBytes(d.rxBytes)}", color = Success,
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("↑ ${humanBytes(d.txBytes)}", color = GoldBright,
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(26.dp), color = Gold, strokeWidth = 2.dp)
            } else {
                // Toggle: ON = allowed (internet), OFF = blocked
                Switch(
                    checked = !d.isBlocked,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink, checkedTrackColor = Gold,
                        uncheckedThumbColor = Color.White, uncheckedTrackColor = Danger
                    )
                )
            }
        }
    }
}
