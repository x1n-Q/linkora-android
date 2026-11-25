package top.linkora.mobile.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.linkora.mobile.R
import top.linkora.mobile.auth.AuthUiState
import top.linkora.mobile.connector.TunnelConnectorState
import top.linkora.mobile.database.DatabaseExplorerMode
import top.linkora.mobile.database.DatabaseExplorerState
import top.linkora.mobile.mariadb.MariaDbRuntimePhase
import top.linkora.mobile.mariadb.MariaDbRuntimeState
import top.linkora.mobile.model.ProjectTemplate
import top.linkora.mobile.model.projectTemplates
import top.linkora.mobile.node.NodeRuntimePhase
import top.linkora.mobile.node.NodeRuntimeState
import top.linkora.mobile.php.PhpRuntimePhase
import top.linkora.mobile.php.PhpRuntimeState
import top.linkora.mobile.project.ProjectWorkspace
import top.linkora.mobile.project.ProjectKind
import top.linkora.mobile.project.ProjectWorkspaceManager
import top.linkora.mobile.project.ProjectWorkspaceState
import top.linkora.mobile.publish.ManagedHostname
import top.linkora.mobile.publish.PublishUiState
import top.linkora.mobile.runtime.RuntimePackState
import top.linkora.mobile.runtime.RuntimePackUiState
import top.linkora.mobile.runtime.RuntimePackInspection
import top.linkora.mobile.runtime.RuntimeDownloadPhase
import top.linkora.mobile.runtime.RuntimeDownloadState
import top.linkora.mobile.runtime.RuntimeDownloadUiState
import top.linkora.mobile.settings.AppMode
import top.linkora.mobile.settings.HostingAccess
import top.linkora.mobile.ui.theme.ActionSurface
import top.linkora.mobile.ui.theme.Charcoal
import top.linkora.mobile.ui.theme.DisabledSurface
import top.linkora.mobile.ui.theme.DisabledText
import top.linkora.mobile.ui.theme.DustyOlive
import top.linkora.mobile.ui.theme.MutedText
import top.linkora.mobile.ui.theme.Oxblood
import top.linkora.mobile.ui.theme.OxbloodSoft
import top.linkora.mobile.ui.theme.PaleStone
import top.linkora.mobile.ui.theme.PaperRaised
import top.linkora.mobile.ui.theme.StrongBorder
import top.linkora.mobile.ui.theme.WarmPaper
import top.linkora.mobile.ui.database.DatabaseExplorerScreen

private enum class OnboardingStep {
    ServiceMode,
    ProjectType,
    WebSignIn,
    Workspace,
    Preview,
    Publish,
}

private enum class CleanRoute {
    Main,
    Project,
    ProjectPicker,
    WebSignIn,
    Preview,
    ManagedPublish,
    StandalonePublish,
    RuntimeManager,
    DatabaseExplorer,
}

private enum class MainDestination {
    Projects,
    Activity,
    Settings,
}

private enum class ProjectSection {
    Overview,
    Logs,
    Publish,
}

private enum class ActionStyle {
    Primary,
    Secondary,
    Neutral,
    Destructive,
}

