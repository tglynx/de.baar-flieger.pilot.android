package de.baarflieger.pilot

//import androidx.compose.foundation.Image
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.window.Dialog
//import androidx.compose.ui.window.DialogProperties

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

//import com.google.accompanist.systemuicontroller.rememberSystemUiController
//import de.baarflieger.pilot.ui.theme.BaarFliegerPrimary40
//import de.baarflieger.pilot.ui.theme.BaarFliegerSecondary40


var loadURL = "https://pilot.baar-flieger.de/app/benutzer"

@Suppress("UNUSED_VARIABLE") //for splashScreen
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        setContent {
            WebViewPage(loadURL)
        }
    }
    }

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPage(url: String){

    val context  = LocalContext.current
    val configuration = LocalConfiguration.current

    var isLoading by remember { mutableStateOf(false) }
    val isOffline = remember { mutableStateOf(false) }

    //Custom Dialog (legacy loading indicator)
/*    val openFullDialogCustom = remember { mutableStateOf(false) }

    if (openFullDialogCustom.value) {

        val systemUiController = rememberSystemUiController()

        systemUiController.setSystemBarsColor(
                color = BaarFliegerPrimary40
        )

        // Custom Dialog
        Dialog(
            onDismissRequest = {
                openFullDialogCustom.value = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false // experimental
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {


                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(200.dp)
                            .fillMaxWidth(),

                        )

                    Spacer(modifier = Modifier.height(20.dp))
                    //.........................Text: title
                    Text(
                        text = "Daten werden übertragen!",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .fillMaxWidth(),
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        //color = MaterialTheme.colorScheme.primary,
                        color = BaarFliegerPrimary40
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    //.........................Text : description
                    Text(
                        text = "Bitte warten...",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 10.dp, start = 25.dp, end = 25.dp)
                            .fillMaxWidth(),
                        letterSpacing = 3.sp,
                        style = MaterialTheme.typography.bodyLarge,
                        //color = MaterialTheme.colorScheme.primary,
                        color = BaarFliegerSecondary40
                    )
                    //.........................Spacer
                    Spacer(modifier = Modifier.height(24.dp))

                }

            }
        }

    }*/

    when (configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> {
            // Toast.makeText(context, "landscape", Toast.LENGTH_SHORT).show()
        }
        else -> {
            //Toast.makeText(context, "portrait", Toast.LENGTH_SHORT).show()
        }
    }

    // Adding a WebView inside AndroidView
    // with layout as full screen
    AndroidView(
        factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                webViewClient = WebViewClient()

                // to play video on a web view
                settings.javaScriptEnabled = true

                // to enable local storage for budibase client library
                settings.domStorageEnabled = true

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
                        //openFullDialogCustom.value = false
                        isLoading = false
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {

                        // check for backend access link -> redirect to cockpit home
                        return if (request.url.toString() == "https://pilot.baar-flieger.de/builder/apps") {
                            view.loadUrl("https://pilot.baar-flieger.de/app/piloten")
                            true
                        } else {

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
                                Toast.makeText(view.context, "No app found to handle the download", Toast.LENGTH_SHORT).show()
                            }
                        }

                        return super.shouldInterceptRequest(view, request)

                    }

                }

                loadUrl(url)

            }

        }
    )

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
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
