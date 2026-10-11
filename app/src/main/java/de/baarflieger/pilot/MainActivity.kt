package de.baarflieger.pilot

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat

private const val PILOTEN_URL = "https://pilot.baar-flieger.de/app/piloten"
private const val BENUTZER_URL = "https://pilot.baar-flieger.de/app/benutzer"
private const val ERROR_URL = "file:///android_asset/error.html"

class MainActivity : ComponentActivity() {

    companion object {
        var versionName = ""
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        versionName = try {
            packageManager.getPackageInfoCompat(packageName, 0).versionName ?: "n/a"
        } catch (_: PackageManager.NameNotFoundException) {
            "not found"
        }

        setContent {
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                // window.statusBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            }

            WebViewPage(
                url = PILOTEN_URL,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            )
        }
    }

    private fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
        }
}

@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnRenderProcessGone")
@Composable
private fun WebViewPage(url: String, modifier: Modifier = Modifier) {

    var currentUrl by rememberSaveable { mutableStateOf<String?>(url) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewKey by remember { mutableIntStateOf(0) }

    Box(modifier = modifier) {
        key(webViewKey) {
            AndroidView(
                factory = {
                    WebView(it).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = System.getProperty("http.agent")
                            useWideViewPort = true
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail.didCrash() else "unknown"
                                android.util.Log.e("WebViewError", "Render process gone. Did crash: $didCrash")
                                if (view.isAttachedToWindow) {
                                    (view.parent as? ViewGroup)?.removeView(view)
                                }
                                view.destroy()

                                currentUrl = ERROR_URL
                                webViewKey++
                                return true
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                // Log the error details to understand the cause
                                android.util.Log.e("WebViewError", "Error loading '${request?.url}': ${error?.errorCode} - ${error?.description}")
                                if (currentUrl != ERROR_URL) {
                                    currentUrl = ERROR_URL
                                }
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)

                                if (url == PILOTEN_URL) {
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    if (cookies == null || !cookies.contains("budibase:auth")) {
                                        view?.loadUrl(BENUTZER_URL)
                                    }
                                }
                                currentUrl = view?.url
                                isLoading = false
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val requestUrl = request.url.toString()
                                return when {
                                    requestUrl.contains("/builder/") -> {
                                        Toast.makeText(view.context, "Baar-Flieger Android App Version ${MainActivity.versionName}", Toast.LENGTH_SHORT).show()
                                        true
                                    }
                                    requestUrl.startsWith("https://pilot.baar-flieger.de/files/signed/prod-budi-app-assets/app_eacc1f2abe4746f2b0d98888da2aa3cf/attachments", ignoreCase = true) -> {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(requestUrl.toUri(), "application/pdf")
                                            }
                                            view.context.startActivity(intent)
                                        } catch (_: ActivityNotFoundException) {
                                            Toast.makeText(view.context, "Es wurde keine Standard Applikation für die PDF Darstellung gefunden.", Toast.LENGTH_SHORT).show()
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }

                            override fun onPageCommitVisible(view: WebView, url: String?) {
                                super.onPageCommitVisible(view, url)
                                view.loadUrl(
                                    "javascript:(function() {" +
                                            "   var observer = new MutationObserver(function(mutations) {" +
                                            "       mutations.forEach(function(mutation) {" +
                                            "           if (mutation.addedNodes) {" +
                                            "               for (var i = 0; i < mutation.addedNodes.length; i++) {" +
                                            "                   var node = mutation.addedNodes[i];" +
                                            "                   if (node.tagName === 'A') {" +
                                            "                       node.removeAttribute('target');" +
                                            "                       node.removeAttribute('download');" +
                                            "                   }" +
                                            "               }" +
                                            "           }" +
                                            "       });" +
                                            "   });" +
                                            "   observer.observe(document.body, { childList: true, subtree: true });" +
                                            "})()"
                                )
                            }
                        }
                        currentUrl?.let { safeUrl -> loadUrl(safeUrl) }
                    }
                },
                update = { webView ->
                    if (webView.url != currentUrl) {
                        currentUrl?.let {
                            webView.loadUrl(it)
                        }
                    }
                }
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = colorResource(R.color.BaarFliegerPrimary),
                modifier = Modifier
                    .size(50.dp)
                    .align(Alignment.Center)
            )
        }
    }
}