@Composable
fun LinkoraApp(
    appMode: AppMode?,
    hostingAccess: HostingAccess,
    localNetworkUrl: String?,
    authState: AuthUiState,
    projectState: ProjectWorkspaceState,
    publishState: PublishUiState,
    connectorState: TunnelConnectorState,
    nodeRuntimeState: NodeRuntimeState,
    phpRuntimeState: PhpRuntimeState,
    mariaDbRuntimeState: MariaDbRuntimeState,
    databaseExplorerState: DatabaseExplorerState,
    runtimePackState: RuntimePackUiState,
    runtimeDownloadState: RuntimeDownloadUiState,
    onSelectAppMode: (AppMode) -> Unit,
    onSelectHostingAccess: (HostingAccess) -> Unit,
    onStartSignIn: () -> Unit,
    onReopenBrowser: () -> Unit,
    onCancelSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetryProject: () -> Unit,
    onPrepareProject: (String) -> Unit,
    onLoadPublish: () -> Unit,
    onClaimHostname: (String, String) -> Unit,
    onConnectHostname: (ManagedHostname) -> Unit,
    onConnectOwnDomain: (String, String) -> Unit,
    onOpenFiles: () -> Unit,
    onUpdateLaunchCommand: (String) -> Unit,
    onStopConnector: () -> Unit,
    onStartNode: () -> Unit,
    onRestartNode: () -> Unit,
    onStopNode: () -> Unit,
    onInstallNodeDependencies: () -> Unit,
    onStartPhp: () -> Unit,
    onRestartPhp: () -> Unit,
    onStopPhp: () -> Unit,
    onStartMariaDb: () -> Unit,
    onRestartMariaDb: () -> Unit,
    onStopMariaDb: () -> Unit,
    onDownloadRuntime: (String) -> Unit,
    onCancelRuntimeDownload: (String) -> Unit,
    onInstallRuntime: (String) -> Unit,
    onRemoveRuntimeInstaller: (String) -> Unit,
    onRefreshDatabaseExplorer: () -> Unit,
    onSetDatabaseExplorerMode: (DatabaseExplorerMode) -> Unit,
    onShowDatabaseTables: () -> Unit,
    onSelectDatabaseTable: (String) -> Unit,
    onUpdateDatabaseQuery: (String) -> Unit,
    onRunDatabaseQuery: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(CleanRoute.Main) }
    var returnRoute by rememberSaveable { mutableStateOf(CleanRoute.Main) }
    var destination by rememberSaveable { mutableStateOf(MainDestination.Projects) }
    var projectSection by rememberSaveable { mutableStateOf(ProjectSection.Overview) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>("static") }
    var openFolderAfterPrepare by rememberSaveable { mutableStateOf(false) }
    var signInForPublishing by rememberSaveable { mutableStateOf(true) }
    val readyProject = projectState as? ProjectWorkspaceState.Ready
    val workspace = readyProject?.workspace
    val selectedProject = projectTemplates.firstOrNull {
        it.id == (selectedProjectId ?: workspace?.kind?.id)
    } ?: projectTemplates.first()

    fun openRuntimeManager(from: CleanRoute) {
        returnRoute = from
        route = CleanRoute.RuntimeManager
    }

    LaunchedEffect(appMode) {
        if (appMode == null) onSelectAppMode(AppMode.Standalone)
    }

    LaunchedEffect(route, authState) {
        if (route == CleanRoute.WebSignIn && authState is AuthUiState.SignedIn) {
            if (signInForPublishing) {
                onLoadPublish()
                route = CleanRoute.ManagedPublish
            } else {
                destination = MainDestination.Settings
                route = CleanRoute.Main
            }
        }
    }

    LaunchedEffect(projectState, openFolderAfterPrepare) {
        if (openFolderAfterPrepare && projectState is ProjectWorkspaceState.Ready) {
            openFolderAfterPrepare = false
            route = CleanRoute.Project
            onOpenFiles()
        }
    }

    BackHandler(enabled = route != CleanRoute.Main || destination != MainDestination.Projects) {
        when (route) {
            CleanRoute.Main -> destination = MainDestination.Projects
            CleanRoute.Project -> route = CleanRoute.Main
            CleanRoute.ProjectPicker -> route = CleanRoute.Main
            CleanRoute.WebSignIn -> {
                projectSection = ProjectSection.Publish
                route = CleanRoute.Project
            }
            CleanRoute.Preview -> route = CleanRoute.Project
            CleanRoute.ManagedPublish,
            CleanRoute.StandalonePublish,
            -> {
                projectSection = ProjectSection.Publish
                route = CleanRoute.Project
            }
            CleanRoute.RuntimeManager -> route = returnRoute
            CleanRoute.DatabaseExplorer -> route = CleanRoute.Project
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmPaper,
        contentColor = Charcoal,
    ) {
        when (route) {
            CleanRoute.Preview -> workspace?.let {
                ProjectPreviewScreen(
                    workspace = it,
                    onBack = { route = CleanRoute.Project },
                )
            }

            CleanRoute.RuntimeManager -> {
                RuntimeManagerScreen(
                    packState = runtimePackState,
                    downloadState = runtimeDownloadState,
                    onBack = { route = returnRoute },
                    onDownload = onDownloadRuntime,
                    onCancel = onCancelRuntimeDownload,
                    onInstall = onInstallRuntime,
                    onRemoveInstaller = onRemoveRuntimeInstaller,
                )
            }

            CleanRoute.DatabaseExplorer -> {
                DatabaseExplorerScreen(
                    state = databaseExplorerState,
                    onBack = { route = CleanRoute.Project },
                    onRefresh = onRefreshDatabaseExplorer,
                    onSetMode = onSetDatabaseExplorerMode,
                    onShowTables = onShowDatabaseTables,
                    onSelectTable = onSelectDatabaseTable,
                    onUpdateQuery = onUpdateDatabaseQuery,
                    onRunQuery = onRunDatabaseQuery,
                )
            }

            CleanRoute.ManagedPublish -> PublishScreen(
                state = publishState,
                connectorState = connectorState,
                onClaimHostname = onClaimHostname,
                onConnectHostname = { hostname ->
                    if (runtimePackState.connector.state == RuntimePackState.Ready) {
                        onConnectHostname(hostname)
                    } else {
                        openRuntimeManager(CleanRoute.ManagedPublish)
                    }
                },
                onRetry = onLoadPublish,
                onStopConnector = onStopConnector,
                onBack = {
                    projectSection = ProjectSection.Publish
                    route = CleanRoute.Project
                },
            )

            CleanRoute.StandalonePublish -> StandalonePublishScreen(
                connectorState = connectorState,
                connectorReady = runtimePackState.connector.state == RuntimePackState.Ready,
                hostingAccess = hostingAccess,
                localNetworkUrl = localNetworkUrl,
                onSelectHostingAccess = onSelectHostingAccess,
                onConnect = onConnectOwnDomain,
                onInstallConnector = { openRuntimeManager(CleanRoute.StandalonePublish) },
                onStopConnector = onStopConnector,
                onBack = {
                    projectSection = ProjectSection.Publish
                    route = CleanRoute.Project
                },
            )

            else -> Scaffold(
                containerColor = WarmPaper,
                topBar = {
                    CleanAppBar(
                        route = route,
                        destination = destination,
                        onBack = {
                            route = when (route) {
                                CleanRoute.Project -> CleanRoute.Main
                                CleanRoute.ProjectPicker -> CleanRoute.Main
                                CleanRoute.WebSignIn -> CleanRoute.Project
                                else -> CleanRoute.Main
                            }
                        },
                        onAddProject = { route = CleanRoute.ProjectPicker },
                    )
                },
                bottomBar = {
                    if (route in setOf(CleanRoute.Main, CleanRoute.Project)) {
                        CleanBottomNavigation(
                            selected = if (route == CleanRoute.Project) {
                                MainDestination.Projects
                            } else {
                                destination
                            },
                            onSelect = {
                                destination = it
                                route = CleanRoute.Main
                            },
                        )
                    }
                },
            ) { scaffoldPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                ) {
                    when (route) {
                        CleanRoute.Main -> when (destination) {
                            MainDestination.Projects -> CleanProjectsScreen(
                                projectState = projectState,
                                nodeRuntimeState = nodeRuntimeState,
                                phpRuntimeState = phpRuntimeState,
                                onOpenProject = { route = CleanRoute.Project },
                                onNewProject = { route = CleanRoute.ProjectPicker },
                                onOpenFolder = {
                                    openFolderAfterPrepare = true
                                    route = CleanRoute.ProjectPicker
                                },
                                onRetryProject = onRetryProject,
                            )
                            MainDestination.Activity -> CleanActivityScreen(
                                workspace = workspace,
                                nodeRuntimeState = nodeRuntimeState,
                                phpRuntimeState = phpRuntimeState,
                                mariaDbRuntimeState = mariaDbRuntimeState,
                                connectorState = connectorState,
                            )
                            MainDestination.Settings -> CleanSettingsScreen(
                                authState = authState,
                                runtimePackState = runtimePackState,
                                onOpenRuntimes = { openRuntimeManager(CleanRoute.Main) },
                                onSignIn = {
                                    signInForPublishing = false
                                    onSelectAppMode(AppMode.LinkoraManaged)
                                    route = CleanRoute.WebSignIn
                                },
                                onSignOut = onSignOut,
                            )
                        }

                        CleanRoute.ProjectPicker -> CleanProjectPickerScreen(
                            selectedProjectId = selectedProjectId,
                            runtimePackState = runtimePackState,
                            openFolderAfterPrepare = openFolderAfterPrepare,
                            onSelectProject = { selectedProjectId = it.id },
                            onContinue = {
                                if (projectNeedsRuntime(selectedProjectId, runtimePackState)) {
                                    openRuntimeManager(CleanRoute.ProjectPicker)
                                } else {
                                    onPrepareProject(requireNotNull(selectedProjectId))
                                    if (!openFolderAfterPrepare) route = CleanRoute.Project
                                }
                            },
                        )

                        CleanRoute.WebSignIn -> WebSignInScreen(
                            selectedProject = selectedProject,
                            runtimePackState = runtimePackState,
                            authState = authState,
                            projectState = projectState,
                            onStartSignIn = onStartSignIn,
                            onReopenBrowser = onReopenBrowser,
                            onCancelSignIn = {
                                onCancelSignIn()
                                route = if (signInForPublishing) {
                                    CleanRoute.Project
                                } else {
                                    CleanRoute.Main
                                }
                            },
                            onSignOut = onSignOut,
                            onRetryProject = onRetryProject,
                            onOpenWorkspace = {
                                if (signInForPublishing) {
                                    onLoadPublish()
                                    route = CleanRoute.ManagedPublish
                                } else {
                                    destination = MainDestination.Settings
                                    route = CleanRoute.Main
                                }
                            },
                            onChangeProject = { route = CleanRoute.ProjectPicker },
                        )

                        CleanRoute.Project -> workspace?.let {
                            CleanProjectScreen(
                                workspace = it,
                                notice = readyProject.notice,
                                section = projectSection,
                                hostingAccess = hostingAccess,
                                localNetworkUrl = localNetworkUrl,
                                authState = authState,
                                connectorState = connectorState,
                                nodeRuntimeState = nodeRuntimeState,
                                phpRuntimeState = phpRuntimeState,
                                mariaDbRuntimeState = mariaDbRuntimeState,
                                projectRuntimePackState = when (it.kind) {
                                    ProjectKind.Static -> RuntimePackState.Ready
                                    ProjectKind.Node -> runtimePackState.node.state
                                    ProjectKind.Php -> runtimePackState.php.state
                                },
                                mariaDbPackState = runtimePackState.mariaDb.state,
                                onSectionChange = { projectSection = it },
                                onPreview = { route = CleanRoute.Preview },
                                onOpenFiles = onOpenFiles,
                                onUpdateLaunchCommand = onUpdateLaunchCommand,
                                onSelectHostingAccess = onSelectHostingAccess,
                                onUseLinkora = {
                                    onSelectAppMode(AppMode.LinkoraManaged)
                                    if (authState is AuthUiState.SignedIn) {
                                        onLoadPublish()
                                        route = CleanRoute.ManagedPublish
                                    } else {
                                        signInForPublishing = true
                                        route = CleanRoute.WebSignIn
                                    }
                                },
                                connectorReady = runtimePackState.connector.state == RuntimePackState.Ready,
                                onConnectOwnDomain = onConnectOwnDomain,
                                onInstallConnector = { openRuntimeManager(CleanRoute.Project) },
                                onStopConnector = onStopConnector,
                                onStartNode = onStartNode,
                                onRestartNode = onRestartNode,
                                onStopNode = onStopNode,
                                onInstallNodeDependencies = onInstallNodeDependencies,
                                onStartPhp = onStartPhp,
                                onRestartPhp = onRestartPhp,
                                onStopPhp = onStopPhp,
                                onStartMariaDb = onStartMariaDb,
                                onRestartMariaDb = onRestartMariaDb,
                                onStopMariaDb = onStopMariaDb,
                                onOpenDatabaseExplorer = {
                                    onRefreshDatabaseExplorer()
                                    route = CleanRoute.DatabaseExplorer
                                },
                                onManageRuntimes = { openRuntimeManager(CleanRoute.Project) },
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanAppBar(
    route: CleanRoute,
    destination: MainDestination,
    onBack: () -> Unit,
    onAddProject: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (route == CleanRoute.Main) {
                Image(
                    painter = painterResource(R.drawable.ic_linkora_wordmark),
                    contentDescription = "Linkora",
                    modifier = Modifier.size(width = 112.dp, height = 34.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (destination == MainDestination.Projects) {
                    IconButton(onClick = onAddProject) {
                        Icon(Icons.Rounded.Add, contentDescription = "New project")
                    }
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = when (route) {
                        CleanRoute.Project -> "Project"
                        CleanRoute.ProjectPicker -> "New project"
                        CleanRoute.WebSignIn -> "Linkora sign in"
                        else -> "Linkora"
                    },
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun CleanBottomNavigation(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(68.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            CleanNavigationItem(
                label = "Projects",
                icon = Icons.Rounded.HomeWork,
                selected = selected == MainDestination.Projects,
                onClick = { onSelect(MainDestination.Projects) },
                modifier = Modifier.weight(1f),
            )
            CleanNavigationItem(
                label = "Activity",
                icon = Icons.Rounded.Timeline,
                selected = selected == MainDestination.Activity,
                onClick = { onSelect(MainDestination.Activity) },
                modifier = Modifier.weight(1f),
            )
            CleanNavigationItem(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                selected = selected == MainDestination.Settings,
                onClick = { onSelect(MainDestination.Settings) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CleanNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        color = if (selected) OxbloodSoft else Color.Transparent,
        contentColor = if (selected) Oxblood else MutedText,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CleanProjectsScreen(
    projectState: ProjectWorkspaceState,
    nodeRuntimeState: NodeRuntimeState,
    phpRuntimeState: PhpRuntimeState,
    onOpenProject: () -> Unit,
    onNewProject: () -> Unit,
    onOpenFolder: () -> Unit,
    onRetryProject: () -> Unit,
) {
    val workspace = (projectState as? ProjectWorkspaceState.Ready)?.workspace
    val running = when (workspace?.kind) {
        ProjectKind.Static -> true
        ProjectKind.Node -> nodeRuntimeState.isRunning
        ProjectKind.Php -> phpRuntimeState.isRunning
        null -> false
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Projects", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Run and publish from this phone.",
                        modifier = Modifier.padding(top = 5.dp),
                        color = MutedText,
                    )
                }
                if (running) CleanStatusBadge("1 running", true)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CleanCommandButton(
                    label = "New project",
                    icon = Icons.Rounded.Add,
                    onClick = onNewProject,
                    modifier = Modifier.weight(1f),
                )
                CleanCommandButton(
                    label = "Open folder",
                    icon = Icons.Rounded.FolderOpen,
                    onClick = onOpenFolder,
                    modifier = Modifier.weight(1f),
                    secondary = true,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Active project", style = MaterialTheme.typography.headlineSmall)
                if (workspace != null) Text("On this phone", color = MutedText, fontSize = 12.sp)
            }
        }
        item {
            when (projectState) {
                ProjectWorkspaceState.Preparing -> CleanMessagePanel("Preparing project...")
                is ProjectWorkspaceState.Error -> CleanMessagePanel(
                    message = projectState.message,
                    action = "Retry",
                    onAction = onRetryProject,
                )
                is ProjectWorkspaceState.Ready -> CleanProjectCard(
                    workspace = projectState.workspace,
                    running = running,
                    onClick = onOpenProject,
                )
            }
        }
    }
}

@Composable
private fun CleanProjectCard(
    workspace: ProjectWorkspace,
    running: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = PaperRaised,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = OxbloodSoft,
                contentColor = Oxblood,
                shape = RoundedCornerShape(7.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when (workspace.kind) {
                            ProjectKind.Static -> "HTML"
                            ProjectKind.Node -> "NODE"
                            ProjectKind.Php -> "PHP"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    workspace.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = workspace.visibleLocation,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MutedText,
                    fontSize = 12.sp,
                )
            }
            CleanStatusBadge(if (running) "Running" else "Stopped", running)
        }
    }
}

@Composable
private fun CleanProjectPickerScreen(
    selectedProjectId: String?,
    runtimePackState: RuntimePackUiState,
    openFolderAfterPrepare: Boolean,
    onSelectProject: (ProjectTemplate) -> Unit,
    onContinue: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = if (openFolderAfterPrepare) "What kind of project is in the folder?" else "What do you want to run?",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = if (openFolderAfterPrepare) {
                    "This helps Linkora choose the correct runtime before opening Android Files."
                } else {
                    "Choose a starter. You can replace its files at any time."
                },
                modifier = Modifier.padding(top = 7.dp),
                color = MutedText,
            )
        }
        projectTemplates.filter { it.available }.forEach { template ->
            item(key = template.id) {
                val selected = selectedProjectId == template.id
                val ready = !projectNeedsRuntime(template.id, runtimePackState)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = { onSelectProject(template) },
                            role = Role.RadioButton,
                        ),
                    color = if (selected) OxbloodSoft else PaperRaised,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) Oxblood else PaleStone),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            color = if (selected) PaperRaised else OxbloodSoft,
                            shape = RoundedCornerShape(7.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    template.code,
                                    color = Oxblood,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(template.name, fontWeight = FontWeight.Bold)
                            Text(
                                template.description,
                                modifier = Modifier.padding(top = 3.dp),
                                color = MutedText,
                                fontSize = 12.sp,
                            )
                        }
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Oxblood,
                                unselectedColor = StrongBorder,
                            ),
                        )
                    }
                }
                if (!ready && selected) {
                    Text(
                        "The runtime will be installed first.",
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item {
            ActionButton(
                label = when {
                    selectedProjectId == null -> "Choose a project"
                    projectNeedsRuntime(selectedProjectId, runtimePackState) -> "Install runtime"
                    openFolderAfterPrepare -> "Choose folder"
                    else -> "Create project"
                },
                onClick = onContinue,
                enabled = selectedProjectId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CleanActivityScreen(
    workspace: ProjectWorkspace?,
    nodeRuntimeState: NodeRuntimeState,
    phpRuntimeState: PhpRuntimeState,
    mariaDbRuntimeState: MariaDbRuntimeState,
    connectorState: TunnelConnectorState,
) {
    val projectStatus = when (workspace?.kind) {
        ProjectKind.Static -> "Available locally"
        ProjectKind.Node -> nodeRuntimeState.message
        ProjectKind.Php -> phpRuntimeState.message
        null -> "No active project"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("Activity", style = MaterialTheme.typography.headlineLarge)
            Text("Current project and service state.", modifier = Modifier.padding(top = 5.dp), color = MutedText)
        }
        item {
            CleanActivityRow(
                icon = Icons.Rounded.Code,
                title = workspace?.name ?: "Project",
                message = projectStatus,
                active = when (workspace?.kind) {
                    ProjectKind.Static -> true
                    ProjectKind.Node -> nodeRuntimeState.isRunning
                    ProjectKind.Php -> phpRuntimeState.isRunning
                    null -> false
                },
            )
        }
        item {
            CleanActivityRow(
                icon = Icons.Rounded.Storage,
                title = "Database",
                message = mariaDbRuntimeState.message,
                active = mariaDbRuntimeState.isRunning,
            )
        }
        item {
            val connectorMessage = when (connectorState) {
                is TunnelConnectorState.Starting -> "Connecting ${connectorState.hostname}"
                is TunnelConnectorState.Running -> "${connectorState.hostname} is online"
                is TunnelConnectorState.Reconnecting -> "Reconnecting ${connectorState.hostname}"
                is TunnelConnectorState.Error -> connectorState.message
                TunnelConnectorState.Stopped -> "No public connection"
            }
            CleanActivityRow(
                icon = Icons.Rounded.Public,
                title = "Public connection",
                message = connectorMessage,
                active = connectorState is TunnelConnectorState.Running,
            )
        }
    }
}

@Composable
private fun CleanActivityRow(
    icon: ImageVector,
    title: String,
    message: String,
    active: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(40.dp),
            color = if (active) Color(0xFFE7F3EC) else ActionSurface,
            contentColor = if (active) DustyOlive else MutedText,
            shape = RoundedCornerShape(7.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                message,
                modifier = Modifier.padding(top = 4.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CleanSettingsScreen(
    authState: AuthUiState,
    runtimePackState: RuntimePackUiState,
    onOpenRuntimes: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Text("App, runtime, and account options.", modifier = Modifier.padding(top = 5.dp), color = MutedText)
        }
        item { Text("Runtimes", style = MaterialTheme.typography.headlineSmall) }
        item {
            CleanListGroup {
                CleanSettingsRow(
                    icon = Icons.Rounded.Code,
                    title = "Node.js",
                    subtitle = runtimePackState.node.message,
                    value = runtimeLabel(runtimePackState.node.state),
                    onClick = onOpenRuntimes,
                )
                CleanSettingsRow(
                    icon = Icons.Rounded.Terminal,
                    title = "PHP",
                    subtitle = runtimePackState.php.message,
                    value = runtimeLabel(runtimePackState.php.state),
                    onClick = onOpenRuntimes,
                )
                CleanSettingsRow(
                    icon = Icons.Rounded.Storage,
                    title = "MariaDB",
                    subtitle = "Optional local database",
                    value = runtimeLabel(runtimePackState.mariaDb.state),
                    onClick = onOpenRuntimes,
                    last = true,
                )
            }
        }
        item { Text("Linkora account", style = MaterialTheme.typography.headlineSmall) }
        item {
            val signedIn = authState as? AuthUiState.SignedIn
            CleanListGroup {
                CleanSettingsRow(
                    icon = Icons.Rounded.AccountCircle,
                    title = signedIn?.session?.email ?: "Not signed in",
                    subtitle = if (signedIn == null) {
                        "Only needed for a Linkora address"
                    } else {
                        "Linkora hostname access is available"
                    },
                    value = if (signedIn == null) "Sign in" else "Sign out",
                    onClick = if (signedIn == null) onSignIn else onSignOut,
                    last = true,
                )
            }
        }
        item { Text("General", style = MaterialTheme.typography.headlineSmall) }
        item {
            CleanListGroup {
                CleanSettingsRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Runtime notification",
                    subtitle = "Shows running projects and memory",
                    value = "On",
                    onClick = null,
                )
                CleanSettingsRow(
                    icon = Icons.Rounded.Info,
                    title = "About Linkora",
                    subtitle = "Android project runtime",
                    value = "0.7.0",
                    onClick = null,
                    last = true,
                )
            }
        }
    }
}

private fun runtimeLabel(state: RuntimePackState): String = when (state) {
    RuntimePackState.Checking -> "Checking"
    RuntimePackState.Ready -> "Ready"
    RuntimePackState.Missing -> "Install"
    RuntimePackState.Invalid -> "Repair"
    RuntimePackState.Incompatible -> "Unavailable"
}

@Composable
private fun CleanProjectScreen(
    workspace: ProjectWorkspace,
    notice: String?,
    section: ProjectSection,
    hostingAccess: HostingAccess,
    localNetworkUrl: String?,
    authState: AuthUiState,
    connectorState: TunnelConnectorState,
    nodeRuntimeState: NodeRuntimeState,
    phpRuntimeState: PhpRuntimeState,
    mariaDbRuntimeState: MariaDbRuntimeState,
    projectRuntimePackState: RuntimePackState,
    mariaDbPackState: RuntimePackState,
    onSectionChange: (ProjectSection) -> Unit,
    onPreview: () -> Unit,
    onOpenFiles: () -> Unit,
    onUpdateLaunchCommand: (String) -> Unit,
    onSelectHostingAccess: (HostingAccess) -> Unit,
    onUseLinkora: () -> Unit,
    connectorReady: Boolean,
    onConnectOwnDomain: (String, String) -> Unit,
    onInstallConnector: () -> Unit,
    onStopConnector: () -> Unit,
    onStartNode: () -> Unit,
    onRestartNode: () -> Unit,
    onStopNode: () -> Unit,
    onInstallNodeDependencies: () -> Unit,
    onStartPhp: () -> Unit,
    onRestartPhp: () -> Unit,
    onStopPhp: () -> Unit,
    onStartMariaDb: () -> Unit,
    onRestartMariaDb: () -> Unit,
    onStopMariaDb: () -> Unit,
    onOpenDatabaseExplorer: () -> Unit,
    onManageRuntimes: () -> Unit,
) {
    val running = when (workspace.kind) {
        ProjectKind.Static -> true
        ProjectKind.Node -> nodeRuntimeState.isRunning
        ProjectKind.Php -> phpRuntimeState.isRunning
    }
    val busy = when (workspace.kind) {
        ProjectKind.Static -> false
        ProjectKind.Node -> nodeRuntimeState.isBusy
        ProjectKind.Php -> phpRuntimeState.isBusy
    }
    val runtimeReady = projectRuntimePackState == RuntimePackState.Ready
    val logs = when (workspace.kind) {
        ProjectKind.Static -> listOf("Static project is available at ${workspace.localUrl}")
        ProjectKind.Node -> nodeRuntimeState.logs
        ProjectKind.Php -> phpRuntimeState.logs
    }
    val memory = when (workspace.kind) {
        ProjectKind.Static -> null
        ProjectKind.Node -> nodeRuntimeState.memoryMb
        ProjectKind.Php -> phpRuntimeState.memoryMb
    }
    val uptime = when (workspace.kind) {
        ProjectKind.Static -> null
        ProjectKind.Node -> nodeRuntimeState.uptimeSeconds
        ProjectKind.Php -> phpRuntimeState.uptimeSeconds
    }
    val startProject = when (workspace.kind) {
        ProjectKind.Static -> onPreview
        ProjectKind.Node -> onStartNode
        ProjectKind.Php -> onStartPhp
    }
    val stopProject = when (workspace.kind) {
        ProjectKind.Static -> ({})
        ProjectKind.Node -> onStopNode
        ProjectKind.Php -> onStopPhp
    }
    val restartProject = when (workspace.kind) {
        ProjectKind.Static -> ({})
        ProjectKind.Node -> onRestartNode
        ProjectKind.Php -> onRestartPhp
    }
    var advancedExpanded by rememberSaveable(workspace.name) { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (workspace.kind) {
                            ProjectKind.Static -> "HTML project"
                            ProjectKind.Node -> "Node.js project"
                            ProjectKind.Php -> "PHP project"
                        },
                        color = MutedText,
                        fontSize = 13.sp,
                    )
                    Text(
                        workspace.name,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
                CleanStatusBadge(if (running) "Running" else "Stopped", running)
            }
        }
        notice?.let { item { CleanMessagePanel(it) } }
        item {
            CleanProjectTabs(selected = section, onSelect = onSectionChange)
        }
        when (section) {
            ProjectSection.Overview -> {
                item {
                    CleanRuntimeBand(
                        workspace = workspace,
                        running = running,
                        busy = busy,
                        runtimeReady = runtimeReady,
                        hostingAccess = hostingAccess,
                        localNetworkUrl = localNetworkUrl,
                        onPrimary = if (running) onPreview else startProject,
                        onStop = stopProject,
                        onManageRuntimes = onManageRuntimes,
                    )
                }
                item {
                    CleanMetricRow(
                        uptime = uptime,
                        memory = memory,
                        runtime = when (workspace.kind) {
                            ProjectKind.Static -> "Built in"
                            ProjectKind.Node -> "Node 24"
                            ProjectKind.Php -> "PHP 8.4"
                        },
                    )
                }
                item { Text("Project", style = MaterialTheme.typography.headlineSmall) }
                item {
                    CleanListGroup {
                        CleanActionRow(
                            icon = Icons.Rounded.Folder,
                            title = "Files",
                            subtitle = workspace.visibleLocation,
                            onClick = onOpenFiles,
                        )
                        CleanActionRow(
                            icon = Icons.Rounded.Terminal,
                            title = "Runtime logs",
                            subtitle = if (logs.isEmpty()) "No output yet" else "${logs.size} recent lines",
                            onClick = { onSectionChange(ProjectSection.Logs) },
                        )
                        if (workspace.kind != ProjectKind.Static) {
                            CleanActionRow(
                                icon = Icons.Rounded.Storage,
                                title = "Database",
                                subtitle = when {
                                    mariaDbPackState != RuntimePackState.Ready -> "Not installed"
                                    mariaDbRuntimeState.isRunning -> "Running"
                                    else -> "Not enabled"
                                },
                                value = when {
                                    mariaDbPackState != RuntimePackState.Ready -> "Install"
                                    mariaDbRuntimeState.isRunning -> "Open"
                                    else -> "Enable"
                                },
                                onClick = when {
                                    mariaDbPackState != RuntimePackState.Ready -> onManageRuntimes
                                    mariaDbRuntimeState.isRunning -> onOpenDatabaseExplorer
                                    else -> onStartMariaDb
                                },
                                last = true,
                            )
                        }
                    }
                }
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded },
                        color = PaperRaised,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PaleStone),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Advanced", fontWeight = FontWeight.Bold)
                            Icon(
                                if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                            )
                        }
                    }
                }
                if (advancedExpanded) {
                    if (workspace.kind != ProjectKind.Static) {
                        item {
                            StartCommandEditor(
                                workspace = workspace,
                                enabled = !running && !busy,
                                onSave = onUpdateLaunchCommand,
                            )
                        }
                    }
                    if (workspace.kind == ProjectKind.Node && workspace.hasDependencies) {
                        item {
                            ActionButton(
                                label = "Check npm packages",
                                onClick = onInstallNodeDependencies,
                                enabled = !running && !busy,
                                modifier = Modifier.fillMaxWidth(),
                                style = ActionStyle.Neutral,
                            )
                        }
                    }
                    if (running && workspace.kind != ProjectKind.Static) {
                        item {
                            ActionButton(
                                label = "Restart project",
                                onClick = restartProject,
                                modifier = Modifier.fillMaxWidth(),
                                style = ActionStyle.Neutral,
                            )
                        }
                    }
                    if (mariaDbRuntimeState.isRunning) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton(
                                    label = "Restart database",
                                    onClick = onRestartMariaDb,
                                    modifier = Modifier.weight(1f),
                                    style = ActionStyle.Neutral,
                                )
                                ActionButton(
                                    label = "Stop database",
                                    onClick = onStopMariaDb,
                                    modifier = Modifier.weight(1f),
                                    style = ActionStyle.Destructive,
                                )
                            }
                        }
                    }
                }
            }

            ProjectSection.Logs -> item {
                CleanLogsPanel(logs)
            }

            ProjectSection.Publish -> item {
                CleanPublishPanel(
                    hostingAccess = hostingAccess,
                    localNetworkUrl = localNetworkUrl,
                    authState = authState,
                    connectorState = connectorState,
                    onSelectHostingAccess = onSelectHostingAccess,
                    onUseLinkora = onUseLinkora,
                    connectorReady = connectorReady,
                    onConnectOwnDomain = onConnectOwnDomain,
                    onInstallConnector = onInstallConnector,
                    onStopConnector = onStopConnector,
                )
            }
        }
    }
}

