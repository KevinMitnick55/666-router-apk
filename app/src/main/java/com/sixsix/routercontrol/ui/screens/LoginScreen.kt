package com.sixsix.routercontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sixsix.routercontrol.data.AppStore
import com.sixsix.routercontrol.ui.theme.*

@Composable
fun LoginScreen(
    saved: AppStore.SavedLogin?,
    loading: Boolean,
    error: String?,
    onLogin: (String, String, String, Boolean) -> Unit
) {
    var host by remember(saved) { mutableStateOf(saved?.host ?: "http://192.168.1.1") }
    var user by remember(saved) { mutableStateOf(saved?.user ?: "user") }
    var pass by remember(saved) { mutableStateOf(saved?.pass ?: "") }
    var remember_ by remember(saved) { mutableStateOf(saved?.remember ?: true) }
    var show by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Ink).padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("666", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 56.sp, color = Gold)
        Text("ROUTER CONTROL", fontFamily = FontFamily.Monospace,
            color = Text2, fontSize = 13.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(40.dp))

        FieldLabel("Router address")
        DarkField(host, { host = it }, "http://192.168.1.1")
        Spacer(Modifier.height(16.dp))
        FieldLabel("Username")
        DarkField(user, { user = it }, "user")
        Spacer(Modifier.height(16.dp))
        FieldLabel("Password")
        DarkField(
            pass, { pass = it }, "••••••••",
            visual = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = Text3)
                }
            }
        )
        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = remember_, onCheckedChange = { remember_ = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink, checkedTrackColor = Gold,
                    uncheckedThumbColor = Text3, uncheckedTrackColor = Surface2))
            Spacer(Modifier.width(12.dp))
            Text("Remember me", color = TextMain, fontSize = 15.sp)
        }
        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { onLogin(host, user, pass, remember_) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.dp)
            else Text("Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Surface(color = Danger.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                Text(it, color = Danger, modifier = Modifier.padding(14.dp), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, color = Text2,
        fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun DarkField(
    value: String, onChange: (String) -> Unit, placeholder: String,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(placeholder, color = Text3) },
        singleLine = true,
        visualTransformation = visual,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold, unfocusedBorderColor = BorderCol,
            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
            focusedTextColor = TextMain, unfocusedTextColor = TextMain,
            cursorColor = Gold
        )
    )
}
