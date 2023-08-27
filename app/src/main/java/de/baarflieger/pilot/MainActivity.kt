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

var loadURL = "https://pilot.baar-flieger.de/app/piloten"
var versionName = ""

@Suppress("UNUSED_VARIABLE") //for splashScreen
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        val splashScreen = installSplashScreen()

        // Get the package info
        val packageInfo: PackageInfo = packageManager.getPackageInfoCompat(packageName, 0)
        // Get the version name
        versionName = packageInfo.versionName

        super.onCreate(savedInstanceState)
        setContent {
            WebViewPage(loadURL)
        }
    }

    private fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
        }

}

//@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPage(url: String){

    var currentUrl by rememberSaveable { mutableStateOf(url) }
    val webViewState: MutableState<WebView?> = remember { mutableStateOf(null) }

    val context  = LocalContext.current
    //val configuration = LocalConfiguration.current

    var isLoading by remember { mutableStateOf(false) }
    val isOffline = remember { mutableStateOf(false) }

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
                        //Log.d("onReceivedError",error)

                        loadURL = if (isOnline(context)) {
                            "file:///android_asset/error.html"
                        } else {
                            "file:///android_asset/error.html"
                        }

                        isOffline.value = true

                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        //openFullDialogCustom.value = true
                        isLoading = true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        if (url == "https://pilot.baar-flieger.de/app/piloten") {
                            val cookieManager = CookieManager.getInstance()
                            val cookies = cookieManager.getCookie(url)

                            if (cookies != null && cookies.contains("budibase:auth")) {
                                // Cookie is present, do nothing
                            } else {
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
                            //view.loadUrl("https://pilot.baar-flieger.de/app/piloten")

                            Toast.makeText(view.context, "Baar-Flieger Android App Version $versionName", Toast.LENGTH_SHORT).show()

                            true

                        } else {

                            if (request.url.toString().startsWith("https://pilot.baar-flieger.de/files/signed/prod-budi-app-assets/app_eacc1f2abe4746f2b0d98888da2aa3cf/attachments", ignoreCase = true)) {

                                try {

                                    val intent = Intent(Intent.ACTION_VIEW)
                                    intent.setDataAndType(Uri.parse(request.url.toString()), "application/pdf")
                                    //intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                    view.context.startActivity(intent)

                                } catch (e: ActivityNotFoundException) {
                                    // Handle exception if no activity can handle the download intent
                                    Toast.makeText(view.context, "Es wurde keine Standard Applikation für die PDF Darstellung gefunden.", Toast.LENGTH_SHORT).show()

                                }

                                return true
                            }

                            return false

                        }
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {

                        // check for pdf asset access -> redirect to PDF intent
                        if (request.url.toString().startsWith("https://pilot.baar-flieger.de/files/signed/prod-budi-app-assets/app_eacc1f2abe4746f2b0d98888da2aa3cf/attachments", ignoreCase = true)) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(Uri.parse(request.url.toString()), "application/pdf")
                                //intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                view.context.startActivity(intent)

                                return super.shouldInterceptRequest(view, request)

                            } catch (e: ActivityNotFoundException) {
                                // Handle exception if no activity can handle the download intent
                                Toast.makeText(view.context, "Es wurde keine Standard Applikation für die PDF Darstellung gefunden.", Toast.LENGTH_SHORT).show()
                            }
                        }

                        return super.shouldInterceptRequest(view, request)

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

            // Load the URL if the WebView is not null
            webViewState.value?.loadUrl(currentUrl)
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
                    .align(Alignment.Center)
            )
        }
    }

    if (isOffline.value) {
        WebViewPage(loadURL)
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
