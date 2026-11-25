package top.linkora.mobile.project

enum class ProjectKind(val id: String) {
    Static("static"),
    Node("node"),
    Php("php");

    companion object {
        fun fromId(id: String?): ProjectKind = entries.firstOrNull { it.id == id } ?: Static
    }
}

data class ProjectWorkspace(
    val name: String,
    val visibleLocation: String,
    val files: List<String>,
    val localUrl: String,
    val isStarter: Boolean,
    val kind: ProjectKind,
    val entryPoint: String? = null,
    val launchCommand: String? = null,
    val availableCommands: List<String> = emptyList(),
    val hasDependencies: Boolean = false,
    val sqliteCompatibilityDrivers: List<String> = emptyList(),
)

data class NodeLaunchConfig(
    val projectDirectory: java.io.File,
    val command: ProjectCommand,
)

data class PhpLaunchConfig(
    val projectDirectory: java.io.File,
    val command: ProjectCommand,
)

sealed interface ProjectWorkspaceState {
    data object Preparing : ProjectWorkspaceState
    data class Ready(
        val workspace: ProjectWorkspace,
        val notice: String? = null,
    ) : ProjectWorkspaceState
    data class Error(val message: String) : ProjectWorkspaceState
}
