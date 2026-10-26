package top.linkora.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import top.linkora.mobile.auth.LinkoraAuthViewModel
import top.linkora.mobile.ui.LinkoraApp
import top.linkora.mobile.ui.theme.LinkoraTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: LinkoraAuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val authState by authViewModel.uiState.collectAsState()

            LaunchedEffect(authViewModel) {
                authViewModel.browserRequests.collect(::openWebSignIn)
            }

            LinkoraTheme {
                LinkoraApp(
                    authState = authState,
                    onStartSignIn = authViewModel::startSignIn,
                    onReopenBrowser = authViewModel::reopenBrowser,
                    onCancelSignIn = authViewModel::cancelSignIn,
                    onSignOut = authViewModel::signOut,
                )
            }
        }
    }

    private fun openWebSignIn(url: String) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
            .launchUrl(this, Uri.parse(url))
    }
}
