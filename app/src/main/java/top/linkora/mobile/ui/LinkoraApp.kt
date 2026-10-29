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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.linkora.mobile.R
import top.linkora.mobile.auth.AuthUiState
import top.linkora.mobile.connector.TunnelConnectorState
import top.linkora.mobile.model.ProjectTemplate
import top.linkora.mobile.model.projectTemplates
import top.linkora.mobile.node.NodeRuntimePhase
import top.linkora.mobile.node.NodeRuntimeState
import top.linkora.mobile.project.ProjectWorkspace
import top.linkora.mobile.project.ProjectKind
import top.linkora.mobile.project.ProjectWorkspaceState
import top.linkora.mobile.publish.ManagedHostname
import top.linkora.mobile.publish.PublishUiState
import top.linkora.mobile.ui.theme.Charcoal
import top.linkora.mobile.ui.theme.ActionSurface
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
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.ProjectType) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>("static") }
    val selectedProject = projectTemplates.firstOrNull { it.id == selectedProjectId }

    LaunchedEffect(authState, projectState) {
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
                LinkoraHeader()
                HorizontalDivider(color = PaleStone)
            }

            when (step) {
                OnboardingStep.ProjectType -> ProjectTypeScreen(
                    selectedProjectId = selectedProjectId,
                    onSelectProject = { selectedProjectId = it.id },
                    onContinue = {
                        onPrepareProject(requireNotNull(selectedProjectId))
                        step = OnboardingStep.WebSignIn
                    },
                )

                OnboardingStep.WebSignIn -> WebSignInScreen(
                    selectedProject = requireNotNull(selectedProject),
                    authState = authState,
                    projectState = projectState,
                    onStartSignIn = onStartSignIn,
                    onReopenBrowser = onReopenBrowser,
                    onCancelSignIn = onCancelSignIn,
                    onSignOut = onSignOut,
                    onRetryProject = onRetryProject,
                    onOpenWorkspace = { step = OnboardingStep.Workspace },
                    onChangeProject = { step = OnboardingStep.ProjectType },
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
                            onPreview = { step = OnboardingStep.Preview },
                            onOpenFiles = onOpenFiles,
                            onAssignUrl = {
                                onLoadPublish()
                                step = OnboardingStep.Publish
                            },
                            onChangeProject = { step = OnboardingStep.ProjectType },
                            onSignOut = onSignOut,
                            onStartNode = onStartNode,
                            onRestartNode = onRestartNode,
                            onStopNode = onStopNode,
                            onInstallNodeDependencies = onInstallNodeDependencies,
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
                    onConnectHostname = onConnectHostname,
                    onRetry = onLoadPublish,
                    onStopConnector = onStopConnector,
                    onBack = { step = OnboardingStep.Workspace },
                )
            }
        }
    }
}

