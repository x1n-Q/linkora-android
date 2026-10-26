package top.linkora.mobile.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LinkoraAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val api = LinkoraApi()
    private val tokenStore = TokenStore(application)
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Restoring)
    private val _browserRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val uiState = _uiState.asStateFlow()
    val browserRequests = _browserRequests.asSharedFlow()

    private var accessToken: String? = null
    private var authorizationJob: Job? = null

    init {
        viewModelScope.launch { restoreSession() }
    }

    fun startSignIn() {
        if (authorizationJob?.isActive == true) return

        authorizationJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Starting
            try {
                tokenStore.clear()
                accessToken = null
                val authorization = api.startDeviceAuthorization()
                _uiState.value = AuthUiState.AwaitingApproval(
                    userCode = authorization.userCode,
                    verificationUrl = authorization.verificationUrl,
                )
                _browserRequests.emit(authorization.verificationUrl)

                val deadline = System.currentTimeMillis() + authorization.expiresInSeconds * 1_000L
                while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
                    delay(authorization.pollIntervalSeconds * 1_000L)
                    when (val result = api.pollDeviceToken(authorization.deviceCode)) {
                        DeviceTokenResult.Pending -> Unit
                        is DeviceTokenResult.Approved -> {
                            tokenStore.save(result.accessToken)
                            accessToken = result.accessToken
                            _uiState.value = AuthUiState.SignedIn(
                                api.getSession(result.accessToken),
                            )
                            return@launch
                        }
                    }
                }
                _uiState.value = AuthUiState.Error(
                    "The sign-in request expired. Start again to receive a new code.",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = AuthUiState.Error(error.userMessage())
            }
        }
    }

    fun reopenBrowser() {
        val state = _uiState.value as? AuthUiState.AwaitingApproval ?: return
        _browserRequests.tryEmit(state.verificationUrl)
    }

    fun cancelSignIn() {
        authorizationJob?.cancel()
        authorizationJob = null
        _uiState.value = AuthUiState.SignedOut
    }

    fun signOut() {
        val token = accessToken
        authorizationJob?.cancel()
        authorizationJob = viewModelScope.launch {
            if (token != null) {
                runCatching { api.revokeSession(token) }
            }
            tokenStore.clear()
            accessToken = null
            _uiState.value = AuthUiState.SignedOut
        }
    }

    private suspend fun restoreSession() {
        val storedToken = tokenStore.read()
        if (storedToken == null) {
            _uiState.value = AuthUiState.SignedOut
            return
        }

        try {
            val session = api.getSession(storedToken)
            accessToken = storedToken
            _uiState.value = AuthUiState.SignedIn(session)
        } catch (error: LinkoraApiException) {
            if (error.status == 401) {
                tokenStore.clear()
                _uiState.value = AuthUiState.SignedOut
            } else {
                _uiState.value = AuthUiState.Error(error.userMessage())
            }
        } catch (error: Exception) {
            _uiState.value = AuthUiState.Error(error.userMessage())
        }
    }
}

private fun Exception.userMessage(): String = when (this) {
    is LinkoraApiException -> message ?: "Linkora could not complete the request."
    is IOException -> "Linkora could not be reached. Check your internet connection and try again."
    else -> "Linkora could not complete sign-in. Please try again."
}
