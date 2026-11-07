package com.sportage.obd.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sportage.obd.ui.theme.*
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val webViewClient = remember(context) {
        object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (url.startsWith("file:///android_asset/") || url.startsWith("file:///android_res/raw/")) {
                    return null
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
    }

    // Load local HTML first; fall back to the public URL only when the local asset
    // is unavailable (missing from the APK, or the device cannot load it).
    val localHtml = remember(context) {
        runCatching {
            PrivacyHtmlLoader.loadLocalHtml(context)
        }.getOrNull()
    }

    val effectiveHtml = localHtml ?: return@remember ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", color = PrimaryText()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = PrimaryText())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground())
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidWebView(
                context = context,
                html = effectiveHtml,
                webViewClient = webViewClient,
                onOpenExternalLink = onOpenExternalUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AndroidWebView(
    context: android.content.Context,
    html: String,
    webViewClient: WebViewClient,
    onOpenExternalLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = this@AndroidWebView.webViewClient
            navigationDelegate = object : WebView.NavigationDelegate {
                override fun shouldStartLoad(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        onOpenExternalUrl(url)
                        return false
                    }
                    return true
                }

                override fun preloadPage(
                    view: WebView,
                    callback: WebView.PreloadPageCallback
                ) {
                    callback.preload()
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String?,
                    favicon: Bitmap?
                ) {
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String?
                ) {
                    super.onPageFinished(view, url)
                }

                override fun onPageCommitVisible(
                    view: WebView,
                    url: String?
                ) {
                    super.onPageCommitVisible(view, url)
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: android.webkit.SslErrorHandler,
                    error: android.webkit.SslError
                ) {
                    handler.cancel()
                }

                override fun onReceivedLoginRequest(
                    view: WebView,
                    handler: android.webkit.HttpAuthHandler,
                    host: String?,
                    realm: String?
                ) {
                    handler.cancel()
                }

                override fun onDownloadStart(
                    view: WebView,
                    url: String?,
                    userAgent: String?,
                    contentDisposition: String?,
                    mimetype: String?,
                    contentLength: Long
                ) {
                    super.onDownloadStart(view, url, userAgent, contentDisposition, mimetype, contentLength)
                }

                override fun doUpdateVisitedList(
                    view: WebView,
                    visited: Boolean
                ) {
                    super.doUpdateVisitedList(view, visited)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (url.startsWith("file:///android_asset/") || url.startsWith("file:///android_res/raw/")) {
                        return null
                    }
                    return null
                }

                override fun onLoadResource(view: WebView?, url: String?) {
                    super.onLoadResource(view, url)
                }

                override fun onScaleChanged(
                    view: WebView,
                    oldScale: Float,
                    newScale: Float
                ) {
                    super.onScaleChanged(view, oldScale, newScale)
                }

                override fun onCustomViewStarted(view: WebView?) {
                    super.onCustomViewStarted(view)
                }

                override fun onCustomViewStopped(view: WebView?) {
                    super.onCustomViewStopped(view)
                }

                override fun onKeyShortcut(
                    view: WebView,
                    keyCode: Int,
                    event: KeyEvent?
                ): Boolean {
                    return super.onKeyShortcut(view, keyCode, event)
                }

                override fun onSafeBrowsingHit(
                    view: WebView,
                    request: WebResourceRequest,
                    threatType: Int,
                    handler: android.webkit.SafeBrowsingResponseHandler
                ) {
                    handler.cancel()
                }

                override fun onFormResubmission(
                    view: WebView,
                    info: android.webkit.FormUrlCallback
                ) {
                    info.proceed()
                }

                override fun onTooManyRedirects(
                    view: WebView,
                    cancel: Boolean,
                    url: String?
                ) {
                    super.onTooManyRedirects(view, cancel, url)
                }

                override fun onReceivedAbortedError(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedAbortedError(view, errorCode, description, failingUrl)
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?
                ) {
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    super.onPageFinished(view, url)
                }
            }
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

private object PrivacyHtmlLoader {
    private const val ASSETS_HTML = "privacy.html"
    private const val RAW_HTML = "privacy_policy.html"

    fun loadLocalHtml(context: android.content.Context): String? {
        // Try assets first, then res/raw.
        return try {
            loadFromAssets(context, ASSETS_HTML)
        } catch (_: Exception) {
            try {
                loadFromRaw(context, RAW_HTML)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun loadFromAssets(context: android.content.Context, name: String): String {
        val stream = ContextCompat.openInputStream(context, "assets/$name")
            ?: throw IllegalStateException("Missing assets/$name")
        return stream.bufferedReader().readText().also { stream.close() }
    }

    private fun loadFromRaw(context: android.content.Context, resName: String): String {
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            .takeIf { it != 0 }
            ?: throw IllegalStateException("Missing raw/$resName")
        return context.resources.openRawResource(resId).bufferedReader().readText()
    }
}
