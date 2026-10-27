package top.linkora.mobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import top.linkora.mobile.auth.LinkoraAuthViewModel
import top.linkora.mobile.connector.TunnelConnectorService
import top.linkora.mobile.connector.TunnelConnectorStatus
import top.linkora.mobile.project.ProjectWorkspaceViewModel
import top.linkora.mobile.publish.PublishViewModel
import top.linkora.mobile.ui.LinkoraApp
import top.linkora.mobile.ui.theme.LinkoraTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: LinkoraAuthViewModel by viewModels()
    private val projectViewModel: ProjectWorkspaceViewModel by viewModels()
    private val publishViewModel: PublishViewModel by viewModels()
    private val projectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        val grantedFlags = result.data?.flags?.and(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        ) ?: 0
        if (grantedFlags != 0) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, grantedFlags)
            }
        }
        projectViewModel.selectProjectFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val authState by authViewModel.uiState.collectAsState()
            val projectState by projectViewModel.uiState.collectAsState()
            val publishState by publishViewModel.uiState.collectAsState()
            val connectorState by TunnelConnectorStatus.state.collectAsState()

            LaunchedEffect(authViewModel) {
                authViewModel.browserRequests.collect(::openWebSignIn)
            }

            LinkoraTheme {
                LinkoraApp(
                    authState = authState,
                    projectState = projectState,
                    publishState = publishState,
                    connectorState = connectorState,
                    onStartSignIn = authViewModel::startSignIn,
                    onReopenBrowser = authViewModel::reopenBrowser,
                    onCancelSignIn = authViewModel::cancelSignIn,
                    onSignOut = authViewModel::signOut,
                    onRetryProject = projectViewModel::prepareStarterProject,
                    onLoadPublish = publishViewModel::load,
                    onClaimHostname = publishViewModel::claim,
                    onConnectHostname = publishViewModel::connect,
                    onOpenFiles = ::chooseProjectFolder,
                    onStopConnector = { TunnelConnectorService.stop(this) },
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

    private fun chooseProjectFolder() {
        val folderUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/LinkoraProjects/linkora-starter",
        )
        projectFolderLauncher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            },
        )
    }
}
