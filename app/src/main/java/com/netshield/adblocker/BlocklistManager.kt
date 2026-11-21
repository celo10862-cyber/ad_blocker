package com.netshield.adblocker

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Thread-safe domain rules. Rules are persisted locally; no browsing history is collected. */
object BlocklistManager {
    private const val PREFS = "netshield_rules"
    private const val KEY_CUSTOM = "custom_domains"
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    val blockedCount = AtomicInteger(0)
    @Volatile private var initialized = false

    private val builtIn = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "pagead2.googlesyndication.com", "admob.com",
        "app-measurement.com", "unityads.unity3d.com", "applovin.com", "vungle.com",
        "ironsrc.com", "chartboost.com", "inmobi.com", "adcolony.com", "taboola.com",
        "outbrain.com", "criteo.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "casalemedia.com", "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "juicyads.com", "trafficjunky.com", "adsterra.com", "mgid.com", "revcontent.com",
        "zedo.com", "advertising.com", "scorecardresearch.com", "quantserve.com",
        "hotjar.com", "mixpanel.com", "segment.io", "adjust.com", "appsflyer.com",
        "branch.io", "kochava.com", "flurry.com", "bat.bing.com", "ads.tiktok.com"
    )

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            blockedDomains.addAll(builtIn)
            val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_CUSTOM, emptySet()).orEmpty()
            blockedDomains.addAll(saved.mapNotNull(::normalizeDomain))
            initialized = true
        }
    }

    private fun normalizeDomain(input: String): String? {
        val value = input.trim().lowercase(Locale.ROOT).trimEnd('.')
            .removePrefix("http://").removePrefix("https://").substringBefore('/')
            .substringBefore(':')
        if (value.length !in 1..253 || value.startsWith('.') || value.endsWith('.')) return null
        val labels = value.split('.')
        if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') || !it.matches(Regex("[a-z0-9-]+")) }) return null
        return value
    }

    fun isBlocked(domain: String): Boolean {
        val clean = normalizeDomain(domain) ?: return false
        if (clean in blockedDomains) return true
        var index = clean.indexOf('.')
        while (index >= 0) {
            if (clean.substring(index + 1) in blockedDomains) return true
            index = clean.indexOf('.', index + 1)
        }
        return false
    }

    fun addCustomDomain(context: Context, domain: String): Boolean {
        val clean = normalizeDomain(domain) ?: return false
        blockedDomains.add(clean)
        persist(context)
        return true
    }

    private fun persist(context: Context) {
        val custom = blockedDomains.filter { it !in builtIn }.toSet()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CUSTOM, custom).apply()
    }

    fun getRuleCount(): Int = blockedDomains.size

    fun syncRemoteHosts(context: Context, onComplete: (Int, String?) -> Unit) {
        Thread {
            var error: String? = null
            try {
                val conn = (URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "NetShield-AdBlocker/1.1")
                }
                try {
                    if (conn.responseCode !in 200..299) error = "List server returned HTTP ${conn.responseCode}"
                    else conn.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { raw ->
                            val line = raw.substringBefore('#').trim()
                            val parts = line.split(Regex("\\s+"))
                            if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                                parts.drop(1).forEach { normalizeDomain(it)?.let(blockedDomains::add) }
                            }
                        }
                    }
                } finally { conn.disconnect() }
                persist(context)
            } catch (e: Exception) {
                error = e.message ?: "Unable to update blocklist"
            }
            onComplete(blockedDomains.size, error)
        }.start()
    }
}
