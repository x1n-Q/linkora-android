package top.linkora.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.linkora.mobile.R
import top.linkora.mobile.model.ProjectTemplate
import top.linkora.mobile.model.projectTemplates
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
}

@Composable
fun LinkoraApp(onOpenWebSignIn: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.ProjectType) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedProject = projectTemplates.firstOrNull { it.id == selectedProjectId }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmPaper,
        contentColor = Charcoal,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LinkoraHeader()
            HorizontalDivider(color = PaleStone)

            when (step) {
                OnboardingStep.ProjectType -> ProjectTypeScreen(
                    selectedProjectId = selectedProjectId,
                    onSelectProject = { selectedProjectId = it.id },
                    onContinue = { step = OnboardingStep.WebSignIn },
                )

                OnboardingStep.WebSignIn -> WebSignInScreen(
                    selectedProject = requireNotNull(selectedProject),
                    onOpenWebSignIn = onOpenWebSignIn,
                    onChangeProject = { step = OnboardingStep.ProjectType },
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
                    text = "Choose the closest project type. Linkora will prepare the correct local runtime in a later step.",
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
                    text = project.runtimePackage?.let { "TERMUX PACKAGE / $it" }
                        ?: "NO RUNTIME REQUIRED",
                    modifier = Modifier.padding(top = 10.dp),
                    color = if (project.runtimePackage == null) DustyOlive else Oxblood,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            RadioButton(
                selected = selected,
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
    onOpenWebSignIn: () -> Unit,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Button(
                    onClick = onOpenWebSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Oxblood,
                        contentColor = WarmPaper,
                    ),
                ) {
                    Text("Continue in browser", style = MaterialTheme.typography.labelLarge)
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
