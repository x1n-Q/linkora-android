package top.linkora.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import top.linkora.mobile.auth.LinkoraAuthViewModel
import top.linkora.mobile.connector.TunnelConnectorService
import top.linkora.mobile.connector.TunnelConnectorStatus
import top.linkora.mobile.node.NodeRuntimeViewModel
import top.linkora.mobile.project.ProjectKind
import top.linkora.mobile.project.ProjectWorkspaceState
import top.linkora.mobile.project.ProjectWorkspaceViewModel
import top.linkora.mobile.publish.PublishViewModel
import top.linkora.mobile.ui.LinkoraApp
import top.linkora.mobile.ui.theme.LinkoraTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: LinkoraAuthViewModel by viewModels()
    private val projectViewModel: ProjectWorkspaceViewModel by viewModels()
    private val publishViewModel: PublishViewModel by viewModels()
    private val nodeRuntimeViewModel: NodeRuntimeViewModel by viewModels()
    private var pendingNodeAction: (() -> Unit)? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingNodeAction?.invoke()
        pendingNodeAction = null
    }
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
        val activeWorkspace =
            (projectViewModel.uiState.value as? ProjectWorkspaceState.Ready)?.workspace
        if (activeWorkspace?.kind == ProjectKind.Node) {
            nodeRuntimeViewModel.stop()
        }
        TunnelConnectorService.stop(this)
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
            val nodeRuntimeState by nodeRuntimeViewModel.uiState.collectAsState()

            LaunchedEffect(authViewModel) {
                authViewModel.browserRequests.collect(::openWebSignIn)
            }

            LinkoraTheme {
                LinkoraApp(
                    authState = authState,
                    projectState = projectState,
                    publishState = publishState,
                    connectorState = connectorState,
                    nodeRuntimeState = nodeRuntimeState,
                    onStartSignIn = authViewModel::startSignIn,
                    onReopenBrowser = authViewModel::reopenBrowser,
                    onCancelSignIn = authViewModel::cancelSignIn,
                    onSignOut = authViewModel::signOut,
                    onRetryProject = projectViewModel::restoreProject,
                    onPrepareProject = { projectId ->
                        val activeWorkspace =
                            (projectViewModel.uiState.value as? ProjectWorkspaceState.Ready)?.workspace
                        if (activeWorkspace?.kind == ProjectKind.Node) {
                            nodeRuntimeViewModel.stop()
                        }
                        TunnelConnectorService.stop(this)
                        projectViewModel.prepareProject(projectId)
                    },
                    onLoadPublish = publishViewModel::load,
                    onClaimHostname = publishViewModel::claim,
                    onConnectHostname = publishViewModel::connect,
                    onOpenFiles = ::chooseProjectFolder,
                    onStopConnector = { TunnelConnectorService.stop(this) },
                    onStartNode = {
                        runWithRuntimeNotification(nodeRuntimeViewModel::start)
                    },
                    onRestartNode = {
                        runWithRuntimeNotification(nodeRuntimeViewModel::restart)
                    },
                    onStopNode = nodeRuntimeViewModel::stop,
                    onInstallNodeDependencies = {
                        runWithRuntimeNotification(nodeRuntimeViewModel::installDependencies)
                    },
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

    private fun runWithRuntimeNotification(action: () -> Unit) {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            action()
            return
        }

        pendingNodeAction = action
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun chooseProjectFolder() {
        val folderUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/LinkoraProjects",
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
