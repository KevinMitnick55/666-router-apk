package com.sixsix.routercontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sixsix.routercontrol.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val devices: List<Device> = emptyList(),
    val usage: UsageTotals? = null,
    val savedLogin: AppStore.SavedLogin? = null,
    val resetConfig: AppStore.ResetConfig = AppStore.ResetConfig("manual", 1),
    val busyMac: String? = null,
    val ssidNames: Map<String, String> = emptyMap()
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AppStore(app)
    private var client: RouterClient? = null
    private var pollJob: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.loadLogin()
            val reset = store.loadResetConfig()
            _state.value = _state.value.copy(savedLogin = saved, resetConfig = reset)
        }
    }

    fun login(host: String, user: String, pass: String, remember: Boolean) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val c = RouterClient(host = host, username = user)
            when (val r = c.login(pass)) {
                is RouterResult.Ok -> {
                    client = c
                    store.saveLogin(host, user, pass, remember)
                    _state.value = _state.value.copy(loggedIn = true, loading = false)
                    refresh()
                    loadSsidNames()
                    startPolling()
                }
                is RouterResult.Err ->
                    _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun logout() {
        pollJob?.cancel(); client = null
        _state.value = UiState(savedLogin = _state.value.savedLogin, resetConfig = _state.value.resetConfig)
    }

    fun refresh() {
        val c = client ?: return
        viewModelScope.launch {
            when (val r = c.devices()) {
                is RouterResult.Ok -> {
                    store.accumulate(r.value)
                    val totals = store.usageTotals()
                    val diag = if (r.value.isEmpty() && c.lastDiag.isNotEmpty())
                        "Empty read — ${c.lastDiag}" else null
                    _state.value = _state.value.copy(
                        devices = r.value, usage = totals, error = diag)
                }
                is RouterResult.Err -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(60_000)
                refresh()
            }
        }
    }

    fun toggleBlock(d: Device) {
        val c = client ?: return
        _state.value = _state.value.copy(busyMac = d.mac)
        viewModelScope.launch {
            val r = if (d.isBlocked) c.unblock(d.mac) else c.block(d.mac, d.name)
            val msg = when (r) {
                is RouterResult.Ok -> if (d.isBlocked) "${d.name} unblocked" else "${d.name} blocked"
                is RouterResult.Err -> r.message
            }
            _state.value = _state.value.copy(busyMac = null, info = msg)
            refresh()
        }
    }

    private fun loadSsidNames() {
        val c = client ?: return
        viewModelScope.launch {
            val names = c.ssidNames()
            if (names.isNotEmpty()) _state.value = _state.value.copy(ssidNames = names)
        }
    }

    fun setWifi(ssid: String, name: String?, pass: String?) {
        val c = client ?: return
        _state.value = _state.value.copy(loading = true, error = null, info = null)
        viewModelScope.launch {
            val r = c.setWifi(ssid, name?.ifBlank { null }, pass?.ifBlank { null })
            _state.value = when (r) {
                is RouterResult.Ok -> _state.value.copy(loading = false, info = "$ssid updated — reconnect with new settings")
                is RouterResult.Err -> _state.value.copy(loading = false, error = r.message)
            }
            loadSsidNames()
        }
    }

    fun resetCounter() {
        viewModelScope.launch {
            store.resetCounter()
            _state.value = _state.value.copy(usage = store.usageTotals(), info = "Usage counter reset")
        }
    }

    fun setResetMode(mode: String, day: Int) {
        viewModelScope.launch {
            store.saveResetConfig(mode, day)
            _state.value = _state.value.copy(resetConfig = AppStore.ResetConfig(mode, day),
                info = if (mode == "monthly") "Auto-reset on day $day each month" else "Manual reset mode")
        }
    }

    fun clearInfo() { _state.value = _state.value.copy(info = null, error = null) }
}
