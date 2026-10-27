package top.linkora.mobile.publish

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.linkora.mobile.auth.LinkoraApi
import top.linkora.mobile.auth.LinkoraApiException
import top.linkora.mobile.auth.TokenStore
import top.linkora.mobile.project.ProjectWorkspaceManager

class PublishViewModel(application: Application) : AndroidViewModel(application) {
    private val api = LinkoraApi()
    private val tokenStore = TokenStore(application)
    private val _uiState = MutableStateFlow<PublishUiState>(PublishUiState.Loading)

    val uiState = _uiState.asStateFlow()

    fun load() {
        _uiState.value = PublishUiState.Loading
        viewModelScope.launch {
            runPublishAction {
                val token = requireToken()
                PublishUiState.Ready(
                    pools = api.getDomainPools(token),
                    hostnames = api.getManagedHostnames(token),
                )
            }
        }
    }

    fun claim(label: String, poolId: String) {
        val cleanLabel = label.trim().lowercase()
        if (!LABEL_PATTERN.matches(cleanLabel)) {
            _uiState.value = PublishUiState.Error(
                "Use 3-63 lowercase letters, numbers, or hyphens. Do not start or end with a hyphen.",
            )
            return
        }

        _uiState.value = PublishUiState.Working("Reserving your Linkora URL...")
        viewModelScope.launch {
            runPublishAction {
                val token = requireToken()
                val hostname = api.claimManagedHostname(
                    accessToken = token,
                    poolId = poolId,
                    label = cleanLabel,
                    localServiceUrl = ProjectWorkspaceManager.LOCAL_URL,
                )
                val access = api.createTunnel(
                    accessToken = token,
                    subdomainId = hostname.id,
                    hostname = hostname.hostname,
                    localServiceUrl = ProjectWorkspaceManager.LOCAL_URL,
                )
                access.publishedState()
            }
        }
    }

    fun connect(hostname: ManagedHostname) {
        val tunnelId = hostname.tunnelId
        if (tunnelId == null) {
            _uiState.value = PublishUiState.Error(
                "${hostname.hostname} does not have a managed tunnel.",
            )
            return
        }

        _uiState.value = PublishUiState.Working("Connecting ${hostname.hostname}...")
        viewModelScope.launch {
            runPublishAction {
                api.connectTunnel(
                    accessToken = requireToken(),
                    tunnelId = tunnelId,
                    localServiceUrl = ProjectWorkspaceManager.LOCAL_URL,
                ).publishedState()
            }
        }
    }

    private suspend fun runPublishAction(action: suspend () -> PublishUiState) {
        _uiState.value = try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PublishUiState.Error(error.userMessage())
        }
    }

    private fun requireToken(): String = tokenStore.read()
        ?: throw LinkoraApiException(401, "Sign in again before assigning a URL.")

    private fun TunnelAccess.publishedState(): PublishUiState.Published {
        val safeToken = token.replace("'", "'\\''")
        return PublishUiState.Published(
            hostname = hostname,
            connectorCommand =
                "pkg install -y cloudflared && cloudflared tunnel --no-autoupdate run --token '$safeToken'",
        )
    }

    private fun Exception.userMessage(): String = when (this) {
        is LinkoraApiException -> message ?: "Linkora could not assign the URL."
        is IOException -> "Linkora could not be reached. Check your connection and try again."
        else -> message ?: "Linkora could not assign the URL."
    }

    private companion object {
        val LABEL_PATTERN = Regex("^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])$")
    }
}