@Composable
private fun CleanCommandButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) PaperRaised else Oxblood,
            contentColor = if (secondary) Charcoal else PaperRaised,
        ),
        border = if (secondary) BorderStroke(1.dp, StrongBorder) else null,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
    }
}

@Composable
private fun CleanStatusBadge(label: String, active: Boolean) {
    Surface(
        color = if (active) Color(0xFFE7F3EC) else ActionSurface,
        contentColor = if (active) DustyOlive else MutedText,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (active) DustyOlive else MutedText,
                        shape = RoundedCornerShape(50),
                    ),
            )
            Text(
                label,
                modifier = Modifier.padding(start = 6.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CleanMessagePanel(
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(message, color = MutedText, style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) {
                ActionButton(
                    label = action,
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    style = ActionStyle.Neutral,
                )
            }
        }
    }
}

@Composable
private fun CleanListGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Column(content = content)
    }
}

@Composable
private fun CleanSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: (() -> Unit)?,
    last: Boolean = false,
) {
    val rowModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Column(modifier = rowModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CleanRowIcon(icon)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 11.dp),
            ) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(value, color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (!last) HorizontalDivider(color = PaleStone)
    }
}

@Composable
private fun CleanActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    value: String? = null,
    last: Boolean = false,
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CleanRowIcon(icon)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 11.dp),
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (value == null) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MutedText)
            } else {
                Surface(
                    color = PaperRaised,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, StrongBorder),
                ) {
                    Text(
                        value,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (!last) HorizontalDivider(color = PaleStone)
    }
}

