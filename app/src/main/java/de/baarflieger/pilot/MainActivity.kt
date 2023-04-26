package de.baarflieger.pilot

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope

import de.baarflieger.pilot.ui.theme.BaarFliegerPrimary40
import de.baarflieger.pilot.ui.theme.BaarFliegerSecondary40
import kotlinx.coroutines.delay


var loadURL = "https://pilot.baar-flieger.de/app/benutzer"

@SuppressLint("CustomSplashScreen")
class SplashActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        lifecycleScope.launchWhenCreated {
            delay(1000)

            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebViewPage(loadURL)
            //WebViewPage("file:///android_asset/shop.html") //OFFLINE
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPage(url: String){

    val openFullDialogCustom = remember { mutableStateOf(false) }
    if (openFullDialogCustom.value) {

        // Dialog function
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

    }
    //..........................................................................


    val context  = LocalContext.current


    //The Configuration object represents all of the current configurations, not just the ones that have changed.
    val configuration = LocalConfiguration.current

    when (configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> {
            // Toast.makeText(context, "landscape", Toast.LENGTH_SHORT).show()
        }
        else -> {
            //Toast.makeText(context, "portrait", Toast.LENGTH_SHORT).show()
        }
    }

    val mutableStateTrigger = remember { mutableStateOf(false) }

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
                settings.userAgentString = System.getProperty("http.agent") //Dalvik/2.1.0 (Linux; U; Android 11; M2012K11I Build/RKQ1.201112.002)

                settings.useWideViewPort = true


                webViewClient = object : WebViewClient() {


                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        //Log.d("test001","error")

                        loadURL = if(isOnline(context)){
                            "file:///android_asset/error.html" // other error
                        } else{
                            "file:///android_asset/error.html" // no internet
                        }

                        mutableStateTrigger.value = true

                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        openFullDialogCustom.value = true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        openFullDialogCustom.value = false
                    }

                }

                loadUrl(url)

            }
        })

    if (mutableStateTrigger.value) {
        WebViewPage(loadURL)
    }

}


fun isOnline(context: Context): Boolean {
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