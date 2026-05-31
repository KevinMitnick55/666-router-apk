package com.sixsix.routercontrol.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * In-memory cookie jar that MERGES cookies by name instead of replacing the
 * whole set on every response. This matters for the router: the session cookie
 * is set at login, and later responses that carry no Set-Cookie must NOT wipe
 * it. The previous version overwrote everything, which dropped the session and
 * made all data reads come back empty (the bug behind "no devices").
 */
class SimpleCookieJar : CookieJar {
    // host -> (cookie name -> cookie)
    private val store = HashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val byName = store.getOrPut(url.host) { mutableMapOf() }
        for (c in cookies) {
            // An expired cookie removes it; otherwise upsert by name.
            if (c.expiresAt < System.currentTimeMillis()) byName.remove(c.name)
            else byName[c.name] = c
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val byName = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return byName.values.filter { it.expiresAt >= now }
    }
}
