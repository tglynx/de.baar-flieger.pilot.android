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

var loadURL = "https://pilot.baar-flieger.de/app/piloten"
var versionName = ""

@Suppress("UNUSED_VARIABLE") //for splashScreen
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        val splashScreen = installSplashScreen()

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Get the package info
        val packageInfo: PackageInfo = packageManager.getPackageInfoCompat(packageName, 0)
        // Get the version name
        versionName = packageInfo.versionName

        super.onCreate(savedInstanceState)
        setContent {
            // Apply insets as padding to the root composable
            WebViewPage(
                url = loadURL,
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPage(url: String, modifier: Modifier = Modifier){

    var currentUrl by rememberSaveable { mutableStateOf(url) }
    val webViewState: MutableState<WebView?> = remember { mutableStateOf(null) }

    val context  = LocalContext.current

    var isLoading by remember { mutableStateOf(false) }
    val isOffline = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Adding a WebView inside AndroidView
        // with layout as full screen
        AndroidView(
            factory = {
                WebView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // to play video on a web view
                    settings.javaScriptEnabled = true

                    // to enable local storage for budibase client library
                    settings.domStorageEnabled = true

                    // Enable caching
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    // to verify that the client requesting your web page is actually your Android app.
                    settings.userAgentString =
                        System.getProperty("http.agent") //Dalvik/2.1.0 (Linux; U; Android 11; M2012K11I Build/RKQ1.201112.002)

                    settings.useWideViewPort = true

                    webViewClient = object : WebViewClient() {

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)

                            val newUrl = if (isOnline(context)) {
                                "file:///android_asset/error.html"
                            } else {
                                "file:///android_asset/error.html"
                            }
                            // To avoid recursive loading, only load if the url is different
                            if (currentUrl != newUrl) {
                                currentUrl = newUrl
                                isOffline.value = true
                            }
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)

                            if (url == "https://pilot.baar-flieger.de/app/piloten") {
                                val cookieManager = CookieManager.getInstance()
                                val cookies = cookieManager.getCookie(url)

                                if (cookies == null || !cookies.contains("budibase:auth")) {
                                    // Cookie is not present, redirect to the desired URL
                                    view?.loadUrl("https://pilot.baar-flieger.de/app/benutzer")
                                }
                            }

                            currentUrl = view?.url ?: currentUrl
                            isLoading = false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {

                            // check for backend access link -> redirect to cockpit home
                            return if (request.url.toString().contains("/builder/")) {
                                Toast.makeText(view.context, "Baar-Flieger Android App Version $versionName", Toast.LENGTH_SHORT).show()
                                true
                            } else if (request.url.toString().startsWith("https://pilot.baar-flieger.de/files/signed/prod-budi-app-assets/app_eacc1f2abe4746f2b0d98888da2aa3cf/attachments", ignoreCase = true)) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW)
                                    intent.setDataAndType(Uri.parse(request.url.toString()), "application/pdf")
                                    view.context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(view.context, "Es wurde keine Standard Applikation für die PDF Darstellung gefunden.", Toast.LENGTH_SHORT).show()
                                }
                                true
                            } else {
                                false
                            }
                        }

                        override fun onPageCommitVisible(view: WebView, url: String?) {
                            super.onPageCommitVisible(view, url)

                            // Inject JavaScript code to continuously monitor and modify links (remove target and download tags)
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
                // Store the updated WebView instance in the state
                webViewState.value = webView
                // Check if the url is different to avoid reloading the same page
                if (webView.url != currentUrl) {
                    webView.loadUrl(currentUrl)
                }
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = colorResource(R.color.BaarFliegerPrimary),
                    modifier = Modifier
                        .size(50.dp)
                )
            }
        }

        if (isOffline.value) {
            // This will recompose and trigger the update block of AndroidView
            // which will then load the offline url.
            LaunchedEffect(Unit) {
                isOffline.value = false // Reset for next time
            }
        }
    }
}

private fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // For 29 api or above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return false
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->    true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->   true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->   true
            else ->     false
        }
    }
    // For below 29 api
    else {
        @Suppress("DEPRECATION")
        if (connectivityManager.activeNetworkInfo != null && connectivityManager.activeNetworkInfo!!.isConnectedOrConnecting) {
            return true
        }
    }
    return false
}
