package com.netshield.adblocker

import android.annotation.SuppressLint
import android.net.Uri
<<<<<<< HEAD
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

/** Lightweight browser. Its cosmetic filtering applies only to this WebView. */
class AdFreeWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var addressBar: EditText

    private val cosmeticFilter = """
        (function() {
          if (window.__netShieldInjected) return;
          window.__netShieldInjected = true;
          const css = `.adsbygoogle,[id^="google_ads_"],[class*="ad-banner"],
            [class*="advertisement"],[data-ad],[aria-label="Advertisement"] { display:none!important; }`;
          const style = document.createElement('style');
          style.textContent = css;
          (document.head || document.documentElement).appendChild(style);
=======
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class AdFreeWebActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText

    private val adBlockScript = """
        (function() {
            const style = document.createElement('style');
            style.innerHTML = `
                .ad-showing, .video-ads, .ytp-ad-module, .ytp-ad-overlay-container,
                ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer,
                .ad-container, .adsbygoogle, [id^="google_ads_"], [class*="popup"],
                [class*="ad-banner"], ytd-display-ad-renderer {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                }
            `;
            document.documentElement.appendChild(style);

            window.open = function() { return null; };

            setInterval(() => {
                const video = document.querySelector('video');
                const adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                if (video && adShowing) {
                    video.muted = true;
                    if (isFinite(video.duration) && video.duration > 0) {
                        video.currentTime = video.duration;
                    }
                    video.playbackRate = 16.0;
                }
                const skipBtn = document.querySelector(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button'
                );
                if (skipBtn) skipBtn.click();
            }, 250);
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
<<<<<<< HEAD
        BlocklistManager.initialize(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff080d19.toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
        }
        addressBar = EditText(this).apply {
            hint = "Search or enter HTTPS address"
            setSingleLine(true)
            setTextColor(0xfff4f7ff.toInt())
            setHintTextColor(0xff8492aa.toInt())
            setBackgroundColor(0xff131e30.toInt())
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, _, _ -> loadAddress(text.toString()); true }
        }
        val go = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Load address"
            setBackgroundColor(0xff76e7d1.toInt())
            setOnClickListener { loadAddress(addressBar.text.toString()) }
        }
        toolbar.addView(addressBar)
        toolbar.addView(go, LinearLayout.LayoutParams(48, 48).apply { marginStart = 8 })

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = true
            if (Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return uri.scheme != "https" && uri.scheme != "http"
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
                val host = uri.host.orEmpty()
                if (host.isNotBlank() && BlocklistManager.isBlocked(host)) {
                    BlocklistManager.blockedCount.incrementAndGet()
                    return WebResourceResponse("text/plain", "UTF-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
=======

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        urlBar = EditText(this).apply {
            hint = "Enter URL or search..."
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val goBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setOnClickListener { loadInputUrl(urlBar.text.toString()) }
        }

        topBar.addView(urlBar)
        topBar.addView(goBtn)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                val host = request.url?.host ?: ""

                if (BlocklistManager.isBlocked(host) ||
                    url.contains("/pagead/") ||
                    url.contains("/ptracking") ||
                    url.contains("/api/stats/ads") ||
                    url.contains("&oad=") ||
                    url.contains("doubleclick")
                ) {
                    BlocklistManager.blockedCount.incrementAndGet()
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
<<<<<<< HEAD
                addressBar.setText(url.orEmpty())
                view?.evaluateJavascript(cosmeticFilter, null)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                handler?.cancel() // Never bypass certificate errors.
                Toast.makeText(this@AdFreeWebActivity, "Secure connection rejected: certificate error", Toast.LENGTH_LONG).show()
            }
        }
        root.addView(toolbar)
        root.addView(webView)
        setContentView(root)
        loadAddress(intent.getStringExtra("URL") ?: "https://duckduckgo.com")
    }

    private fun loadAddress(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val target = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.contains('.') && !value.contains(' ') -> "https://$value"
            else -> "https://duckduckgo.com/?q=${Uri.encode(value)}"
        }
        val uri = Uri.parse(target)
        if (uri.scheme != "https" && uri.scheme != "http") {
            Toast.makeText(this, "Only HTTP and HTTPS addresses are supported", Toast.LENGTH_SHORT).show()
            return
        }
        webView.loadUrl(target)
=======
                urlBar.setText(url)
                view?.evaluateJavascript(adBlockScript, null)
            }
        }

        root.addView(topBar)
        root.addView(webView)
        setContentView(root)

        val initialUrl = intent.getStringExtra("URL") ?: "https://m.youtube.com"
        loadInputUrl(initialUrl)
    }

    private fun loadInputUrl(raw: String) {
        val trimmed = raw.trim()
        val finalUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=${Uri.encode(trimmed)}"
        }
        webView.loadUrl(finalUrl)
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
<<<<<<< HEAD
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
=======
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
    }
}
