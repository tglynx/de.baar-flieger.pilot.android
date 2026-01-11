package de.baarflieger.pilot

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        versionName = try {
            packageManager.getPackageInfoCompat(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }

        setContent {
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
            getPackageInfo(packageName, flags)
        }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewPage(url: String, modifier: Modifier = Modifier) {

    var currentUrl by rememberSaveable { mutableStateOf(url) }
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = modifier) {
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
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            if (isOnline(context)) {
                                currentUrl = ERROR_URL
                            } else {
                                currentUrl = ERROR_URL
                            }
                            view?.loadUrl(currentUrl)
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
                            currentUrl = view?.url ?: currentUrl
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
                                            setDataAndType(Uri.parse(requestUrl), "application/pdf")
                                        }
                                        view.context.startActivity(intent)
                                    } catch (e: ActivityNotFoundException) {
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
                    loadUrl(currentUrl)
                }
            },
            update = { webView ->
                if (webView.url != currentUrl) {
                    webView.loadUrl(currentUrl)
                }
            }
        )

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

private fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
