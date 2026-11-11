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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.linkora.mobile.R
import top.linkora.mobile.auth.AuthUiState
import top.linkora.mobile.connector.TunnelConnectorState
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
import top.linkora.mobile.project.ProjectWorkspaceState
import top.linkora.mobile.publish.ManagedHostname
import top.linkora.mobile.publish.PublishUiState
import top.linkora.mobile.runtime.RuntimePackState
import top.linkora.mobile.runtime.RuntimePackUiState
import top.linkora.mobile.runtime.RuntimePackInspection
import top.linkora.mobile.runtime.RuntimeDownloadPhase
import top.linkora.mobile.runtime.RuntimeDownloadState
import top.linkora.mobile.runtime.RuntimeDownloadUiState
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

private enum class OnboardingStep {
    ProjectType,
    WebSignIn,
    Workspace,
    Preview,
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
    authState: AuthUiState,
    projectState: ProjectWorkspaceState,
    publishState: PublishUiState,
    connectorState: TunnelConnectorState,
    nodeRuntimeState: NodeRuntimeState,
    phpRuntimeState: PhpRuntimeState,
    mariaDbRuntimeState: MariaDbRuntimeState,
    runtimePackState: RuntimePackUiState,
    runtimeDownloadState: RuntimeDownloadUiState,
    onStartSignIn: () -> Unit,
    onReopenBrowser: () -> Unit,
    onCancelSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetryProject: () -> Unit,
    onPrepareProject: (String) -> Unit,
    onLoadPublish: () -> Unit,
    onClaimHostname: (String, String) -> Unit,
    onConnectHostname: (ManagedHostname) -> Unit,
    onOpenFiles: () -> Unit,
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
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.ProjectType) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>("static") }
    var choosingProject by rememberSaveable { mutableStateOf(false) }
    var showRuntimeManager by rememberSaveable { mutableStateOf(false) }
    val selectedProject = projectTemplates.firstOrNull { it.id == selectedProjectId }

    BackHandler(enabled = showRuntimeManager) { showRuntimeManager = false }

    LaunchedEffect(authState, projectState, choosingProject) {
        if (
            authState is AuthUiState.SignedIn &&
            projectState is ProjectWorkspaceState.Ready &&
            step == OnboardingStep.ProjectType &&
            !choosingProject
        ) {
            selectedProjectId = projectState.workspace.kind.name.lowercase()
            step = OnboardingStep.Workspace
        }
        if (
            authState is AuthUiState.SignedIn &&
            projectState is ProjectWorkspaceState.Ready &&
            step == OnboardingStep.WebSignIn
        ) {
            step = OnboardingStep.Workspace
        }
        if (
            authState is AuthUiState.SignedOut &&
            (
                step == OnboardingStep.Workspace ||
                    step == OnboardingStep.Preview ||
                    step == OnboardingStep.Publish
                )
        ) {
            step = OnboardingStep.WebSignIn
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmPaper,
        contentColor = Charcoal,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (step != OnboardingStep.Preview) {
                LinkoraHeader(onOpenRuntimes = { showRuntimeManager = true })
                HorizontalDivider(color = PaleStone)
            }

            if (showRuntimeManager) {
                RuntimeManagerScreen(
                    packState = runtimePackState,
                    downloadState = runtimeDownloadState,
                    onBack = { showRuntimeManager = false },
                    onDownload = onDownloadRuntime,
                    onCancel = onCancelRuntimeDownload,
                    onInstall = onInstallRuntime,
                    onRemoveInstaller = onRemoveRuntimeInstaller,
                )
            } else when (step) {
                OnboardingStep.ProjectType -> ProjectTypeScreen(
                    selectedProjectId = selectedProjectId,
                    runtimePackState = runtimePackState,
                    onSelectProject = { selectedProjectId = it.id },
                    onContinue = {
                        if (projectNeedsRuntime(selectedProjectId, runtimePackState)) {
                            showRuntimeManager = true
                        } else {
                            choosingProject = false
                            onPrepareProject(requireNotNull(selectedProjectId))
                            step = OnboardingStep.WebSignIn
                        }
                    },
                )

                OnboardingStep.WebSignIn -> WebSignInScreen(
                    selectedProject = requireNotNull(selectedProject),
                    runtimePackState = runtimePackState,
                    authState = authState,
                    projectState = projectState,
                    onStartSignIn = onStartSignIn,
                    onReopenBrowser = onReopenBrowser,
                    onCancelSignIn = onCancelSignIn,
                    onSignOut = onSignOut,
                    onRetryProject = onRetryProject,
                    onOpenWorkspace = { step = OnboardingStep.Workspace },
                    onChangeProject = {
                        choosingProject = true
                        step = OnboardingStep.ProjectType
                    },
                )

                OnboardingStep.Workspace -> {
                    val readyProject = projectState as? ProjectWorkspaceState.Ready
                    val workspace = readyProject?.workspace
                    if (workspace != null && authState is AuthUiState.SignedIn) {
                        WorkspaceScreen(
                            workspace = workspace,
                            notice = readyProject.notice,
                            accountEmail = authState.session.email,
                            nodeRuntimeState = nodeRuntimeState,
                            phpRuntimeState = phpRuntimeState,
                            mariaDbRuntimeState = mariaDbRuntimeState,
                            mariaDbPackState = runtimePackState.mariaDb.state,
                            onPreview = { step = OnboardingStep.Preview },
                            onOpenFiles = onOpenFiles,
                            onAssignUrl = {
                                onLoadPublish()
                                step = OnboardingStep.Publish
                            },
                            onChangeProject = {
                                choosingProject = true
                                step = OnboardingStep.ProjectType
                            },
                            onSignOut = onSignOut,
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
                            onManageRuntimes = { showRuntimeManager = true },
                        )
                    }
                }

                OnboardingStep.Preview -> {
                    val workspace = (projectState as? ProjectWorkspaceState.Ready)?.workspace
                    if (workspace != null) {
                        ProjectPreviewScreen(
                            workspace = workspace,
                            onBack = { step = OnboardingStep.Workspace },
                        )
                    }
                }

                OnboardingStep.Publish -> PublishScreen(
                    state = publishState,
                    connectorState = connectorState,
                    onClaimHostname = onClaimHostname,
                    onConnectHostname = { hostname ->
                        if (runtimePackState.connector.state == RuntimePackState.Ready) {
                            onConnectHostname(hostname)
                        } else {
                            showRuntimeManager = true
                        }
                    },
                    onRetry = onLoadPublish,
                    onStopConnector = onStopConnector,
                    onBack = { step = OnboardingStep.Workspace },
                )
            }
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
private fun SetupProgress(currentStep: Int) {
    val steps = listOf("Project", "Account", "Run")
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Runtime manager")
                    Text(
                        text = "Install only what your projects need.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.headlineMedium,
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

        runtimes.forEach { (inspection, download) ->
            item(key = inspection.spec.id) {
                RuntimeDownloadCard(
                    inspection = inspection,
                    download = download,
                    onDownload = { onDownload(inspection.spec.id) },
                    onCancel = { onCancel(inspection.spec.id) },
                    onInstall = { onInstall(inspection.spec.id) },
                    onRemoveInstaller = { onRemoveInstaller(inspection.spec.id) },
                )
            }
        }

        item {
            Text(
                text = "Downloads continue through Android Download Manager when Linkora is in the background.",
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RuntimeDownloadCard(
    inspection: RuntimePackInspection,
    download: RuntimeDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRemoveInstaller: () -> Unit,
) {
    val installed = inspection.state == RuntimePackState.Ready
    val error = !installed && (
        inspection.state in setOf(
            RuntimePackState.Invalid,
            RuntimePackState.Incompatible,
        ) || download.phase == RuntimeDownloadPhase.Failed
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (error) Oxblood else PaleStone),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inspection.spec.displayName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${inspection.spec.runtimeVersion}  |  ${download.artifact.abi}",
                        modifier = Modifier.padding(top = 4.dp),
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
                RuntimeStatusBadge(
                    label = when {
                        installed -> "Installed"
                        download.phase == RuntimeDownloadPhase.Downloading -> "Downloading"
                        download.phase == RuntimeDownloadPhase.ReadyToInstall -> "Ready"
                        download.phase == RuntimeDownloadPhase.Installing -> "Installing"
                        download.phase == RuntimeDownloadPhase.Unavailable -> "Unavailable"
                        download.phase == RuntimeDownloadPhase.Failed -> "Failed"
                        else -> "Optional"
                    },
                    error = error,
                )
            }

            Text(
                text = runtimeDescription(inspection.spec.id),
                modifier = Modifier.padding(top = 10.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (download.phase == RuntimeDownloadPhase.Downloading) {
                val progress = download.progress
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        color = Oxblood,
                        trackColor = PaleStone,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        color = Oxblood,
                        trackColor = PaleStone,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        modifier = Modifier.padding(top = 6.dp),
                        color = Charcoal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = if (installed) inspection.message else download.message,
                modifier = Modifier.padding(top = 10.dp),
                color = if (error) Oxblood else MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )

            when {
                installed -> {
                    if (download.phase == RuntimeDownloadPhase.ReadyToInstall) {
                        ActionButton(
                            label = "Delete downloaded installer",
                            onClick = onRemoveInstaller,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                            style = ActionStyle.Neutral,
                        )
                    }
                }
                download.phase == RuntimeDownloadPhase.Unavailable -> ActionButton(
                    label = "Not available for this device",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
                download.phase == RuntimeDownloadPhase.Downloading -> ActionButton(
                    label = "Cancel download",
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    style = ActionStyle.Destructive,
                )
                download.phase == RuntimeDownloadPhase.ReadyToInstall -> {
                    ActionButton(
                        label = "Install runtime",
                        onClick = onInstall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                    )
                    ActionButton(
                        label = "Delete installer",
                        onClick = onRemoveInstaller,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        style = ActionStyle.Neutral,
                    )
                }
                download.phase == RuntimeDownloadPhase.Installing -> ActionButton(
                    label = "Waiting for Android...",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
                else -> ActionButton(
                    label = if (download.phase == RuntimeDownloadPhase.Failed) {
                        "Retry download"
                    } else {
                        "Download ${formatDownloadSize(download.totalBytes)}"
                    },
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
            }
        }
    }
}

private fun runtimeDescription(runtimeId: String): String = when (runtimeId) {
    "node" -> "Node.js and npm for JavaScript servers and API projects."
    "php" -> "PHP with SQLite, mysqli, mysqlnd, and PDO MySQL support."
    "mariadb" -> "Optional local MySQL-compatible database for Node.js and PHP projects."
    "connector" -> "Publishes a running phone project through a Linkora managed tunnel."
    else -> "Optional project runtime."
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
            SetupProgress(currentStep = 1)
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
            SetupProgress(currentStep = 2)
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
    workspace: ProjectWorkspace,
    notice: String?,
    accountEmail: String,
    nodeRuntimeState: NodeRuntimeState,
    phpRuntimeState: PhpRuntimeState,
    mariaDbRuntimeState: MariaDbRuntimeState,
    mariaDbPackState: RuntimePackState,
    onPreview: () -> Unit,
    onOpenFiles: () -> Unit,
    onAssignUrl: () -> Unit,
    onChangeProject: () -> Unit,
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
                        "It is running on this phone. Preview it or assign a public Linkora URL."
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
                )
                ProjectKind.Php -> PhpRuntimePanel(
                    state = phpRuntimeState,
                    onPreview = onPreview,
                    onChooseProject = onOpenFiles,
                    onStart = onStartPhp,
                    onRestart = onRestartPhp,
                    onStop = onStopPhp,
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
                        label = "Assign Linkora URL",
                        onClick = onAssignUrl,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Route a Linkora hostname to the project running on this phone.",
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
                SectionLabel("Account")
                Text(
                    text = "Connected as $accountEmail",
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

@Composable
private fun RuntimeStatusBadge(label: String, error: Boolean = false) {
    Surface(
        color = if (error) OxbloodSoft else Color(0xFFF0F2EC),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (error) Oxblood else DustyOlive),
    ) {
        Text(
            text = label.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = if (error) Oxblood else DustyOlive,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
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
    state: PhpRuntimeState,
    onPreview: () -> Unit,
    onChooseProject: () -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
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
