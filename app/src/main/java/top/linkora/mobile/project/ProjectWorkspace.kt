package top.linkora.mobile.project

data class ProjectWorkspace(
    val name: String,
    val visibleLocation: String,
    val files: List<String>,
    val localUrl: String,
    val isStarter: Boolean,
)

sealed interface ProjectWorkspaceState {
    data object Preparing : ProjectWorkspaceState
    data class Ready(
        val workspace: ProjectWorkspace,
        val notice: String? = null,
    ) : ProjectWorkspaceState
    data class Error(val message: String) : ProjectWorkspaceState
}
