package top.linkora.mobile.project

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = ProjectWorkspaceManager(application)
    private val _uiState = MutableStateFlow<ProjectWorkspaceState>(ProjectWorkspaceState.Preparing)

    val uiState = _uiState.asStateFlow()

    init {
        restoreProject()
    }

    fun restoreProject() {
        _uiState.value = ProjectWorkspaceState.Preparing
        viewModelScope.launch {
            _uiState.value = try {
                ProjectWorkspaceState.Ready(
                    withContext(Dispatchers.IO) { manager.restoreProject() },
                )
            } catch (error: Exception) {
                ProjectWorkspaceState.Error(
                    error.message ?: "The starter project could not be prepared.",
                )
            }
        }
    }

    fun prepareProject(projectId: String) {
        val kind = ProjectKind.fromId(projectId)
        _uiState.value = ProjectWorkspaceState.Preparing
        viewModelScope.launch {
            _uiState.value = try {
                ProjectWorkspaceState.Ready(
                    withContext(Dispatchers.IO) { manager.prepareProject(kind) },
                )
            } catch (error: Exception) {
                ProjectWorkspaceState.Error(
                    error.message ?: "The ${kind.id} starter project could not be prepared.",
                )
            }
        }
    }

    fun selectProjectFolder(uri: Uri) {
        val current = (_uiState.value as? ProjectWorkspaceState.Ready)?.workspace
        viewModelScope.launch {
            _uiState.value = try {
                ProjectWorkspaceState.Ready(
                    withContext(Dispatchers.IO) { manager.selectProjectTree(uri) },
                )
            } catch (error: Exception) {
                if (current != null) {
                    ProjectWorkspaceState.Ready(
                        workspace = current,
                        notice = error.message
                            ?: "Linkora could not open that project folder.",
                    )
                } else {
                    ProjectWorkspaceState.Error(
                        error.message ?: "Linkora could not open that project folder.",
                    )
                }
            }
        }
    }

    fun updateLaunchCommand(command: String) {
        val current = (_uiState.value as? ProjectWorkspaceState.Ready)?.workspace ?: return
        viewModelScope.launch {
            _uiState.value = try {
                ProjectWorkspaceState.Ready(
                    withContext(Dispatchers.IO) { manager.updateLaunchCommand(command) },
                )
            } catch (error: Exception) {
                ProjectWorkspaceState.Ready(
                    workspace = current,
                    notice = error.message ?: "The start command is not valid.",
                )
            }
        }
    }
}