@Composable
private fun LinkoraHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_linkora_wordmark),
            contentDescription = "Linkora",
            modifier = Modifier.size(width = 137.dp, height = 40.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            color = PaperRaised,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, StrongBorder),
        ) {
            Text(
                text = "EARLY BUILD",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                color = Oxblood,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProjectTypeScreen(
    selectedProjectId: String?,
    onSelectProject: (ProjectTemplate) -> Unit,
    onContinue: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 36.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("LOCAL PROJECT SETUP")
                Text(
                    text = "What are you building?",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Choose a static site or run a Node.js server directly on this phone. PHP and Python are planned.",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            ProjectTemplateGrid(
                selectedProjectId = selectedProjectId,
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
                    label = "Continue",
                    onClick = onContinue,
                    enabled = selectedProjectId != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Node.js is bundled with Linkora. The app does not require Termux or download native runtimes.",
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
                projectTemplates.chunked(2).forEach { columnProjects ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        columnProjects.forEach { project ->
                            ProjectTemplateCard(
                                project = project,
                                selected = selectedProjectId == project.id,
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
                projectTemplates.forEach { project ->
                    ProjectTemplateCard(
                        project = project,
                        selected = selectedProjectId == project.id,
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
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = { if (project.available) onSelect() },
                enabled = project.available,
                role = Role.RadioButton,
            ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (selected) Oxblood else StrongBorder),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFF8ECEC) else PaperRaised,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, if (selected) Oxblood else StrongBorder),
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
                    .padding(start = 14.dp),
            ) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = project.description,
                    modifier = Modifier.padding(top = 5.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = when {
                        !project.available -> "PLANNED / ${project.runtimePackage.orEmpty()}"
                        project.runtimePackage != null -> "BUILT IN / ${project.runtimePackage}"
                        else -> "AVAILABLE / NO RUNTIME REQUIRED"
                    },
                    modifier = Modifier.padding(top = 10.dp),
                    color = if (project.available) DustyOlive else MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            RadioButton(
                selected = selected,
                enabled = project.available,
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
            top = 36.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            SectionLabel("SECURE WEB SIGN-IN")
            Text(
                text = "Connect your Linkora account.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Authentication opens on linkora.top in your browser. The Android app never receives your password.",
                modifier = Modifier.padding(top = 12.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, StrongBorder),
                colors = CardDefaults.cardColors(containerColor = PaperRaised),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SectionLabel("SELECTED PROJECT")
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
                        value = if (selectedProject.id == "node") "Included in app" else "Ready",
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
                        else -> "Continue in browser"
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
                    label = "Change project type",
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
    onPreview: () -> Unit,
    onOpenFiles: () -> Unit,
    onAssignUrl: () -> Unit,
    onChangeProject: () -> Unit,
    onSignOut: () -> Unit,
    onStartNode: () -> Unit,
    onRestartNode: () -> Unit,
    onStopNode: () -> Unit,
    onInstallNodeDependencies: () -> Unit,
) {
    var showNativeModuleWarning by rememberSaveable { mutableStateOf(false) }
    val originReady = workspace.kind == ProjectKind.Static || nodeRuntimeState.isRunning

    if (showNativeModuleWarning) {
        AlertDialog(
            onDismissRequest = { showNativeModuleWarning = false },
            title = { Text("Some packages may not support Android") },
            text = {
                Text(
                    "Linkora's Android Node.js runtime is experimental. Pure JavaScript packages " +
                        "are supported first. Native packages such as sharp, native bcrypt, and " +
                        "some SQLite packages may fail to install. npm packages and install scripts " +
                        "run with Linkora's app permissions, so continue only for projects you trust. " +
                        "The complete npm log will be shown.",
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 32.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SectionLabel(
                if (workspace.kind == ProjectKind.Node) "LOCAL NODE.JS WORKSPACE" else "LOCAL STATIC WORKSPACE",
            )
            Text(
                text = if (workspace.kind == ProjectKind.Node) {
                    "Run your Node.js project."
                } else {
                    "Your project is ready."
                },
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = if (workspace.kind == ProjectKind.Node && workspace.isStarter) {
                    "The Node.js sample is stored in Downloads. Edit it in Files, then restart Node.js to sync and run the latest source."
                } else if (workspace.kind == ProjectKind.Node) {
                    "Edit the selected folder, then restart Node.js to sync and run the latest source."
                } else if (workspace.isStarter) {
                    "This starter is stored in Downloads. Choose it or another folder in Files, then Linkora will serve the real files inside it."
                } else {
                    "Linkora is serving this folder directly. Edit its files, then reload the local preview."
                },
                modifier = Modifier.padding(top = 12.dp),
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, StrongBorder),
                colors = CardDefaults.cardColors(containerColor = PaperRaised),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionLabel("PROJECT")
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
                                text = if (workspace.kind == ProjectKind.Node) {
                                    nodeRuntimeState.phase.name.uppercase()
                                } else {
                                    "READY"
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
                    }
                }
            }
        }

        if (workspace.kind == ProjectKind.Node) {
            item {
                NodeRuntimePanel(
                    workspace = workspace,
                    state = nodeRuntimeState,
                    onStart = onStartNode,
                    onRestart = onRestartNode,
                    onStop = onStopNode,
                    onInstallDependencies = { showNativeModuleWarning = true },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("PROJECT FILES")
                workspace.files.forEach { fileName ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PaperRaised,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, StrongBorder),
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
            ) {
                ActionButton(
                    label = "Assign Linkora URL",
                    onClick = onAssignUrl,
                    enabled = originReady,
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionButton(
                    label = "Choose project folder",
                    onClick = onOpenFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = ActionStyle.Secondary,
                )
                Text(
                    text = if (workspace.kind == ProjectKind.Node) {
                        "Select a folder with package.json or a supported JavaScript entry file. " +
                            "The server must listen on process.env.HOST and process.env.PORT."
                    } else {
                        "Select a folder containing index.html. Android will remember your choice."
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ActionButton(
                    label = if (originReady) {
                        "Preview local server"
                    } else {
                        "Start Node.js to preview"
                    },
                    onClick = onPreview,
                    enabled = originReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = ActionStyle.Neutral,
                )
                Text(
                    text = "Connected as $accountEmail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ActionButton(
                    label = "Change project type",
                    onClick = onChangeProject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    style = ActionStyle.Neutral,
                )
                ActionButton(
                    label = "Sign out",
                    onClick = onSignOut,
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
private fun NodeRuntimePanel(
    workspace: ProjectWorkspace,
    state: NodeRuntimeState,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onInstallDependencies: () -> Unit,
) {
    val isError = state.phase == NodeRuntimePhase.Error
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isError) Oxblood else StrongBorder),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionLabel("NODE.JS RUNTIME")
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
                Text(
                    text = state.phase.name.uppercase(),
                    color = if (isError) Oxblood else DustyOlive,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = state.message,
                modifier = Modifier.padding(top = 8.dp),
                color = if (isError) Oxblood else MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Run only projects you trust. Node.js code runs locally with Linkora's app permissions.",
                modifier = Modifier.padding(top = 8.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    label = if (state.isRunning) "Restart" else "Start",
                    onClick = if (state.isRunning) onRestart else onStart,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
                ActionButton(
                    label = "Stop",
                    onClick = onStop,
                    enabled = state.isRunning || state.isBusy,
                    modifier = Modifier.weight(1f),
                    style = ActionStyle.Destructive,
                    compact = true,
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
            )

            if (state.logs.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = PaleStone,
                )
                Text(
                    text = "RUNTIME LOG",
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
                    border = BorderStroke(1.dp, StrongBorder),
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
        border = BorderStroke(1.dp, if (error) Oxblood else StrongBorder),
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
        border = BorderStroke(1.dp, StrongBorder),
        colors = CardDefaults.cardColors(containerColor = PaperRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
            contentColor = WarmPaper,
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
            containerColor = ActionSurface,
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
        ActionStyle.Neutral -> BorderStroke(1.dp, StrongBorder)
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 44.dp else 50.dp),
        shape = MaterialTheme.shapes.small,
        colors = colors,
        border = border,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (compact) 14.dp else 18.dp,
            vertical = 10.dp,
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Oxblood,
        style = MaterialTheme.typography.labelSmall,
    )
}
