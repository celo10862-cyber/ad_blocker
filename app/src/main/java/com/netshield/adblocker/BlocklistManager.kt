package com.netshield.adblocker

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class ProtectionMode { STANDARD, BALANCED, STRICT }

object BlocklistManager {
    private val adDomains = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "admob.com", "ads.youtube.com",
        "unityads.unity3d.com", "ads.mopub.com", "applovin.com",
        "vungle.com", "ironsrc.com", "chartboost.com", "inmobi.com",
        "adcolony.com", "taboola.com", "outbrain.com", "criteo.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com",
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "juicyads.com", "trafficjunky.com", "adsterra.com", "mgid.com",
        "revcontent.com", "zedo.com", "advertising.com", "bat.bing.com",
        "ads.tiktok.com"
    )
    private val trackerDomains = setOf(
        "app-measurement.com", "scorecardresearch.com", "quantserve.com",
        "hotjar.com", "mixpanel.com", "segment.io", "adjust.com",
        "appsflyer.com", "branch.io", "kochava.com", "flurry.com",
        "an.facebook.com", "firebase-settings.crashlytics.com"
    )
    private val strictDomains = setOf(
        "analytics.google.com", "analytics.twitter.com", "pixel.facebook.com",
        "connect.facebook.net", "static.ads-twitter.com"
    )
    private val customBlocked = ConcurrentHashMap.newKeySet<String>()
    private val allowlisted = ConcurrentHashMap.newKeySet<String>()
    @Volatile var mode: ProtectionMode = ProtectionMode.BALANCED
        private set
    val blockedCount = AtomicInteger(0)
    val allowedPackages = ConcurrentHashMap.newKeySet<String>()


    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("netshield", Context.MODE_PRIVATE)
        mode = runCatching {
            ProtectionMode.valueOf(prefs.getString("protection_mode", "BALANCED") ?: "BALANCED")
        }.getOrDefault(ProtectionMode.BALANCED)
        customBlocked.addAll(prefs.getStringSet("custom_blocked", emptySet()) ?: emptySet())
        allowlisted.addAll(prefs.getStringSet("allowlisted", emptySet()) ?: emptySet())
        allowedPackages.addAll(prefs.getStringSet("allowed_packages", emptySet()) ?: emptySet())
        blockedCount.set(prefs.getInt("blocked_count", 0))
    }

    fun setAllowedPackages(context: Context, packages: Set<String>) {
        allowedPackages.clear()
        allowedPackages.addAll(packages)
        context.getSharedPreferences("netshield", Context.MODE_PRIVATE).edit()
            .putStringSet("allowed_packages", packages.toSet()).apply()
    }

    fun isPackageAllowed(packageName: String): Boolean =
        allowedPackages.isEmpty() || allowedPackages.contains(packageName)

    fun recordBlocked(context: Context) {
        val count = blockedCount.incrementAndGet()
        context.getSharedPreferences("netshield", Context.MODE_PRIVATE).edit()
            .putInt("blocked_count", count).apply()
    }

    fun resetStatistics(context: Context) {
        blockedCount.set(0)
        context.getSharedPreferences("netshield", Context.MODE_PRIVATE).edit()
            .putInt("blocked_count", 0).apply()
    }

    fun setMode(context: Context, newMode: ProtectionMode) {
        mode = newMode
        context.getSharedPreferences("netshield", Context.MODE_PRIVATE)
            .edit().putString("protection_mode", newMode.name).apply()
    }

    fun isBlocked(domain: String): Boolean {
        val clean = normalizeDomain(domain) ?: return false
        if (matches(allowlisted, clean)) return false
        if (matches(customBlocked, clean)) return true
        if (matches(adDomains, clean)) return true
        if (mode == ProtectionMode.BALANCED || mode == ProtectionMode.STRICT) {
            if (matches(trackerDomains, clean)) return true
        }
        return mode == ProtectionMode.STRICT && matches(strictDomains, clean)
    }

    private fun matches(rules: Set<String>, domain: String): Boolean {
        if (rules.contains(domain)) return true
        var index = domain.indexOf('.')
        while (index >= 0) {
            if (rules.contains(domain.substring(index + 1))) return true
            index = domain.indexOf('.', index + 1)
        }
        return false
    }

    fun normalizeDomain(value: String): String? {
        val clean = value.trim().lowercase().trimEnd('.')
        if (clean.length !in 1..253 || clean.contains("://") || clean.contains('/') ||
            clean.contains(' ') || clean.startsWith(".") || clean.endsWith(".")) return null
        val labels = clean.split('.')
        if (labels.size < 2 || labels.any {
                it.isEmpty() || it.length > 63 || it.startsWith("-") || it.endsWith("-") ||
                    !it.all { c -> c.isLetterOrDigit() || c == '-' }
            }) return null
        return clean
    }

    fun addCustomDomain(context: Context, domain: String): Boolean {
        val clean = normalizeDomain(domain) ?: return false
        allowlisted.remove(clean)
        customBlocked.add(clean)
        persist(context)
        return true
    }

    fun allowDomain(context: Context, domain: String): Boolean {
        val clean = normalizeDomain(domain) ?: return false
        customBlocked.remove(clean)
        allowlisted.add(clean)
        persist(context)
        return true
    }

    private fun persist(context: Context) {
        context.getSharedPreferences("netshield", Context.MODE_PRIVATE).edit()
            .putStringSet("custom_blocked", customBlocked.toSet())
            .putStringSet("allowlisted", allowlisted.toSet()).apply()
    }

    fun getRuleCount(): Int = adDomains.size + trackerDomains.size + strictDomains.size + customBlocked.size

    fun syncRemoteHosts(context: Context, onComplete: (Int, String?) -> Unit) {
        Thread {
            var error: String? = null
            try {
                val conn = (URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "NetShield-Android")
                }
                conn.inputStream.bufferedReader().useLines { lines ->
                    var accepted = 0
                lines.forEach { line ->
                        val trimmed = line.substringBefore('#').trim()
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                            normalizeDomain(parts[1])?.let { if (customBlocked.add(it)) accepted++ }
                        }
                    }
                }
                conn.disconnect()
                persist(context)
            } catch (e: Exception) {
                error = e.message ?: "Unable to update filter list"
            }
            onComplete(getRuleCount(), error)
        }.start()
    }
}
