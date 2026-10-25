package top.linkora.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import top.linkora.mobile.ui.LinkoraApp
import top.linkora.mobile.ui.theme.LinkoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LinkoraTheme {
                LinkoraApp(onOpenWebSignIn = ::openWebSignIn)
            }
        }
    }

    private fun openWebSignIn() {
        val signInUri = Uri.Builder()
            .scheme("https")
            .authority("linkora.top")
            .path("login")
            .appendQueryParameter("source", "android")
            .build()

        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
            .launchUrl(this, signInUri)
    }
}