@Composable
private fun CleanRowIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        color = OxbloodSoft,
        contentColor = Oxblood,
        shape = RoundedCornerShape(7.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun CleanProjectTabs(
    selected: ProjectSection,
    onSelect: (ProjectSection) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DisabledSurface,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            ProjectSection.entries.forEach { section ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clickable { onSelect(section) },
                    color = if (selected == section) PaperRaised else Color.Transparent,
                    shape = RoundedCornerShape(5.dp),
                    shadowElevation = if (selected == section) 1.dp else 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            section.name,
                            color = if (selected == section) Charcoal else MutedText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanRuntimeBand(
    workspace: ProjectWorkspace,
    running: Boolean,
    busy: Boolean,
    runtimeReady: Boolean,
    hostingAccess: HostingAccess,
    localNetworkUrl: String?,
    onPrimary: () -> Unit,
    onStop: () -> Unit,
    onManageRuntimes: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF22352C),
        contentColor = Color(0xFFF5FFF8),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            busy -> "Working..."
                            running -> "Project is running"
                            runtimeReady -> "Project is stopped"
                            else -> "Runtime required"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = when {
                            running && hostingAccess == HostingAccess.LocalNetwork ->
                                localNetworkUrl ?: workspace.localUrl
                            running -> workspace.localUrl
                            else -> "Ready to start"
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color(0xFFBFD1C5),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
                CleanStatusBadge(if (running) "Online" else "Offline", running)
            }
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = if (runtimeReady) onPrimary else onManageRuntimes,
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Oxblood,
                        contentColor = PaperRaised,
                    ),
                ) {
                    Icon(
                        when {
                            !runtimeReady -> Icons.Rounded.Cloud
                            running -> Icons.AutoMirrored.Rounded.OpenInNew
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = null,
                    )
                    Text(
                        text = when {
                            !runtimeReady -> "Install runtime"
                            running -> "Open preview"
                            else -> "Start project"
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (running && workspace.kind != ProjectKind.Static) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier
                            .width(104.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaperRaised,
                            contentColor = Oxblood,
                        ),
                        border = BorderStroke(1.dp, Oxblood),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Stop", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanMetricRow(uptime: Long?, memory: Long?, runtime: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Row {
            CleanMetric("Uptime", uptime?.let(::formatRuntimeDuration) ?: "--", Modifier.weight(1f))
            CleanMetric("Memory", memory?.let { "$it MB" } ?: "--", Modifier.weight(1f), divider = true)
            CleanMetric("Runtime", runtime, Modifier.weight(1f), divider = true)
        }
    }
}

@Composable
private fun CleanMetric(
    label: String,
    value: String,
    modifier: Modifier,
    divider: Boolean = false,
) {
    Row(modifier = modifier) {
        if (divider) Box(modifier = Modifier.width(1.dp).height(64.dp).background(PaleStone))
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(label, color = MutedText, fontSize = 10.sp)
            Text(
                value,
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CleanLogsPanel(logs: List<String>) {
    Column {
        Text("Runtime logs", style = MaterialTheme.typography.headlineSmall)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            color = Color(0xFF1F2521),
            contentColor = Color(0xFFDCE9DF),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = if (logs.isEmpty()) "No runtime output yet." else logs.takeLast(100).joinToString("\n"),
                modifier = Modifier.padding(16.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun CleanPublishPanel(
    hostingAccess: HostingAccess,
    localNetworkUrl: String?,
    authState: AuthUiState,
    connectorState: TunnelConnectorState,
    onSelectHostingAccess: (HostingAccess) -> Unit,
    onUseLinkora: () -> Unit,
    connectorReady: Boolean,
    onConnectOwnDomain: (String, String) -> Unit,
    onInstallConnector: () -> Unit,
    onStopConnector: () -> Unit,
) {
    var publicSelected by rememberSaveable { mutableStateOf(false) }
    var ownDomainSelected by rememberSaveable { mutableStateOf(false) }
    var hostname by rememberSaveable { mutableStateOf("") }
    var tunnelToken by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Who can open it?", style = MaterialTheme.typography.headlineSmall)
        CleanPublishChoice(
            icon = Icons.Rounded.Smartphone,
            title = "This phone only",
            description = "Private local preview",
            selected = !publicSelected && hostingAccess == HostingAccess.DeviceOnly,
            onClick = {
                publicSelected = false
                onSelectHostingAccess(HostingAccess.DeviceOnly)
            },
        )
        CleanPublishChoice(
            icon = Icons.Rounded.Wifi,
            title = "Local network",
            description = localNetworkUrl ?: "Devices on the same Wi-Fi",
            selected = !publicSelected && hostingAccess == HostingAccess.LocalNetwork,
            onClick = {
                publicSelected = false
                onSelectHostingAccess(HostingAccess.LocalNetwork)
            },
        )
        CleanPublishChoice(
            icon = Icons.Rounded.Public,
            title = "Public URL",
            description = "Open from anywhere",
            selected = publicSelected,
            onClick = { publicSelected = true },
        )
        if (publicSelected) {
            Text(
                "Choose an address",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CleanProviderChoice(
                    title = "Linkora address",
                    description = if (authState is AuthUiState.SignedIn) {
                        "Use your Linkora account"
                    } else {
                        "Free managed hostname"
                    },
                    onClick = onUseLinkora,
                    modifier = Modifier.weight(1f),
                )
                CleanProviderChoice(
                    title = "My own domain",
                    description = "Use your Cloudflare tunnel",
                    onClick = { ownDomainSelected = !ownDomainSelected },
                    modifier = Modifier.weight(1f),
                    selected = ownDomainSelected,
                )
            }
            if (ownDomainSelected) {
                OutlinedTextField(
                    value = hostname,
                    onValueChange = {
                        hostname = it.lowercase().trim()
                        validationError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    label = { Text("Public hostname") },
                    placeholder = { Text("app.example.com") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tunnelToken,
                    onValueChange = {
                        tunnelToken = it.trim()
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cloudflare tunnel token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                validationError?.let {
                    Text(it, color = Oxblood, style = MaterialTheme.typography.bodyMedium)
                }
                ActionButton(
                    label = if (connectorReady) "Connect domain" else "Install connector",
                    onClick = {
                        if (!connectorReady) {
                            onInstallConnector()
                        } else {
                            when {
                                !STANDALONE_HOSTNAME_PATTERN.matches(hostname) ->
                                    validationError = "Enter a valid hostname such as app.example.com."
                                tunnelToken.length < 20 ->
                                    validationError = "Enter the tunnel token from Cloudflare."
                                else -> onConnectOwnDomain(hostname, tunnelToken)
                            }
                        }
                    },
                    enabled = connectorState !is TunnelConnectorState.Starting &&
                        connectorState !is TunnelConnectorState.Reconnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (connectorState is TunnelConnectorState.Running) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = Color(0xFFE7F3EC),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DustyOlive),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Language, contentDescription = null, tint = DustyOlive)
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text("Public connection online", fontWeight = FontWeight.Bold)
                        Text(connectorState.hostname, color = MutedText, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onStopConnector) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Stop public connection", tint = Oxblood)
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanPublishChoice(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        color = PaperRaised,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) Oxblood else PaleStone),
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Oxblood,
                    unselectedColor = StrongBorder,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(icon, contentDescription = null, tint = Charcoal)
        }
    }
}

@Composable
private fun CleanProviderChoice(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clickable(onClick = onClick),
        color = if (selected) OxbloodSoft else PaperRaised,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) Oxblood else PaleStone),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                description,
                modifier = Modifier.padding(top = 6.dp),
                color = MutedText,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun LinkoraHeader(onOpenRuntimes: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_linkora_wordmark),
                contentDescription = "Linkora",
                modifier = Modifier.size(width = 94.dp, height = 28.dp),
            )
            Box(modifier = Modifier.weight(1f))
            ActionButton(
                label = "Runtimes",
                onClick = onOpenRuntimes,
                style = ActionStyle.Neutral,
                compact = true,
            )
        }
    }
}

@Composable
private fun ServiceModeScreen(
    selectedMode: AppMode?,
    onContinue: (AppMode) -> Unit,
) {
    var selection by rememberSaveable(selectedMode) { mutableStateOf(selectedMode) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 24.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SectionLabel("Connection")
            Text(
                text = "Choose how you want to publish.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "You can change this later without changing your project files.",
                modifier = Modifier.padding(top = 8.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        item {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ServiceModeCard(
                    title = "Linkora managed",
                    description = "Sign in to claim a Linkora hostname and use managed publishing.",
                    code = "LINKORA",
                    selected = selection == AppMode.LinkoraManaged,
                    onSelect = { selection = AppMode.LinkoraManaged },
                )
                ServiceModeCard(
                    title = "Standalone",
                    description = "Run locally or connect your own domain without a Linkora account.",
                    code = "OWN DOMAIN",
                    selected = selection == AppMode.Standalone,
                    onSelect = { selection = AppMode.Standalone },
                )
            }
        }

        item {
            ActionButton(
                label = "Continue",
                onClick = { selection?.let(onContinue) },
                enabled = selection != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun ServiceModeCard(
    title: String,
    description: String,
    code: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (selected) Oxblood else PaleStone),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFF8ECEC) else PaperRaised,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = code,
                    modifier = Modifier.padding(top = 9.dp),
                    color = if (selected) Oxblood else DustyOlive,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Oxblood,
                    unselectedColor = StrongBorder,
                ),
            )
        }
    }
}

@Composable
private fun SetupProgress(currentStep: Int, appMode: AppMode) {
    val steps = if (appMode == AppMode.LinkoraManaged) {
        listOf("Project", "Account", "Run")
    } else {
        listOf("Project", "Run", "Publish")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            steps.forEachIndexed { index, label ->
                val stepNumber = index + 1
                val current = stepNumber == currentStep
                val complete = stepNumber < currentStep
                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (current) Oxblood else Color.Transparent,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stepNumber.toString(),
                            color = if (current) PaperRaised else if (complete) DustyOlive else MutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 6.dp),
                            color = if (current) PaperRaised else Charcoal,
                            fontSize = 12.sp,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeManagerScreen(
    packState: RuntimePackUiState,
    downloadState: RuntimeDownloadUiState,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onInstall: (String) -> Unit,
    onRemoveInstaller: (String) -> Unit,
) {
    val runtimes = listOf(
        packState.node to downloadState["node"],
        packState.php to downloadState["php"],
        packState.mariaDb to downloadState["mariadb"],
        packState.connector to downloadState["connector"],
    )
    val installedCount = runtimes.count { it.first.state == RuntimePackState.Ready }
    val deviceAbi = runtimes.firstOrNull()?.second?.artifact?.abi ?: "unknown"

    Scaffold(
        containerColor = WarmPaper,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PaperRaised,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(64.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Runtime manager",
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Runtime packs", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Add only what your projects need.",
                    modifier = Modifier.padding(top = 5.dp),
                    color = MutedText,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$installedCount of ${runtimes.size} installed",
                        color = if (installedCount == runtimes.size) DustyOlive else Charcoal,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = deviceAbi,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }

            item { Text("Project runtimes", style = MaterialTheme.typography.headlineSmall) }
            item {
                RuntimePackGroup(
                    runtimes = runtimes.take(2),
                    onDownload = onDownload,
                    onCancel = onCancel,
                    onInstall = onInstall,
                    onRemoveInstaller = onRemoveInstaller,
                )
            }

            item { Text("Add-ons", style = MaterialTheme.typography.headlineSmall) }
            item {
                RuntimePackGroup(
                    runtimes = runtimes.drop(2),
                    onDownload = onDownload,
                    onCancel = onCancel,
                    onInstall = onInstall,
                    onRemoveInstaller = onRemoveInstaller,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MutedText,
                    )
                    Text(
                        text = "Android manages downloads and installation. Downloads continue in the background.",
                        modifier = Modifier.padding(start = 9.dp),
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimePackGroup(
    runtimes: List<Pair<RuntimePackInspection, RuntimeDownloadState>>,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onInstall: (String) -> Unit,
    onRemoveInstaller: (String) -> Unit,
) {
    CleanListGroup {
        runtimes.forEachIndexed { index, (inspection, download) ->
            RuntimePackRow(
                inspection = inspection,
                download = download,
                onDownload = { onDownload(inspection.spec.id) },
                onCancel = { onCancel(inspection.spec.id) },
                onInstall = { onInstall(inspection.spec.id) },
                onRemoveInstaller = { onRemoveInstaller(inspection.spec.id) },
                last = index == runtimes.lastIndex,
            )
        }
    }
}

@Composable
private fun RuntimePackRow(
    inspection: RuntimePackInspection,
    download: RuntimeDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRemoveInstaller: () -> Unit,
    last: Boolean,
) {
    val installed = inspection.state == RuntimePackState.Ready
    val failed = inspection.state in setOf(
        RuntimePackState.Invalid,
        RuntimePackState.Incompatible,
    ) || download.phase == RuntimeDownloadPhase.Failed
    val showDownloadDetails = !installed || download.phase in setOf(
        RuntimeDownloadPhase.Downloading,
        RuntimeDownloadPhase.ReadyToInstall,
        RuntimeDownloadPhase.Installing,
        RuntimeDownloadPhase.Failed,
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CleanRowIcon(runtimeIcon(inspection.spec.id))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = runtimeTitle(inspection.spec.id),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = runtimePurpose(inspection.spec.id),
                        modifier = Modifier.padding(top = 2.dp),
                        color = MutedText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${inspection.spec.runtimeVersion}  ·  ${download.artifact.abi}",
                        modifier = Modifier.padding(top = 3.dp),
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
                RuntimeStatusBadge(
                    label = runtimeDownloadLabel(inspection, download),
                    error = failed,
                    active = installed,
                )
            }
        }

        if (showDownloadDetails) {
            Column(
                modifier = Modifier.padding(
                    start = 13.dp,
                    end = 13.dp,
                    bottom = 13.dp,
                ),
            ) {
                if (download.phase == RuntimeDownloadPhase.Downloading) {
                    val progress = download.progress
                    if (progress == null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Oxblood,
                            trackColor = PaleStone,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Oxblood,
                            trackColor = PaleStone,
                        )
                    }
                }

                Text(
                    text = runtimeDownloadMessage(inspection, download),
                    modifier = Modifier.padding(top = if (download.phase == RuntimeDownloadPhase.Downloading) 8.dp else 0.dp),
                    color = if (failed) Oxblood else MutedText,
                    fontSize = 12.sp,
                )

                when {
                    download.phase == RuntimeDownloadPhase.Unavailable -> ActionButton(
                        label = "Not available for this device",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        compact = true,
                    )
                    download.phase == RuntimeDownloadPhase.Downloading -> ActionButton(
                        label = "Cancel download",
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        style = ActionStyle.Neutral,
                        compact = true,
                    )
                    download.phase == RuntimeDownloadPhase.ReadyToInstall && !installed -> {
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton(
                                label = "Install",
                                onClick = onInstall,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                            ActionButton(
                                label = "Delete",
                                onClick = onRemoveInstaller,
                                modifier = Modifier.weight(1f),
                                style = ActionStyle.Neutral,
                                compact = true,
                            )
                        }
                    }
                    download.phase == RuntimeDownloadPhase.ReadyToInstall -> ActionButton(
                        label = "Delete saved installer",
                        onClick = onRemoveInstaller,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        style = ActionStyle.Neutral,
                        compact = true,
                    )
                    download.phase == RuntimeDownloadPhase.Installing -> ActionButton(
                        label = "Waiting for Android",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        compact = true,
                    )
                    !installed -> ActionButton(
                        label = if (failed) {
                            "Download again"
                        } else {
                            "Download ${formatDownloadSize(download.totalBytes)}"
                        },
                        onClick = onDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        compact = true,
                    )
                }
            }
        }

        if (!last) HorizontalDivider(color = PaleStone)
    }
}

private fun runtimeTitle(runtimeId: String): String = when (runtimeId) {
    "node" -> "Node.js"
    "php" -> "PHP"
    "mariadb" -> "MariaDB"
    "connector" -> "Public connector"
    else -> "Runtime"
}

private fun runtimeIcon(runtimeId: String): ImageVector = when (runtimeId) {
    "node" -> Icons.Rounded.Code
    "php" -> Icons.Rounded.Terminal
    "mariadb" -> Icons.Rounded.Storage
    "connector" -> Icons.Rounded.Cloud
    else -> Icons.Rounded.Code
}

private fun runtimePurpose(runtimeId: String): String = when (runtimeId) {
    "node" -> "JavaScript servers and npm"
    "php" -> "PHP websites and APIs"
    "mariadb" -> "MySQL-compatible database"
    "connector" -> "Public domain connection"
    else -> "Optional project runtime"
}

private fun runtimeDownloadLabel(
    inspection: RuntimePackInspection,
    download: RuntimeDownloadState,
): String = when {
    inspection.state == RuntimePackState.Ready -> "Ready"
    download.phase == RuntimeDownloadPhase.Downloading -> download.progress
        ?.let { "${(it * 100).toInt()}%" }
        ?: "Downloading"
    download.phase == RuntimeDownloadPhase.ReadyToInstall -> "Downloaded"
    download.phase == RuntimeDownloadPhase.Installing -> "Installing"
    download.phase == RuntimeDownloadPhase.Failed -> "Failed"
    inspection.state == RuntimePackState.Invalid -> "Repair"
    inspection.state == RuntimePackState.Incompatible ||
        download.phase == RuntimeDownloadPhase.Unavailable -> "Unavailable"
    inspection.state == RuntimePackState.Checking -> "Checking"
    else -> "Not installed"
}

private fun runtimeDownloadMessage(
    inspection: RuntimePackInspection,
    download: RuntimeDownloadState,
): String = when {
    inspection.state == RuntimePackState.Ready &&
        download.phase == RuntimeDownloadPhase.ReadyToInstall ->
        "Runtime installed. The saved installer can be deleted."
    download.phase == RuntimeDownloadPhase.Downloading -> download.message
    download.phase == RuntimeDownloadPhase.ReadyToInstall -> "Download complete. Android will ask you to approve installation."
    download.phase == RuntimeDownloadPhase.Installing -> download.message
    download.phase == RuntimeDownloadPhase.Failed -> download.message
    inspection.state == RuntimePackState.Invalid -> inspection.message
    inspection.state == RuntimePackState.Incompatible -> inspection.message
    download.phase == RuntimeDownloadPhase.Unavailable -> download.message
    else -> "Install this pack when a project needs ${runtimeTitle(inspection.spec.id)}."
}

private fun projectNeedsRuntime(
    projectId: String?,
    runtimePackState: RuntimePackUiState,
): Boolean = when (projectId) {
    "node" -> runtimePackState.node.state != RuntimePackState.Ready
    "php" -> runtimePackState.php.state != RuntimePackState.Ready
    else -> false
}

private fun formatDownloadSize(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

@Composable
private fun ProjectTypeScreen(
    appMode: AppMode,
    selectedProjectId: String?,
    runtimePackState: RuntimePackUiState,
    onSelectProject: (ProjectTemplate) -> Unit,
    onContinue: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SetupProgress(currentStep = 1, appMode = appMode)
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Choose project")
                Text(
                    text = "What do you want to run?",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Choose the option that matches the files you want to run on this phone.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            ProjectTemplateGrid(
                selectedProjectId = selectedProjectId,
                runtimePackState = runtimePackState,
                onSelectProject = onSelectProject,
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                ActionButton(
                    label = if (projectNeedsRuntime(selectedProjectId, runtimePackState)) {
                        "Open Runtime Manager"
                    } else {
                        "Continue with ${projectTemplates.firstOrNull { it.id == selectedProjectId }?.name ?: "project"}"
                    },
                    onClick = onContinue,
                    enabled = selectedProjectId != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (selectedProjectId == "php") {
                        "PHP uses the separately installed Linkora PHP Runtime Pack."
                    } else {
                        "You can select your existing project folder after setup."
                    },
                    modifier = Modifier.padding(top = 12.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ProjectTemplateGrid(
    selectedProjectId: String?,
    runtimePackState: RuntimePackUiState,
    onSelectProject: (ProjectTemplate) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 680.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                projectTemplates.filter { it.available }.chunked(2).forEach { columnProjects ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        columnProjects.forEach { project ->
                            ProjectTemplateCard(
                                project = project,
                                selected = selectedProjectId == project.id,
                                runtimePackState = runtimePackState,
                                onSelect = { onSelectProject(project) },
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                projectTemplates.filter { it.available }.forEach { project ->
                    ProjectTemplateCard(
                        project = project,
                        selected = selectedProjectId == project.id,
                        runtimePackState = runtimePackState,
                        onSelect = { onSelectProject(project) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectTemplateCard(
    project: ProjectTemplate,
    selected: Boolean,
    runtimePackState: RuntimePackUiState,
    onSelect: () -> Unit,
) {
    val selectable = project.available
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = { if (selectable) onSelect() },
                enabled = selectable,
                role = Role.RadioButton,
            ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (selected) Oxblood else PaleStone),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFF8ECEC) else PaperRaised,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, if (selected) Oxblood else PaleStone),
                color = WarmPaper,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = project.code,
                        color = if (selected) Oxblood else Charcoal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = project.description,
                    modifier = Modifier.padding(top = 3.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = when {
                        project.id == "php" &&
                            runtimePackState.php.state == RuntimePackState.Ready ->
                            "PHP 8.4 READY"
                        project.id == "php" &&
                            runtimePackState.php.state == RuntimePackState.Checking ->
                            "CHECKING PHP RUNTIME"
                        project.id == "php" -> "PHP RUNTIME REQUIRED"
                        project.id == "node" &&
                            runtimePackState.node.state == RuntimePackState.Ready ->
                            "NODE.JS READY"
                        project.id == "node" &&
                            runtimePackState.node.state == RuntimePackState.Checking ->
                            "CHECKING NODE.JS RUNTIME"
                        project.id == "node" -> "NODE.JS RUNTIME REQUIRED"
                        project.runtimePackage != null -> "RUNTIME REQUIRED"
                        else -> "NO RUNTIME NEEDED"
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    color = if (selectable) DustyOlive else MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            RadioButton(
                selected = selected,
                enabled = selectable,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Oxblood,
                    unselectedColor = StrongBorder,
                ),
            )
        }
    }
}

@Composable
private fun WebSignInScreen(
    selectedProject: ProjectTemplate,
    runtimePackState: RuntimePackUiState,
    authState: AuthUiState,
    projectState: ProjectWorkspaceState,
    onStartSignIn: () -> Unit,
    onReopenBrowser: () -> Unit,
    onCancelSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetryProject: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onChangeProject: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SetupProgress(currentStep = 2, appMode = AppMode.LinkoraManaged)
        }

        item {
            SectionLabel("Connect account")
            Text(
                text = "Sign in to Linkora.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Your browser handles the password. After approval, it returns you to this app automatically.",
                modifier = Modifier.padding(top = 8.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, PaleStone),
                colors = CardDefaults.cardColors(containerColor = PaperRaised),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Project to open")
                    Text(
                        text = selectedProject.name,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = selectedProject.description,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = PaleStone,
                    )
                    SummaryRow(
                        label = "Runtime",
                        value = selectedProject.runtimePackage ?: "Built in",
                    )
                    SummaryRow(
                        label = "Install status",
                        value = if (selectedProject.id == "node") {
                            when (runtimePackState.node.state) {
                                RuntimePackState.Ready -> "Verified runtime pack"
                                RuntimePackState.Invalid -> "Runtime pack rejected"
                                RuntimePackState.Incompatible -> "Runtime pack incompatible"
                                RuntimePackState.Missing -> "Node.js Runtime Pack required"
                                RuntimePackState.Checking -> "Checking runtime pack"
                            }
                        } else if (selectedProject.id == "php") {
                            when (runtimePackState.php.state) {
                                RuntimePackState.Ready -> "Verified PHP Runtime Pack"
                                RuntimePackState.Invalid -> "Runtime pack rejected"
                                RuntimePackState.Incompatible -> "Runtime pack incompatible"
                                RuntimePackState.Missing -> "PHP Runtime Pack required"
                                RuntimePackState.Checking -> "Checking runtime pack"
                            }
                        } else {
                            "Ready"
                        },
                    )
                }
            }
        }

        item {
            AuthenticationStatusCard(
                authState = authState,
                onReopenBrowser = onReopenBrowser,
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                ActionButton(
                    label = when (authState) {
                        AuthUiState.Restoring -> "Checking saved session..."
                        AuthUiState.Starting -> "Preparing secure sign-in..."
                        is AuthUiState.AwaitingApproval -> "Open approval page"
                        is AuthUiState.SignedIn -> when (projectState) {
                            ProjectWorkspaceState.Preparing -> "Preparing sample project..."
                            is ProjectWorkspaceState.Error -> "Retry project setup"
                            is ProjectWorkspaceState.Ready -> "Open local project"
                        }
                        else -> "Sign in with browser"
                    },
                    onClick = when {
                        authState is AuthUiState.SignedIn &&
                            projectState is ProjectWorkspaceState.Ready -> onOpenWorkspace
                        authState is AuthUiState.SignedIn &&
                            projectState is ProjectWorkspaceState.Error -> onRetryProject
                        authState is AuthUiState.AwaitingApproval -> onReopenBrowser
                        else -> onStartSignIn
                    },
                    enabled = authState !is AuthUiState.Starting &&
                        authState !is AuthUiState.Restoring &&
                        !(authState is AuthUiState.SignedIn &&
                            projectState is ProjectWorkspaceState.Preparing),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (authState is AuthUiState.AwaitingApproval) {
                    ActionButton(
                        label = "Cancel sign-in",
                        onClick = onCancelSignIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        style = ActionStyle.Destructive,
                    )
                }
                if (authState is AuthUiState.SignedIn) {
                    ActionButton(
                        label = "Sign out",
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        style = ActionStyle.Destructive,
                    )
                }
                ActionButton(
                    label = "Choose a different project",
                    onClick = onChangeProject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = ActionStyle.Neutral,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(
    appMode: AppMode,
    workspace: ProjectWorkspace,
    notice: String?,
    accountEmail: String?,
    nodeRuntimeState: NodeRuntimeState,
    phpRuntimeState: PhpRuntimeState,
    mariaDbRuntimeState: MariaDbRuntimeState,
    mariaDbPackState: RuntimePackState,
    onPreview: () -> Unit,
    onOpenFiles: () -> Unit,
    onUpdateLaunchCommand: (String) -> Unit,
    onAssignUrl: () -> Unit,
    onChangeProject: () -> Unit,
    onChangeMode: () -> Unit,
    onSignOut: () -> Unit,
    onStartNode: () -> Unit,
    onRestartNode: () -> Unit,
    onStopNode: () -> Unit,
    onInstallNodeDependencies: () -> Unit,
    onStartPhp: () -> Unit,
    onRestartPhp: () -> Unit,
    onStopPhp: () -> Unit,
    onStartMariaDb: () -> Unit,
    onRestartMariaDb: () -> Unit,
    onStopMariaDb: () -> Unit,
    onOpenDatabaseExplorer: () -> Unit,
    onManageRuntimes: () -> Unit,
) {
    var showNativeModuleWarning by rememberSaveable { mutableStateOf(false) }
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    val originReady = when (workspace.kind) {
        ProjectKind.Static -> true
        ProjectKind.Node -> nodeRuntimeState.isRunning
        ProjectKind.Php -> phpRuntimeState.isRunning
    }

    if (showNativeModuleWarning) {
        AlertDialog(
            onDismissRequest = { showNativeModuleWarning = false },
            title = { Text("Some packages may not support Android") },
            text = {
                Text(
                    if (workspace.sqliteCompatibilityDrivers.isNotEmpty()) {
                        "Linkora detected ${workspace.sqliteCompatibilityDrivers.joinToString()}. " +
                            "Their native Android binaries will be replaced inside the private " +
                            "runtime copy with Linkora adapters backed by Node.js SQLite. Common " +
                            "queries, prepared statements, and transactions are supported; advanced " +
                            "native APIs may not be. Your selected project files will not be changed."
                    } else {
                        "Linkora's Android Node.js runtime is experimental. Pure JavaScript packages " +
                            "are supported first. Native packages such as sharp and native bcrypt may " +
                            "fail to install. npm packages and install scripts run with Linkora's app " +
                            "permissions, so continue only for projects you trust. The complete npm " +
                            "log will be shown."
                    },
                )
            },
            confirmButton = {
                ActionButton(
                    label = "Try anyway",
                    onClick = {
                        showNativeModuleWarning = false
                        onInstallNodeDependencies()
                    },
                    compact = true,
                )
            },
            dismissButton = {
                ActionButton(
                    label = "Cancel",
                    onClick = { showNativeModuleWarning = false },
                    style = ActionStyle.Neutral,
                    compact = true,
                )
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePadding = ((maxWidth - 680.dp) / 2).coerceAtLeast(20.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = sidePadding,
                top = 20.dp,
                end = sidePadding,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        item {
            SectionLabel("Project workspace")
            Text(
                text = workspace.name,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = when {
                    workspace.kind == ProjectKind.Static ->
                        "Your website is ready. Open the preview or choose another folder."
                    originReady ->
                        if (appMode == AppMode.LinkoraManaged) {
                            "It is running on this phone. Preview it or assign a public Linkora URL."
                        } else {
                            "It is running on this phone. Preview it or publish with your own setup."
                        }
                    else ->
                        "Start the project, then open its preview."
                },
                modifier = Modifier.padding(top = 6.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (notice != null) {
            item {
                StatusPanel("PROJECT NOT CHANGED", notice, error = true)
            }
        }

        item {
            when (workspace.kind) {
                ProjectKind.Static -> StaticServerPanel(
                    workspace = workspace,
                    onPreview = onPreview,
                    onChooseProject = onOpenFiles,
                )
                ProjectKind.Node -> NodeRuntimePanel(
                    workspace = workspace,
                    state = nodeRuntimeState,
                    onPreview = onPreview,
                    onChooseProject = onOpenFiles,
                    onStart = onStartNode,
                    onRestart = onRestartNode,
                    onStop = onStopNode,
                    onInstallDependencies = { showNativeModuleWarning = true },
                    onUpdateLaunchCommand = onUpdateLaunchCommand,
                )
                ProjectKind.Php -> PhpRuntimePanel(
                    workspace = workspace,
                    state = phpRuntimeState,
                    onPreview = onPreview,
                    onChooseProject = onOpenFiles,
                    onStart = onStartPhp,
                    onRestart = onRestartPhp,
                    onStop = onStopPhp,
                    onUpdateLaunchCommand = onUpdateLaunchCommand,
                )
            }
        }

        if (workspace.kind != ProjectKind.Static) {
            item {
                MariaDbPanel(
                    state = mariaDbRuntimeState,
                    packState = mariaDbPackState,
                    onStart = onStartMariaDb,
                    onRestart = onRestartMariaDb,
                    onStop = onStopMariaDb,
                    onExploreDatabase = onOpenDatabaseExplorer,
                    onManageRuntimes = onManageRuntimes,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Project files and settings",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = workspace.visibleLocation,
                        modifier = Modifier.padding(top = 2.dp),
                        color = MutedText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ActionButton(
                    label = if (showTechnicalDetails) "Hide" else "Details",
                    onClick = { showTechnicalDetails = !showTechnicalDetails },
                    modifier = Modifier.padding(start = 12.dp),
                    style = ActionStyle.Neutral,
                    compact = true,
                )
            }
        }

        if (showTechnicalDetails) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, PaleStone),
                colors = CardDefaults.cardColors(containerColor = PaperRaised),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionLabel("Project details")
                            Text(
                                text = workspace.name,
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        Surface(
                            color = Color(0xFFEEF0E8),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, DustyOlive),
                        ) {
                            Text(
                                text = when (workspace.kind) {
                                    ProjectKind.Static -> "READY"
                                    ProjectKind.Node -> nodeRuntimeState.phase.name.uppercase()
                                    ProjectKind.Php -> phpRuntimeState.phase.name.uppercase()
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                color = DustyOlive,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = PaleStone,
                    )
                    Text(
                        text = "SAVED TO",
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = workspace.visibleLocation,
                        modifier = Modifier.padding(top = 7.dp),
                        color = Charcoal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    SummaryRow(label = "Local server", value = workspace.localUrl)
                    if (workspace.kind == ProjectKind.Node) {
                        SummaryRow(label = "Runtime", value = "Node.js 24.22.1")
                        SummaryRow(label = "Entry", value = workspace.entryPoint ?: "Not detected")
                        if (workspace.sqliteCompatibilityDrivers.isNotEmpty()) {
                            SummaryRow(
                                label = "SQLite adapter",
                                value = workspace.sqliteCompatibilityDrivers.joinToString(),
                            )
                        }
                    }
                    if (workspace.kind == ProjectKind.Php) {
                        SummaryRow(label = "Runtime", value = "PHP 8.4.27")
                        SummaryRow(label = "Entry", value = workspace.entryPoint ?: "index.php")
                        SummaryRow(label = "Database", value = "SQLite or MariaDB 13")
                    }
                }
            }
        }

        if (showTechnicalDetails && workspace.sqliteCompatibilityDrivers.isNotEmpty()) {
            item {
                StatusPanel(
                    label = "SQLITE COMPATIBILITY",
                    message = "${workspace.sqliteCompatibilityDrivers.joinToString()} will use " +
                        "Linkora's node:sqlite compatibility layer. Runtime-created database files " +
                        "are preserved between restarts.",
                )
            }
        }

        if (showTechnicalDetails) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Project files")
                workspace.files.forEach { fileName ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PaperRaised,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, PaleStone),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = fileName.substringAfterLast('.', "FILE").uppercase(),
                                color = Oxblood,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                text = fileName,
                                modifier = Modifier.padding(start = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("Publish online")
                if (originReady) {
                    ActionButton(
                        label = if (appMode == AppMode.LinkoraManaged) {
                            "Assign Linkora URL"
                        } else {
                            "Publish or share"
                        },
                        onClick = onAssignUrl,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (appMode == AppMode.LinkoraManaged) {
                            "Route a Linkora hostname to the project running on this phone."
                        } else {
                            "Use local access or connect a domain you manage."
                        },
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = "Start this project to make publishing available.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider(color = PaleStone)
                SectionLabel(if (appMode == AppMode.LinkoraManaged) "Account" else "Service mode")
                Text(
                    text = if (appMode == AppMode.LinkoraManaged) {
                        "Connected as ${accountEmail ?: "Linkora account"}"
                    } else {
                        "Standalone. Linkora sign-in is not required."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ActionButton(
                    label = "Choose a different project",
                    onClick = onChangeProject,
                    modifier = Modifier.fillMaxWidth(),
                    style = ActionStyle.Neutral,
                    compact = true,
                )
                ActionButton(
                    label = "Change service mode",
                    onClick = onChangeMode,
                    modifier = Modifier.fillMaxWidth(),
                    style = ActionStyle.Neutral,
                    compact = true,
                )
                if (appMode == AppMode.LinkoraManaged) {
                    ActionButton(
                        label = "Sign out",
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                        style = ActionStyle.Destructive,
                        compact = true,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun RuntimeStatusBadge(
    label: String,
    error: Boolean = false,
    active: Boolean = true,
) {
    Surface(
        color = when {
            error -> OxbloodSoft
            active -> Color(0xFFF0F2EC)
            else -> ActionSurface
        },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            when {
                error -> Oxblood
                active -> DustyOlive
                else -> PaleStone
            },
        ),
    ) {
        Text(
            text = label.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = when {
                error -> Oxblood
                active -> DustyOlive
                else -> MutedText
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StartCommandEditor(
    workspace: ProjectWorkspace,
    enabled: Boolean,
    onSave: (String) -> Unit,
) {
    var editing by rememberSaveable(workspace.visibleLocation) { mutableStateOf(false) }
    var value by rememberSaveable(workspace.visibleLocation, workspace.launchCommand) {
        mutableStateOf(workspace.launchCommand.orEmpty())
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        color = WarmPaper,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "START COMMAND",
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = workspace.launchCommand ?: "Not configured",
                        modifier = Modifier.padding(top = 5.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ActionButton(
                    label = if (editing) "Cancel" else "Change",
                    onClick = { editing = !editing },
                    enabled = enabled,
                    modifier = Modifier.padding(start = 10.dp),
                    style = ActionStyle.Neutral,
                    compact = true,
                )
            }

            if (editing) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text("Command") },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                )
                workspace.availableCommands
                    .filter { it != value }
                    .take(4)
                    .forEach { command ->
                        ActionButton(
                            label = command,
                            onClick = { value = command },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            style = ActionStyle.Neutral,
                            compact = true,
                        )
                    }
                Text(
                    text = "Use {HOST} and {PORT} for the server address. Run only projects you trust.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
                ActionButton(
                    label = "Save command",
                    onClick = {
                        onSave(value)
                        editing = false
                    },
                    enabled = value.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun StaticServerPanel(
    workspace: ProjectWorkspace,
    onPreview: () -> Unit,
    onChooseProject: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel("Local server")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Serving locally", style = MaterialTheme.typography.titleMedium)
                RuntimeStatusBadge("Running")
            }
            Text(
                text = "Your selected folder is being served from this phone.",
                modifier = Modifier.padding(top = 8.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            SummaryRow(label = "Address", value = workspace.localUrl)
            ActionButton(
                label = "Open preview",
                onClick = onPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            ActionButton(
                label = "Choose another folder",
                onClick = onChooseProject,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                style = ActionStyle.Secondary,
                compact = true,
            )
            Text(
                text = "Select a folder that contains index.html.",
                modifier = Modifier.padding(top = 8.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NodeRuntimePanel(
    workspace: ProjectWorkspace,
    state: NodeRuntimeState,
    onPreview: () -> Unit,
    onChooseProject: () -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onInstallDependencies: () -> Unit,
    onUpdateLaunchCommand: (String) -> Unit,
) {
    val isError = state.phase == NodeRuntimePhase.Error
    var showLogs by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isError) Oxblood else PaleStone),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel("Node.js runtime")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (state.phase) {
                        NodeRuntimePhase.Idle -> "Ready to start"
                        NodeRuntimePhase.Syncing -> "Syncing project"
                        NodeRuntimePhase.Starting -> "Starting"
                        NodeRuntimePhase.Running -> "Running"
                        NodeRuntimePhase.Stopping -> "Stopping"
                        NodeRuntimePhase.Installing -> "Installing packages"
                        NodeRuntimePhase.Error -> "Needs attention"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                RuntimeStatusBadge(state.phase.name, isError)
            }
            Text(
                text = state.message,
                modifier = Modifier.padding(top = 8.dp),
                color = if (isError) Oxblood else MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            StartCommandEditor(
                workspace = workspace,
                enabled = !state.isBusy && !state.isRunning,
                onSave = onUpdateLaunchCommand,
            )
            if (state.isRunning) {
                SummaryRow(
                    label = "Uptime",
                    value = state.uptimeSeconds?.let(::formatRuntimeDuration) ?: "Starting",
                )
                SummaryRow(
                    label = "Memory",
                    value = state.memoryMb?.let { "$it MB" } ?: "Measuring",
                )
            }
            ActionButton(
                label = if (state.isRunning) "Open project preview" else "Start project",
                onClick = if (state.isRunning) onPreview else onStart,
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            if (state.isRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionButton(
                        label = "Restart",
                        onClick = onRestart,
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Secondary,
                        compact = true,
                    )
                    ActionButton(
                        label = "Stop",
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Destructive,
                        compact = true,
                    )
                }
            } else if (state.isBusy) {
                ActionButton(
                    label = "Stop",
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = ActionStyle.Destructive,
                )
            }

            ActionButton(
                label = if (workspace.hasDependencies) {
                    "Install or update npm packages"
                } else {
                    "Check npm packages"
                },
                onClick = onInstallDependencies,
                enabled = !state.isBusy && !state.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                style = ActionStyle.Neutral,
                compact = true,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    label = "Project folder",
                    onClick = onChooseProject,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                    style = ActionStyle.Neutral,
                    compact = true,
                )
                if (state.logs.isNotEmpty()) {
                    ActionButton(
                        label = if (showLogs) "Hide logs" else "Runtime logs",
                        onClick = { showLogs = !showLogs },
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Neutral,
                        compact = true,
                    )
                }
            }
            if (showLogs && state.logs.isNotEmpty()) {
                Text(
                    text = "RUNTIME LOG",
                    modifier = Modifier.padding(top = 16.dp),
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = Charcoal,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = state.logs.takeLast(12).joinToString("\n") { line ->
                            line.take(320)
                        },
                        modifier = Modifier.padding(14.dp),
                        color = WarmPaper,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhpRuntimePanel(
    workspace: ProjectWorkspace,
    state: PhpRuntimeState,
    onPreview: () -> Unit,
    onChooseProject: () -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onUpdateLaunchCommand: (String) -> Unit,
) {
    val isError = state.phase == PhpRuntimePhase.Error
    var showLogs by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isError) Oxblood else PaleStone),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel("PHP runtime")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (state.phase) {
                        PhpRuntimePhase.Idle -> "Ready to start"
                        PhpRuntimePhase.Syncing -> "Syncing project"
                        PhpRuntimePhase.Starting -> "Starting"
                        PhpRuntimePhase.Running -> "Running"
                        PhpRuntimePhase.Stopping -> "Stopping"
                        PhpRuntimePhase.Error -> "Needs attention"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                RuntimeStatusBadge(state.phase.name, isError)
            }
            Text(
                text = state.message,
                modifier = Modifier.padding(top = 8.dp),
                color = if (isError) Oxblood else MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            StartCommandEditor(
                workspace = workspace,
                enabled = !state.isBusy && !state.isRunning,
                onSave = onUpdateLaunchCommand,
            )
            if (state.isRunning) {
                SummaryRow(
                    label = "Uptime",
                    value = state.uptimeSeconds?.let(::formatRuntimeDuration) ?: "Starting",
                )
                SummaryRow(
                    label = "Memory",
                    value = state.memoryMb?.let { "$it MB" } ?: "Measuring",
                )
            }
            ActionButton(
                label = if (state.isRunning) "Open project preview" else "Start project",
                onClick = if (state.isRunning) onPreview else onStart,
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            if (state.isRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionButton(
                        label = "Restart",
                        onClick = onRestart,
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Secondary,
                        compact = true,
                    )
                    ActionButton(
                        label = "Stop",
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Destructive,
                        compact = true,
                    )
                }
            } else if (state.isBusy) {
                ActionButton(
                    label = "Stop",
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = ActionStyle.Destructive,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    label = "Project folder",
                    onClick = onChooseProject,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                    style = ActionStyle.Neutral,
                    compact = true,
                )
                if (state.logs.isNotEmpty()) {
                    ActionButton(
                        label = if (showLogs) "Hide logs" else "Runtime logs",
                        onClick = { showLogs = !showLogs },
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Neutral,
                        compact = true,
                    )
                }
            }
            if (showLogs && state.logs.isNotEmpty()) {
                Text(
                    text = "RUNTIME LOG",
                    modifier = Modifier.padding(top = 16.dp),
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = Charcoal,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = state.logs.takeLast(12).joinToString("\n") { it.take(320) },
                        modifier = Modifier.padding(14.dp),
                        color = WarmPaper,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MariaDbPanel(
    state: MariaDbRuntimeState,
    packState: RuntimePackState,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onExploreDatabase: () -> Unit,
    onManageRuntimes: () -> Unit,
) {
    val isError = state.phase == MariaDbRuntimePhase.Error ||
        packState == RuntimePackState.Invalid ||
        packState == RuntimePackState.Incompatible
    val packReady = packState == RuntimePackState.Ready
    var showConnection by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isError) Oxblood else PaleStone),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel("Optional database")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        !packReady -> "MariaDB needs its runtime pack"
                        state.phase == MariaDbRuntimePhase.Idle -> "MariaDB is off"
                        state.phase == MariaDbRuntimePhase.Preparing -> "Preparing database"
                        state.phase == MariaDbRuntimePhase.Starting -> "Starting database"
                        state.phase == MariaDbRuntimePhase.Running -> "MariaDB is running"
                        state.phase == MariaDbRuntimePhase.Stopping -> "Stopping database"
                        else -> "Database needs attention"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                RuntimeStatusBadge(
                    label = if (!packReady) "NOT INSTALLED" else state.phase.name,
                    error = isError,
                )
            }
            Text(
                text = if (!packReady) {
                    "Install the separate MariaDB Runtime Pack to use MySQL-compatible projects."
                } else {
                    state.message
                },
                modifier = Modifier.padding(top = 8.dp),
                color = if (isError) Oxblood else MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.isRunning) {
                SummaryRow(
                    label = "Uptime",
                    value = state.uptimeSeconds?.let(::formatRuntimeDuration) ?: "Starting",
                )
                SummaryRow(
                    label = "Memory",
                    value = state.memoryMb?.let { "$it MB" } ?: "Measuring",
                )
            }

            if (!state.isRunning) {
                ActionButton(
                    label = if (packReady) "Start database" else "Open Runtime Manager",
                    onClick = if (packReady) onStart else onManageRuntimes,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            } else {
                ActionButton(
                    label = "Explore database",
                    onClick = onExploreDatabase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionButton(
                        label = "Restart",
                        onClick = onRestart,
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Secondary,
                        compact = true,
                    )
                    ActionButton(
                        label = "Stop",
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Destructive,
                        compact = true,
                    )
                }
            }

            if (state.credentials != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionButton(
                        label = if (showConnection) "Hide connection" else "Connection details",
                        onClick = { showConnection = !showConnection },
                        modifier = Modifier.weight(1f),
                        style = ActionStyle.Neutral,
                        compact = true,
                    )
                    if (state.logs.isNotEmpty()) {
                        ActionButton(
                            label = if (showLogs) "Hide logs" else "Database logs",
                            onClick = { showLogs = !showLogs },
                            modifier = Modifier.weight(1f),
                            style = ActionStyle.Neutral,
                            compact = true,
                        )
                    }
                }
            }

            state.credentials?.takeIf { showConnection }?.let { credentials ->
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    SummaryRow("Host", "${credentials.host}:${credentials.port}")
                    SummaryRow("Database", credentials.database)
                    SummaryRow("Username", credentials.username)
                    SummaryRow("Password", credentials.password)
                }
            }

            if (showLogs && state.logs.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = Charcoal,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = state.logs.takeLast(10).joinToString("\n") { it.take(320) },
                        modifier = Modifier.padding(14.dp),
                        color = WarmPaper,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ProjectPreviewScreen(
    workspace: ProjectWorkspace,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                label = "Back",
                onClick = onBack,
                style = ActionStyle.Neutral,
                compact = true,
            )
            Text(
                text = workspace.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            ActionButton(
                label = "Reload",
                onClick = { webView?.reload() },
                style = ActionStyle.Secondary,
                compact = true,
            )
        }
        HorizontalDivider(color = PaleStone)
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val destination = request?.url ?: return true
                            if (
                                destination.scheme == "http" &&
                                destination.host == "127.0.0.1" &&
                                destination.port == 8787
                            ) {
                                return false
                            }
                            return runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, destination))
                                true
                            }.getOrDefault(true)
                        }
                    }
                    loadUrl(workspace.localUrl)
                    webView = this
                }
            },
        )
    }
}

@Composable
private fun StandalonePublishScreen(
    connectorState: TunnelConnectorState,
    connectorReady: Boolean,
    hostingAccess: HostingAccess,
    localNetworkUrl: String?,
    onSelectHostingAccess: (HostingAccess) -> Unit,
    onConnect: (String, String) -> Unit,
    onInstallConnector: () -> Unit,
    onStopConnector: () -> Unit,
    onBack: () -> Unit,
) {
    var hostname by rememberSaveable { mutableStateOf("") }
    var tunnelToken by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val activeHostname = when (connectorState) {
        is TunnelConnectorState.Starting -> connectorState.hostname
        is TunnelConnectorState.Running -> connectorState.hostname
        is TunnelConnectorState.Reconnecting -> connectorState.hostname
        is TunnelConnectorState.Error -> connectorState.hostname
        TunnelConnectorState.Stopped -> null
    }

    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 24.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Standalone publishing")
                    Text(
                        text = "Use your own connection.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
                ActionButton(
                    label = "Back",
                    onClick = onBack,
                    style = ActionStyle.Neutral,
                    compact = true,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, PaleStone),
                colors = CardDefaults.cardColors(containerColor = PaperRaised),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Local access")
                    Text(
                        text = localNetworkUrl ?: ProjectWorkspaceManager.LOCAL_URL,
                        modifier = Modifier.padding(top = 10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (hostingAccess == HostingAccess.LocalNetwork) {
                            "Other devices on the same network can open this address."
                        } else {
                            "Only this phone can open the local address."
                        },
                        modifier = Modifier.padding(top = 6.dp),
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ActionButton(
                        label = if (hostingAccess == HostingAccess.LocalNetwork) {
                            "Use device only"
                        } else {
                            "Share on local network"
                        },
                        onClick = {
                            onSelectHostingAccess(
                                if (hostingAccess == HostingAccess.LocalNetwork) {
                                    HostingAccess.DeviceOnly
                                } else {
                                    HostingAccess.LocalNetwork
                                },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        style = ActionStyle.Secondary,
                        compact = true,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, PaleStone),
                colors = CardDefaults.cardColors(containerColor = PaperRaised),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Own domain")
                    Text(
                        text = "Cloudflare Tunnel",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    OutlinedTextField(
                        value = hostname,
                        onValueChange = {
                            hostname = it.lowercase().trim()
                            validationError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        label = { Text("Public hostname") },
                        placeholder = { Text("app.example.com") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = tunnelToken,
                        onValueChange = {
                            tunnelToken = it.trim()
                            validationError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        label = { Text("Tunnel token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Text(
                        text = "Configure the hostname for http://127.0.0.1:8787 in your Cloudflare tunnel.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    validationError?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier.padding(top = 10.dp),
                            color = Oxblood,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (!connectorReady) {
                        ActionButton(
                            label = "Install Connector Pack",
                            onClick = onInstallConnector,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                        )
                    } else {
                        ActionButton(
                            label = "Connect domain",
                            onClick = {
                                when {
                                    !STANDALONE_HOSTNAME_PATTERN.matches(hostname) ->
                                        validationError = "Enter a valid hostname such as app.example.com."
                                    tunnelToken.length < 20 ->
                                        validationError = "Enter the tunnel token from Cloudflare."
                                    else -> onConnect(hostname, tunnelToken)
                                }
                            },
                            enabled = connectorState !is TunnelConnectorState.Starting &&
                                connectorState !is TunnelConnectorState.Reconnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                        )
                    }
                }
            }
        }

        if (activeHostname != null) {
            item {
                val status = when (connectorState) {
                    is TunnelConnectorState.Starting -> "Connecting"
                    is TunnelConnectorState.Running -> "Online"
                    is TunnelConnectorState.Reconnecting -> "Reconnecting"
                    is TunnelConnectorState.Error -> "Connection failed"
                    TunnelConnectorState.Stopped -> "Stopped"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        1.dp,
                        if (connectorState is TunnelConnectorState.Error) Oxblood else DustyOlive,
                    ),
                    colors = CardDefaults.cardColors(containerColor = PaperRaised),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionLabel("Connection status")
                        Text(
                            text = activeHostname,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = status,
                            modifier = Modifier.padding(top = 4.dp),
                            color = if (connectorState is TunnelConnectorState.Error) {
                                Oxblood
                            } else {
                                DustyOlive
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        if (connectorState is TunnelConnectorState.Error) {
                            Text(
                                text = connectorState.message,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MutedText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        ActionButton(
                            label = "Stop connection",
                            onClick = onStopConnector,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                            style = ActionStyle.Destructive,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

private val STANDALONE_HOSTNAME_PATTERN = Regex(
    "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$",
)

@Composable
private fun PublishScreen(
    state: PublishUiState,
    connectorState: TunnelConnectorState,
    onClaimHostname: (String, String) -> Unit,
    onConnectHostname: (ManagedHostname) -> Unit,
    onRetry: () -> Unit,
    onStopConnector: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 28.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            ActionButton(
                label = "Back to project",
                onClick = onBack,
                style = ActionStyle.Neutral,
                compact = true,
            )
            SectionLabel("LINKORA PUBLISHING")
            Text(
                text = "Assign a public URL.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "The URL routes to the real local server running on this phone.",
                modifier = Modifier.padding(top = 12.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        when (state) {
            PublishUiState.Loading -> item {
                StatusPanel("LOADING WORKSPACE", "Checking your hostname pools and existing URLs...")
            }

            is PublishUiState.Working -> item {
                StatusPanel("ASSIGNING URL", state.message)
            }

            is PublishUiState.Error -> item {
                StatusPanel("PUBLISHING NEEDS ATTENTION", state.message, error = true)
                ActionButton(
                    label = "Try again",
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }

            is PublishUiState.Ready -> item {
                ReadyPublishPanel(
                    state = state,
                    onClaimHostname = onClaimHostname,
                    onConnectHostname = onConnectHostname,
                )
            }

            is PublishUiState.Published -> item {
                PublishedPanel(
                    state = state,
                    connectorState = connectorState,
                    onStopConnector = onStopConnector,
                )
            }
        }
    }
}

@Composable
private fun ReadyPublishPanel(
    state: PublishUiState.Ready,
    onClaimHostname: (String, String) -> Unit,
    onConnectHostname: (ManagedHostname) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var selectedPoolId by rememberSaveable { mutableStateOf<String?>(null) }
    var poolMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.pools) {
        if (selectedPoolId !in state.pools.map { it.id }) {
            selectedPoolId = state.pools.firstOrNull()?.id
        }
    }
    val selectedPool = state.pools.firstOrNull { it.id == selectedPoolId }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.hostnames.isNotEmpty()) {
            SectionLabel("EXISTING MANAGED URLS")
            state.hostnames.forEach { hostname ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = PaperRaised,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, PaleStone),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hostname.hostname,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = hostname.status,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                            )
                        }
                        ActionButton(
                            label = "Connect",
                            onClick = { onConnectHostname(hostname) },
                            enabled = hostname.tunnelId != null,
                            compact = true,
                        )
                    }
                }
            }
            HorizontalDivider(color = PaleStone)
        }

        SectionLabel("NEW LINKORA URL")
        if (state.pools.isEmpty()) {
            StatusPanel("NO DOMAIN POOLS", "No active Linkora domain pool is available.", error = true)
            return@Column
        }

        Text(
            text = "DOMAIN",
            color = MutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                label = selectedPool?.domain ?: "Choose a domain",
                onClick = { poolMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
                style = ActionStyle.Neutral,
            )
            DropdownMenu(
                expanded = poolMenuOpen,
                onDismissRequest = { poolMenuOpen = false },
            ) {
                state.pools.forEach { pool ->
                    DropdownMenuItem(
                        text = { Text(pool.domain) },
                        onClick = {
                            selectedPoolId = pool.id
                            poolMenuOpen = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = label,
            onValueChange = { value ->
                label = value.lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(63)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hostname label") },
            supportingText = {
                Text("${label.ifBlank { "your-name" }}.${selectedPool?.domain.orEmpty()}")
            },
            singleLine = true,
        )
        ActionButton(
            label = "Reserve and connect",
            onClick = { selectedPoolId?.let { onClaimHostname(label, it) } },
            enabled = label.isNotBlank() && selectedPoolId != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PublishedPanel(
    state: PublishUiState.Published,
    connectorState: TunnelConnectorState,
    onStopConnector: () -> Unit,
) {
    val context = LocalContext.current
    val matchingState = when (connectorState) {
        is TunnelConnectorState.Starting -> connectorState.takeIf {
            it.hostname == state.hostname
        }
        is TunnelConnectorState.Running -> connectorState.takeIf {
            it.hostname == state.hostname
        }
        is TunnelConnectorState.Reconnecting -> connectorState.takeIf {
            it.hostname == state.hostname
        }
        is TunnelConnectorState.Error -> connectorState.takeIf {
            it.hostname == state.hostname
        }
        TunnelConnectorState.Stopped -> null
    }
    val statusLabel = when (matchingState) {
        is TunnelConnectorState.Running -> "ONLINE"
        is TunnelConnectorState.Reconnecting -> "RECONNECTING"
        is TunnelConnectorState.Error -> "CONNECTION FAILED"
        else -> "CONNECTING"
    }
    val statusMessage = when (matchingState) {
        is TunnelConnectorState.Running ->
            "This phone is serving the selected project at https://${state.hostname}."
        is TunnelConnectorState.Reconnecting ->
            "Linkora is restoring the connection after a network interruption."
        is TunnelConnectorState.Error -> matchingState.message
        is TunnelConnectorState.Starting ->
            "Linkora is starting the built-in connector on this phone."
        TunnelConnectorState.Stopped, null ->
            "The connector stopped. Go back and select this hostname to reconnect."
    }
    val canStop = matchingState is TunnelConnectorState.Starting ||
        matchingState is TunnelConnectorState.Running ||
        matchingState is TunnelConnectorState.Reconnecting
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, DustyOlive),
        colors = CardDefaults.cardColors(containerColor = PaperRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionLabel(statusLabel)
            Text(
                text = state.hostname,
                modifier = Modifier.padding(top = 10.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = statusMessage,
                modifier = Modifier.padding(top = 12.dp),
                color = if (matchingState is TunnelConnectorState.Error) Oxblood else MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (matchingState is TunnelConnectorState.Running) {
                ActionButton(
                    label = "Open website",
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://${state.hostname}")),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                )
            }
            if (canStop) {
                ActionButton(
                    label = "Stop publishing",
                    onClick = onStopConnector,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = ActionStyle.Destructive,
                )
            }
        }
    }
}

@Composable
private fun StatusPanel(
    label: String,
    message: String,
    error: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (error) Oxblood else PaleStone),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel(label)
            Text(
                text = message,
                modifier = Modifier.padding(top = 8.dp),
                color = if (error) Oxblood else MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AuthenticationStatusCard(
    authState: AuthUiState,
    onReopenBrowser: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, PaleStone),
        colors = CardDefaults.cardColors(containerColor = PaperRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (authState) {
                AuthUiState.Restoring -> {
                    SectionLabel("CHECKING SESSION")
                    Text(
                        text = "Looking for a saved Linkora account...",
                        modifier = Modifier.padding(top = 10.dp),
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                AuthUiState.SignedOut -> {
                    SectionLabel("NOT CONNECTED")
                    Text(
                        text = "Sign in through linkora.top to connect this device without sharing your password with the app.",
                        modifier = Modifier.padding(top = 10.dp),
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                AuthUiState.Starting -> {
                    SectionLabel("STARTING SECURE SIGN-IN")
                    Text(
                        text = "Requesting a one-time device authorization code...",
                        modifier = Modifier.padding(top = 10.dp),
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is AuthUiState.AwaitingApproval -> {
                    SectionLabel("WAITING FOR APPROVAL")
                    Text(
                        text = authState.userCode,
                        modifier = Modifier.padding(top = 10.dp),
                        color = Oxblood,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Confirm this code in the browser, approve Linkora Android, then return here. The app will finish automatically.",
                        modifier = Modifier.padding(top = 10.dp),
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ActionButton(
                        label = "Open browser again",
                        onClick = onReopenBrowser,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        style = ActionStyle.Secondary,
                    )
                }

                is AuthUiState.SignedIn -> {
                    SectionLabel("ACCOUNT CONNECTED")
                    Text(
                        text = authState.session.email,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Hostname quota: ${authState.session.hostnameQuota}",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is AuthUiState.Error -> {
                    SectionLabel("SIGN-IN NEEDS ATTENTION")
                    Text(
                        text = authState.message,
                        modifier = Modifier.padding(top = 10.dp),
                        color = Oxblood,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ActionStyle = ActionStyle.Primary,
    compact: Boolean = false,
) {
    val colors = when (style) {
        ActionStyle.Primary -> ButtonDefaults.buttonColors(
            containerColor = Oxblood,
            contentColor = PaperRaised,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = DisabledText,
        )
        ActionStyle.Secondary -> ButtonDefaults.buttonColors(
            containerColor = PaperRaised,
            contentColor = Oxblood,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = DisabledText,
        )
        ActionStyle.Neutral -> ButtonDefaults.buttonColors(
            containerColor = PaperRaised,
            contentColor = Charcoal,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = DisabledText,
        )
        ActionStyle.Destructive -> ButtonDefaults.buttonColors(
            containerColor = OxbloodSoft,
            contentColor = Oxblood,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = DisabledText,
        )
    }
    val border = when (style) {
        ActionStyle.Primary -> null
        ActionStyle.Secondary, ActionStyle.Destructive -> BorderStroke(
            1.dp,
            if (enabled) Oxblood else StrongBorder,
        )
        ActionStyle.Neutral -> BorderStroke(1.dp, PaleStone)
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 42.dp else 48.dp),
        shape = MaterialTheme.shapes.small,
        colors = colors,
        border = border,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (compact) 12.dp else 16.dp,
            vertical = 8.dp,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MutedText, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            modifier = Modifier.padding(start = 16.dp),
            color = Charcoal,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatRuntimeDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Oxblood,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}
