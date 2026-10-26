package top.linkora.mobile.auth

data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Long,
    val pollIntervalSeconds: Long,
)

data class LinkoraSession(
    val id: String,
    val email: String,
    val hostnameQuota: Int,
    val tokenExpiresAt: String,
)

sealed interface DeviceTokenResult {
    data object Pending : DeviceTokenResult

    data class Approved(
        val accessToken: String,
        val expiresAt: String,
    ) : DeviceTokenResult
}

sealed interface AuthUiState {
    data object Restoring : AuthUiState
    data object SignedOut : AuthUiState
    data object Starting : AuthUiState

    data class AwaitingApproval(
        val userCode: String,
        val verificationUrl: String,
    ) : AuthUiState

    data class SignedIn(val session: LinkoraSession) : AuthUiState
    data class Error(val message: String) : AuthUiState
}
