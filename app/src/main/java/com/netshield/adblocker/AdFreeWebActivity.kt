package com.netshield.adblocker

import android.annotation.SuppressLint
import android.net.Uri
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
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
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
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
