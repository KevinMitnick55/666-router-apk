package com.sixsix.routercontrol.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "router_app")

/**
 * Persists everything that must survive app restarts AND router reboots:
 *  - saved login (host/user/password, remember flag)
 *  - the accumulated MB counter (per-device + network), independent of the
 *    router's own counters which reset on reboot
 *  - reset config (manual vs monthly-on-a-date)
 */
class AppStore(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val USER = stringPreferencesKey("user")
        val PASS = stringPreferencesKey("pass")
        val REMEMBER = booleanPreferencesKey("remember")

        // usage accumulation
        val LAST_SAMPLE = stringPreferencesKey("last_sample")   // json mac->bytes (router counters)
        val DAILY = stringPreferencesKey("daily")               // json date->(mac->bytes)
        val TRACK_SINCE = longPreferencesKey("track_since")

        // reset config
        val RESET_MODE = stringPreferencesKey("reset_mode")     // "manual" | "monthly"
        val RESET_DAY = intPreferencesKey("reset_day")          // 1..28
        val LAST_RESET = stringPreferencesKey("last_reset")     // ISO date
    }

    // ---- credentials ----
    data class SavedLogin(val host: String, val user: String, val pass: String, val remember: Boolean)

    suspend fun loadLogin(): SavedLogin {
        val p = context.dataStore.data.first()
        return SavedLogin(
            host = p[Keys.HOST] ?: "http://192.168.1.1",
            user = p[Keys.USER] ?: "user",
            pass = if (p[Keys.REMEMBER] == true) (p[Keys.PASS] ?: "") else "",
            remember = p[Keys.REMEMBER] ?: false
        )
    }

    suspend fun saveLogin(host: String, user: String, pass: String, remember: Boolean) {
        context.dataStore.edit {
            it[Keys.HOST] = host
            it[Keys.USER] = user
            it[Keys.REMEMBER] = remember
            if (remember) it[Keys.PASS] = pass else it.remove(Keys.PASS)
        }
    }

    // ---- reset config ----
    data class ResetConfig(val mode: String, val day: Int)

    suspend fun loadResetConfig(): ResetConfig {
        val p = context.dataStore.data.first()
        return ResetConfig(p[Keys.RESET_MODE] ?: "manual", p[Keys.RESET_DAY] ?: 1)
    }

    suspend fun saveResetConfig(mode: String, day: Int) {
        context.dataStore.edit { it[Keys.RESET_MODE] = mode; it[Keys.RESET_DAY] = day }
    }

    // ---- usage accumulation ----
    /** Add the positive delta of each device's router counter into today's bucket.
     *  Handles counter resets (reboot / device reconnect) by counting from zero. */
    suspend fun accumulate(devices: List<Device>) {
        context.dataStore.edit { p ->
            val last = JSONObject(p[Keys.LAST_SAMPLE] ?: "{}")
            val daily = JSONObject(p[Keys.DAILY] ?: "{}")
            val today = java.time.LocalDate.now().toString()
            val dayObj = if (daily.has(today)) daily.getJSONObject(today) else JSONObject()
            for (d in devices) {
                val cur = d.totalBytes
                val prev = if (last.has(d.mac)) last.getLong(d.mac) else null
                val add = when {
                    prev == null -> 0L
                    cur >= prev -> cur - prev
                    else -> cur                       // counter reset -> count from zero
                }
                if (add > 0) {
                    val existing = if (dayObj.has(d.mac)) dayObj.getLong(d.mac) else 0L
                    dayObj.put(d.mac, existing + add)
                }
                last.put(d.mac, cur)
            }
            daily.put(today, dayObj)
            p[Keys.LAST_SAMPLE] = last.toString()
            p[Keys.DAILY] = daily.toString()
            if (p[Keys.TRACK_SINCE] == null) p[Keys.TRACK_SINCE] = System.currentTimeMillis()
            // auto monthly reset if configured
            maybeMonthlyReset(p)
        }
    }

    private fun maybeMonthlyReset(p: MutablePreferences) {
        if (p[Keys.RESET_MODE] != "monthly") return
        val day = p[Keys.RESET_DAY] ?: 1
        val now = java.time.LocalDate.now()
        if (now.dayOfMonth == day) {
            val marker = now.toString()
            if (p[Keys.LAST_RESET] != marker) {
                p[Keys.DAILY] = "{}"
                p[Keys.TRACK_SINCE] = System.currentTimeMillis()
                p[Keys.LAST_RESET] = marker
            }
        }
    }

    suspend fun resetCounter() {
        context.dataStore.edit {
            it[Keys.DAILY] = "{}"
            it[Keys.TRACK_SINCE] = System.currentTimeMillis()
            it[Keys.LAST_RESET] = java.time.LocalDate.now().toString()
        }
    }

    suspend fun usageTotals(window: Int = 36500): UsageTotals {
        val p = context.dataStore.data.first()
        val daily = JSONObject(p[Keys.DAILY] ?: "{}")
        val perDevice = HashMap<String, Long>()
        var net = 0L
        val today = java.time.LocalDate.now()
        val keys = daily.keys()
        var dayCount = 0
        while (keys.hasNext()) {
            val ds = keys.next()
            val withinWindow = runCatching {
                val dd = java.time.LocalDate.parse(ds)
                java.time.temporal.ChronoUnit.DAYS.between(dd, today) < window
            }.getOrDefault(true)
            if (!withinWindow) continue
            dayCount++
            val obj = daily.getJSONObject(ds)
            val mk = obj.keys()
            while (mk.hasNext()) {
                val mac = mk.next(); val b = obj.getLong(mac)
                perDevice[mac] = (perDevice[mac] ?: 0L) + b
                net += b
            }
        }
        return UsageTotals(net, perDevice, p[Keys.TRACK_SINCE] ?: System.currentTimeMillis(), dayCount)
    }
}
