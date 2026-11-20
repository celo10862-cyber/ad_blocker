package com.netshield.adblocker

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object BlocklistManager {
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    val blockedCount = AtomicInteger(0)

    init {
        val defaultDomains = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "adservice.google.com", "pagead2.googlesyndication.com",
            "tpc.googlesyndication.com", "admob.com", "app-measurement.com",
            "firebase-settings.crashlytics.com", "ads.youtube.com",
            "unityads.unity3d.com", "ads.mopub.com", "applovin.com",
            "vungle.com", "ironsrc.com", "chartboost.com", "inmobi.com",
            "adcolony.com", "taboola.com", "outbrain.com", "criteo.com",
            "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com",
            "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
            "juicyads.com", "trafficjunky.com", "adsterra.com", "mgid.com",
            "revcontent.com", "zedo.com", "advertising.com", "scorecardresearch.com",
            "quantserve.com", "hotjar.com", "mixpanel.com", "segment.io",
            "adjust.com", "appsflyer.com", "branch.io", "kochava.com",
            "flurry.com", "bat.bing.com", "an.facebook.com", "ads.tiktok.com"
        )
        blockedDomains.addAll(defaultDomains)
    }

    fun isBlocked(domain: String): Boolean {
        val clean = domain.lowercase().trimEnd('.')
        if (blockedDomains.contains(clean)) return true
        var dotIndex = clean.indexOf('.')
        while (dotIndex != -1) {
            val parent = clean.substring(dotIndex + 1)
            if (blockedDomains.contains(parent)) return true
            dotIndex = clean.indexOf('.', dotIndex + 1)
        }
        return false
    }

    fun addCustomDomain(domain: String) {
        val clean = domain.lowercase().trim()
        if (clean.isNotEmpty()) blockedDomains.add(clean)
    }

    fun getRuleCount(): Int = blockedDomains.size

    fun syncRemoteHosts(onComplete: (Int) -> Unit) {
        Thread {
            try {
                val url = URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("0.0.0.0 ")) {
                            val host = trimmed.removePrefix("0.0.0.0 ").split("#")[0].trim()
                            if (host.isNotEmpty() && host != "0.0.0.0") {
                                blockedDomains.add(host.lowercase())
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                onComplete(blockedDomains.size)
            }
        }.start()
    }
}
