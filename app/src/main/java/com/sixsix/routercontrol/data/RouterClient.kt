package com.sixsix.routercontrol.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Kotlin port of the proven Python tool. Talks to a ZTE F670L (PTCL) using the
 * exact mechanisms we reverse-engineered:
 *  - 3-step login: token -> salt -> SHA256(password+salt)
 *  - device stats from wlan_client_stat (primed via menuView)
 *  - block/unblock via Parental Controls with RSA "Check" signature + rotating token
 *  - Wi-Fi changes with AES-encrypted fields + RSA "encode" key + Check
 */
class RouterClient(
    private val host: String = "http://192.168.1.1",
    private val username: String = "user"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .cookieJar(SimpleCookieJar())
        .build()

    private var token: String = ""

    private val loginEntry get() = "$host/?_type=loginData&_tag=login_entry"
    private val loginToken get() = "$host/?_type=loginData&_tag=login_token"
    private val wlanStat get() = "$host/?_type=menuData&_tag=wlan_client_stat_lua.lua"
    private val localnetPrimer get() = "$host/?_type=menuView&_tag=localNetStatus&Menu3Location=0"
    private val parentctrl get() = "$host/?_type=menuData&_tag=firewall_parentctrl_lua.lua"
    private val parentctrlPrimer get() = "$host/?_type=menuView&_tag=parentCtrl&Menu3Location=0"
    private val wlanSsid get() = "$host/?_type=menuData&_tag=wlan_wlansssidconf_lua.lua"
    private val wlanPrimer get() = "$host/?_type=menuView&_tag=wlanBasic&Menu3Location=0"

    companion object {
        const val PUBKEY_B64 =
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwlo/vZBnSJ2MyJ0dbNcw" +
            "DvzPqBN+O/BPvLX93GIJVSZmquJHD9X6Xn6VYeM9mRKzjEbXPlv73Dj/gjjtNj9j" +
            "Tq2QVyW2Sd4ZkY9e3h1ALCCCfkbjnmSqedyrcvXriTeW+J65jhBje6lTJbafmC5q" +
            "bGiItjt0OeOkT+Vb4S7hYPSWIjeYYBh+7Y/fg25Rt2a+RgC8dahvJ3ttB1LHXADr" +
            "oCm6q7G+lpbRAlpC8jjc0rZdS0c6HcBoYgzW8vxjj2fTuFy3CZZTrpPyTv/C8K6B" +
            "hjTnjRe6ocgFVyQ0RIYfx2hxSJcuauR57OzfMzlgFQv3RAXguDZtuVUFLO2sAiwL" +
            "ELph3Acfy9Eh58SHcswZvsOSXY0JNb0XeRM9gxpntLRfM6TB7f9hYtYTDw5oKdyN" +
            "BY+nnEa/IpBUjndGDrSs3Z4BxRbYcJEwkKQZkvw/5TpQYbkD6sTRVSlZPaXSjeCl" +
            "0hsLCttqwJqRZcjbWXrINBYFw8PYE14Xr9BCyPgqocdQh7FgvasVgG6u5mLR1PBZ" +
            "o4EFF/LdY0yvMG5rl9egBk1XD/UMayhRtmSQEUzYt3eEWLBbqJB6MbVJ2ygcv5EL" +
            "ReDY0SWXw1PIEbHeP51A/MyB6kwSgZwdoQW3JiaPnGHMaE0NqfAYPNiGJLMsmvT/" +
            "rNUI/8iSCW+WvSzx9tByUxsCAwEAAQ=="
    }

    // ---------- crypto helpers ----------
    private fun sha256Hex(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun rsaEncrypt(plain: String): String {
        val keyBytes = Base64.decode(PUBKEY_B64, Base64.DEFAULT)
        val pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub)
        return Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** Check header = RSA( sha256hex(body) ). */
    private fun checkHeader(body: String): String = rsaEncrypt(sha256Hex(body))

    /** AES-CBC(value, key=SHA256(keyStr), iv=first16(SHA256(ivStr)), ZeroPadding) -> base64. */
    private fun aesEncode(src: String, keyStr: String, ivStr: String): String {
        if (src.isEmpty()) return src
        val key = MessageDigest.getInstance("SHA-256").digest(keyStr.toByteArray())
        val ivFull = MessageDigest.getInstance("SHA-256").digest(ivStr.toByteArray())
        val iv = ivFull.copyOfRange(0, 16)
        var data = src.toByteArray(Charsets.UTF_8)
        val pad = (16 - data.size % 16) % 16
        if (pad > 0) data += ByteArray(pad)   // ZeroPadding
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
    }

    private fun randDigits(n: Int = 16) = (1..n).map { Random.nextInt(0, 10) }.joinToString("")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- HTTP helpers ----------
    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("Referer", "$host/")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        client.newCall(req).execute().use { return it.body?.string() ?: "" }
    }

    private fun postSigned(url: String, body: String): String {
        val rb = body.toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())
        val req = Request.Builder().url(url).post(rb)
            .header("Referer", "$host/")
            .header("Origin", host)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Check", checkHeader(body))
            .build()
        client.newCall(req).execute().use { return it.body?.string() ?: "" }
    }

    private fun refreshToken(vararg texts: String) {
        for (t in texts) {
            val m = Regex("id=\"_sessionTOKEN\"[^>]*value=\"([^\"]+)\"").find(t)
                ?: Regex("\"sess_token\"\\s*:\\s*\"([^\"]+)\"").find(t)
                ?: Regex("<_sessionTOKEN>([^<]+)</_sessionTOKEN>").find(t)
            if (m != null) { token = m.groupValues[1]; return }
        }
        // fallback
        runCatching {
            val j = get(loginEntry)
            Regex("\"sess_token\"\\s*:\\s*\"([^\"]+)\"").find(j)?.let { token = it.groupValues[1] }
        }
    }

    // ---------- login ----------
    suspend fun login(password: String): RouterResult<Unit> = withContext(Dispatchers.IO) {
        try {
            get("$host/")
            val entry = get(loginEntry)
            val tok = Regex("\"sess_token\"\\s*:\\s*\"([^\"]+)\"").find(entry)?.groupValues?.get(1)
                ?: return@withContext RouterResult.Err("No session token from router")
            val saltResp = get(loginToken)
            val salt = Regex("[0-9,]{4,}").findAll(saltResp).map { it.value }.maxByOrNull { it.length } ?: ""
            val hashed = sha256Hex(password + salt)
            val form = FormBody.Builder()
                .add("action", "login").add("Username", username)
                .add("Password", hashed).add("_sessionTOKEN", tok).build()
            val req = Request.Builder().url(loginEntry).post(form)
                .header("Referer", "$host/").header("X-Requested-With", "XMLHttpRequest").build()
            val resp = client.newCall(req).execute().use { it.body?.string() ?: "" }
            if (!resp.contains("login_need_refresh")) {
                return@withContext RouterResult.Err("Login failed — check password")
            }
            Regex("\"sess_token\"\\s*:\\s*\"([^\"]+)\"").find(resp)?.let { token = it.groupValues[1] }
            if (token.isEmpty()) token = tok
            get("$host/")
            RouterResult.Ok(Unit)
        } catch (e: Exception) {
            RouterResult.Err("Cannot reach router at $host (${e.message})")
        }
    }

    // ---------- instance parsing ----------
    private fun parseInstances(xml: String): List<Map<String, String>> {
        val out = ArrayList<Map<String, String>>()
        for (m in Regex("<Instance>(.*?)</Instance>", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val block = m.groupValues[1]
            val names = Regex("<ParaName>(.*?)</ParaName>", RegexOption.DOT_MATCHES_ALL)
                .findAll(block).map { it.groupValues[1].trim() }.toList()
            val vals = Regex("<ParaValue>(.*?)</ParaValue>", RegexOption.DOT_MATCHES_ALL)
                .findAll(block).map { unescape(it.groupValues[1].trim()) }.toList()
            if (names.size == vals.size) out.add(names.zip(vals).toMap())
        }
        return out
    }

    private fun unescape(s: String) = s
        .replace("&#32;", " ").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")

    // ---------- devices ----------
    /** Last raw response + diagnostic, surfaced to the UI when no devices parse. */
    var lastDiag: String = ""
        private set

    suspend fun devices(): RouterResult<List<Device>> = withContext(Dispatchers.IO) {
        try {
            val ts = System.currentTimeMillis()
            runCatching { get("$localnetPrimer&_=$ts") }
            var text = get("$wlanStat&_=${ts + 1}")
            var which = "menuData"
            if (!text.contains("<Instance>")) {
                text = get("$host/?_type=hiddenData&_tag=wlan_client_stat_lua.lua&_=${ts + 2}")
                which = "hiddenData"
            }
            val parsed = parseInstances(text)
            if (parsed.isEmpty()) {
                // Build a short diagnostic from the actual response so we can see why.
                val snippet = text.take(180).replace("\n", " ").replace("\r", " ")
                lastDiag = "via=$which len=${text.length} body=[$snippet]"
            } else {
                lastDiag = ""
            }
            val blocked = currentBlockedMacs()
            val list = parsed.mapNotNull { d ->
                val mac = (d["MACAddress"] ?: "").lowercase()
                if (mac.isEmpty()) null else Device(
                    mac = mac,
                    name = d["HostName"]?.ifEmpty { "(unknown)" } ?: "(unknown)",
                    ip = d["IPAddress"] ?: "",
                    band = d["BAND"] ?: "",
                    linkSecs = (d["LinkTime"] ?: "0").toLongOrNull() ?: 0,
                    rxBytes = (d["RXBytes"] ?: "0").toLongOrNull() ?: 0,
                    txBytes = (d["TXBytes"] ?: "0").toLongOrNull() ?: 0,
                    isBlocked = mac in blocked
                )
            }.sortedByDescending { it.totalBytes }
            RouterResult.Ok(list)
        } catch (e: Exception) {
            RouterResult.Err(e.message ?: "Failed to read devices")
        }
    }

    // ---------- parental control (block) ----------
    private fun primeParentctrl() {
        val ts = System.currentTimeMillis()
        runCatching { get("$parentctrlPrimer&_=$ts") }
        val r = runCatching { get("$parentctrl&_=${ts + 1}") }.getOrDefault("")
        refreshToken(r)
    }

    private fun rulesMap(): Map<String, Map<String, String>> {
        val ts = System.currentTimeMillis()
        runCatching { get("$parentctrlPrimer&_=$ts") }
        val text = runCatching { get("$parentctrl&_=${ts + 1}") }.getOrDefault("")
        val out = HashMap<String, Map<String, String>>()
        for (d in parseInstances(text)) {
            val mac = (d["ChildId"] ?: "").lowercase()
            if (mac.isNotEmpty()) out[mac] = d
        }
        return out
    }

    private fun currentBlockedMacs(): Set<String> = runCatching { rulesMap().keys }.getOrDefault(emptySet())

    suspend fun block(mac: String, name: String): RouterResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val m = mac.lowercase()
            val inst = rulesMap()[m]?.get("_InstID") ?: "-1"
            primeParentctrl()
            val fields = listOf(
                "IF_ACTION" to "Apply", "Enable" to "1", "_InstID" to inst,
                "ChildId" to m, "Week" to "127", "Name" to name.take(10),
                "StartHour" to "0", "StartMin" to "0", "EndHour" to "23", "EndMin" to "59",
                "FilterMode" to "NotAllowed", "Btn_cancel_ParentCtrl" to "",
                "Btn_apply_ParentCtrl" to "", "_sessionTOKEN" to token
            )
            val body = fields.joinToString("&") { "${it.first}=${enc(it.second)}" }
            postSigned(parentctrl, body)
            if (m in rulesMap()) RouterResult.Ok(Unit) else RouterResult.Err("Block not stored")
        } catch (e: Exception) { RouterResult.Err(e.message ?: "Block failed") }
    }

    suspend fun unblock(mac: String): RouterResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val m = mac.lowercase()
            val existing = rulesMap()[m] ?: return@withContext RouterResult.Ok(Unit)
            primeParentctrl()
            val fields = listOf(
                "IF_ACTION" to "Delete", "Enable" to "1", "_InstID" to (existing["_InstID"] ?: ""),
                "ChildId" to m, "Week" to "127", "Name" to (existing["Name"] ?: "blocked"),
                "StartHour" to "0", "StartMin" to "0", "EndHour" to "23", "EndMin" to "59",
                "FilterMode" to "NotAllowed", "Btn_cancel_ParentCtrl" to "",
                "Btn_apply_ParentCtrl" to "", "_sessionTOKEN" to token
            )
            val body = fields.joinToString("&") { "${it.first}=${enc(it.second)}" }
            postSigned(parentctrl, body)
            if (m !in rulesMap()) RouterResult.Ok(Unit) else RouterResult.Err("Unblock failed")
        } catch (e: Exception) { RouterResult.Err(e.message ?: "Unblock failed") }
    }

    // ---------- Wi-Fi settings ----------
    private fun ssidConfig(inst: String): Map<String, String>? {
        val ts = System.currentTimeMillis()
        runCatching { get("$wlanPrimer&_=$ts") }
        val text = runCatching { get("$wlanSsid&_=${ts + 1}") }.getOrDefault("")
        refreshToken(text)
        return parseInstances(text).firstOrNull { it["_InstID"] == inst }
    }

    /** Return label -> current SSID name for the enabled radios, for display. */
    suspend fun ssidNames(): Map<String, String> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, String>()
        try {
            val ts = System.currentTimeMillis()
            runCatching { get("$wlanPrimer&_=$ts") }
            val text = runCatching { get("$wlanSsid&_=${ts + 1}") }.getOrDefault("")
            for (inst in parseInstances(text)) {
                val id = inst["_InstID"] ?: continue
                val m = Regex("DEV\\.WIFI\\.AP([1-8])").matchEntire(id) ?: continue
                val essid = inst["ESSID"] ?: ""
                val enabled = (inst["Enable"] ?: "0") == "1"
                if (essid.isNotEmpty()) {
                    val n = m.groupValues[1]
                    out["SSID$n"] = essid + (if (!enabled) " (off)" else "")
                }
            }
        } catch (_: Exception) {}
        out
    }

    /** ssidLabel like "SSID1".."SSID8" (1-4 = 2.4GHz, 5-8 = 5GHz). */
    suspend fun setWifi(ssidLabel: String, newName: String?, newPass: String?):
        RouterResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val mm = Regex("(?i)ssid([1-8])").matchEntire(ssidLabel.trim())
                ?: return@withContext RouterResult.Err("SSID must be SSID1..SSID8")
            val inst = "DEV.WIFI.AP${mm.groupValues[1]}"
            val cur = ssidConfig(inst) ?: return@withContext RouterResult.Err("Could not read $ssidLabel")
            val essid = newName ?: cur["ESSID"] ?: ""
            val key16 = randDigits(); val iv16 = randDigits()
            fun e(v: String) = aesEncode(v, key16, iv16)
            val fields = ArrayList<Pair<String, String>>()
            fields.addAll(listOf(
                "IF_ACTION" to "Apply", "Enable" to "1", "_InstID" to inst,
                "_WEPCONIG" to "N", "_PSKCONIG" to "Y", "BeaconType" to "11i",
                "WEPAuthMode" to "None", "WPAAuthMode" to "PSKAuthentication",
                "11iAuthMode" to "PSKAuthentication",
                "WPAEncryptType" to "TKIPandAESEncryption", "11iEncryptType" to "AESEncryption",
                "_InstID_WEP0" to "$inst.WEP1", "_InstID_WEP1" to "$inst.WEP2",
                "_InstID_WEP2" to "$inst.WEP3", "_InstID_WEP3" to "$inst.WEP4",
                "_InstID_PSK" to "$inst.PSK1",
                "MasterAuthServerIp" to "...", "BackupAuthServerIp" to "...",
                "MasterAcctServerIp" to "...", "BackupAcctServerIp" to "...",
                "_InstID_GUEST" to "", "_GUEST" to "N",
                "ESSID" to essid,
                "ESSIDHideEnable" to (cur["ESSIDHideEnable"] ?: "0"),
                "EncryptionType" to "WPA2-PSK-AES"
            ))
            var needEncode = false
            if (newPass != null) { fields.add("KeyPassphrase" to e(newPass)); needEncode = true }
            fields.addAll(listOf(
                "WEPKeyIndex" to "1", "ShowWEPKey" to "0",
                "WEPKey00" to e(""), "WEPKey01" to e(""), "WEPKey02" to e(""), "WEPKey03" to e(""),
                "Btn_cancel_WLANSSIDConf" to "", "Btn_apply_WLANSSIDConf" to ""
            ))
            if (needEncode) fields.add("encode" to rsaEncrypt("$key16+$iv16"))
            fields.add("_sessionTOKEN" to token)
            val body = fields.joinToString("&") { "${it.first}=${enc(it.second)}" }
            val resp = postSigned(wlanSsid, body)
            if (resp.contains("SUCC")) RouterResult.Ok(Unit)
            else RouterResult.Err("Router rejected the change")
        } catch (e: Exception) { RouterResult.Err(e.message ?: "Wi-Fi change failed") }
    }
}
