package top.linkora.mobile.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import top.linkora.mobile.model.ProjectTemplate
import top.linkora.mobile.model.projectTemplates
import top.linkora.mobile.project.ProjectWorkspace
import top.linkora.mobile.project.ProjectWorkspaceState
import top.linkora.mobile.publish.ManagedHostname
import top.linkora.mobile.publish.PublishUiState
import top.linkora.mobile.ui.theme.Charcoal
import top.linkora.mobile.ui.theme.DustyOlive
import top.linkora.mobile.ui.theme.MutedText
import top.linkora.mobile.ui.theme.Oxblood
import top.linkora.mobile.ui.theme.PaleStone
import top.linkora.mobile.ui.theme.PaperRaised
import top.linkora.mobile.ui.theme.WarmPaper

private enum class OnboardingStep {
    ProjectType,
    WebSignIn,
    Workspace,
    Preview,
    Publish,
}

@Composable
fun LinkoraApp(
    authState: AuthUiState,
    projectState: ProjectWorkspaceState,
    publishState: PublishUiState,
    onStartSignIn: () -> Unit,
    onReopenBrowser: () -> Unit,
    onCancelSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetryProject: () -> Unit,
    onLoadPublish: () -> Unit,
    onClaimHostname: (String, String) -> Unit,
    onConnectHostname: (ManagedHostname) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTermux: () -> Unit,
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
                        step = if (
                            authState is AuthUiState.SignedIn &&
                            projectState is ProjectWorkspaceState.Ready
                        ) {
                            OnboardingStep.Workspace
                        } else {
                            OnboardingStep.WebSignIn
                        }
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
                            onPreview = { step = OnboardingStep.Preview },
                            onOpenFiles = onOpenFiles,
                            onAssignUrl = {
                                onLoadPublish()
                                step = OnboardingStep.Publish
                            },
                            onChangeProject = { step = OnboardingStep.ProjectType },
                            onSignOut = onSignOut,
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
                    onClaimHostname = onClaimHostname,
                    onConnectHostname = onConnectHostname,
                    onRetry = onLoadPublish,
                    onOpenTermux = onOpenTermux,
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
            painter = painterResource(R.drawable.ic_linkora_mark),
            contentDescription = null,
            modifier = Modifier.size(width = 24.dp, height = 32.dp),
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = "linkora",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "ANDROID",
                color = MutedText,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            color = PaperRaised,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, PaleStone),
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
                    text = "Static sites are ready now. Runtime projects are shown as planned and cannot be selected yet.",
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
                Button(
                    onClick = onContinue,
                    enabled = selectedProjectId != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Oxblood,
                        contentColor = WarmPaper,
                        disabledContainerColor = PaleStone,
                        disabledContentColor = MutedText,
                    ),
                ) {
                    Text("Continue", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = "No runtime will be downloaded without your approval.",
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
        border = BorderStroke(1.dp, if (selected) Oxblood else PaleStone),
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
                        project.runtimePackage != null -> "TERMUX PACKAGE / ${project.runtimePackage}"
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
                    unselectedColor = PaleStone,
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
                border = BorderStroke(1.dp, PaleStone),
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
                    SummaryRow(label = "Install status", value = "Not installed")
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
                Button(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Oxblood,
                        contentColor = WarmPaper,
                    ),
                ) {
                    Text(
                        text = when (authState) {
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
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (authState is AuthUiState.AwaitingApproval) {
                    TextButton(
                        onClick = onCancelSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
                    ) {
                        Text("Cancel sign-in", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (authState is AuthUiState.SignedIn) {
                    TextButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
                    ) {
                        Text("Sign out", style = MaterialTheme.typography.labelLarge)
                    }
                }
                TextButton(
                    onClick = onChangeProject,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
                ) {
                    Text("Change project type", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(
    workspace: ProjectWorkspace,
    notice: String?,
    accountEmail: String,
    onPreview: () -> Unit,
    onOpenFiles: () -> Unit,
    onAssignUrl: () -> Unit,
    onChangeProject: () -> Unit,
    onSignOut: () -> Unit,
) {
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
            SectionLabel("LOCAL STATIC WORKSPACE")
            Text(
                text = "Your project is ready.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = if (workspace.isStarter) {
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
                border = BorderStroke(1.dp, PaleStone),
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
                                text = "READY",
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
                }
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
            ) {
                Button(
                    onClick = onAssignUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Oxblood,
                        contentColor = WarmPaper,
                    ),
                ) {
                    Text("Assign Linkora URL", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onOpenFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaperRaised,
                        contentColor = Oxblood,
                    ),
                    border = BorderStroke(1.dp, Oxblood),
                ) {
                    Text("Choose project folder", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = "Select a folder containing index.html. Android will remember your choice.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = onPreview,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
                ) {
                    Text("Preview local server", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = "Connected as $accountEmail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = onChangeProject,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
                ) {
                    Text("Change project type", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
                ) {
                    Text("Sign out", style = MaterialTheme.typography.labelLarge)
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
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
            ) {
                Text("Back", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = workspace.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                onClick = { webView?.reload() },
                colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
            ) {
                Text("Reload", style = MaterialTheme.typography.labelLarge)
            }
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
    onClaimHostname: (String, String) -> Unit,
    onConnectHostname: (ManagedHostname) -> Unit,
    onRetry: () -> Unit,
    onOpenTermux: () -> Unit,
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
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
            ) {
                Text("Back to project", style = MaterialTheme.typography.labelLarge)
            }
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
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Oxblood,
                        contentColor = WarmPaper,
                    ),
                ) {
                    Text("Try again", style = MaterialTheme.typography.labelLarge)
                }
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
                    onOpenTermux = onOpenTermux,
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
                        Button(
                            onClick = { onConnectHostname(hostname) },
                            enabled = hostname.tunnelId != null,
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Oxblood,
                                contentColor = WarmPaper,
                            ),
                        ) {
                            Text("Use")
                        }
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
            Button(
                onClick = { poolMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaperRaised,
                    contentColor = Charcoal,
                ),
                border = BorderStroke(1.dp, PaleStone),
            ) {
                Text(selectedPool?.domain ?: "Choose a domain")
            }
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
        Button(
            onClick = { selectedPoolId?.let { onClaimHostname(label, it) } },
            enabled = label.isNotBlank() && selectedPoolId != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = Oxblood,
                contentColor = WarmPaper,
            ),
        ) {
            Text("Reserve and connect", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PublishedPanel(
    state: PublishUiState.Published,
    onOpenTermux: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, DustyOlive),
        colors = CardDefaults.cardColors(containerColor = PaperRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionLabel("URL RESERVED")
            Text(
                text = state.hostname,
                modifier = Modifier.padding(top = 10.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Start the connector in Termux to bring this URL online. Keep the Linkora project server notification running.",
                modifier = Modifier.padding(top = 12.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Linkora connector", state.connectorCommand),
                    )
                    Toast.makeText(context, "Connector command copied", Toast.LENGTH_SHORT).show()
                    onOpenTermux()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Oxblood,
                    contentColor = WarmPaper,
                ),
            ) {
                Text("Copy command and open Termux", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = "Paste the copied command in Termux and press Enter.",
                modifier = Modifier.padding(top = 12.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
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
                    TextButton(
                        onClick = onReopenBrowser,
                        modifier = Modifier.padding(top = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Oxblood),
                    ) {
                        Text("Open browser again")
                    }
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
