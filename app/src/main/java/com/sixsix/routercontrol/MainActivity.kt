package com.sixsix.routercontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixsix.routercontrol.ui.AppViewModel
import com.sixsix.routercontrol.ui.screens.*
import com.sixsix.routercontrol.ui.theme.*

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RouterTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(state.info, state.error) {
                    val msg = state.info ?: state.error
                    if (msg != null) {
                        snackbar.showSnackbar(msg)
                        vm.clearInfo()
                    }
                }

                if (!state.loggedIn) {
                    LoginScreen(
                        saved = state.savedLogin,
                        loading = state.loading,
                        error = state.error,
                        onLogin = vm::login
                    )
                } else {
                    var tab by remember { mutableStateOf(0) }
                    var showReset by remember { mutableStateOf(false) }

                    Scaffold(
                        containerColor = Ink,
                        snackbarHost = { SnackbarHost(snackbar) },
                        topBar = {
                            TopAppBar(
                                title = { Text("666 Router", color = Gold) },
                                actions = {
                                    IconButton(onClick = vm::logout) {
                                        Icon(Icons.Default.Logout, "logout", tint = Text2)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = InkElev, titleContentColor = Gold)
                            )
                        },
                        bottomBar = {
                            NavigationBar(containerColor = InkElev) {
                                NavigationBarItem(
                                    selected = tab == 0, onClick = { tab = 0 },
                                    icon = { Icon(Icons.Default.Router, "devices") },
                                    label = { Text("Devices") },
                                    colors = navColors()
                                )
                                NavigationBarItem(
                                    selected = tab == 1, onClick = { tab = 1 },
                                    icon = { Icon(Icons.Default.Wifi, "wifi") },
                                    label = { Text("Wi-Fi") },
                                    colors = navColors()
                                )
                            }
                        }
                    ) { pad ->
                        when (tab) {
                            0 -> DashboardScreen(
                                devices = state.devices,
                                usage = state.usage,
                                busyMac = state.busyMac,
                                onToggle = vm::toggleBlock,
                                onRefresh = vm::refresh,
                                onResetCounter = vm::resetCounter,
                                onOpenResetSettings = { showReset = true },
                                modifier = Modifier.padding(pad)
                            )
                            1 -> WifiScreenWrap(state.loading, state.ssidNames, vm::setWifi, Modifier.padding(pad))
                        }
                    }

                    if (showReset) {
                        ResetSettingsDialog(
                            currentMode = state.resetConfig.mode,
                            currentDay = state.resetConfig.day,
                            onDismiss = { showReset = false },
                            onSave = { m, d -> vm.setResetMode(m, d); showReset = false }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Ink, selectedTextColor = Gold,
    indicatorColor = Gold, unselectedIconColor = Text2, unselectedTextColor = Text2
)

// thin wrappers to accept the Modifier from Scaffold padding
@androidx.compose.runtime.Composable
private fun WifiScreenWrap(loading: Boolean, ssidNames: Map<String, String>,
    onApply: (String, String?, String?) -> Unit, modifier: Modifier) {
    androidx.compose.foundation.layout.Box(modifier) { WifiScreen(loading, ssidNames, onApply) }
}
