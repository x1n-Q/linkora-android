package top.linkora.mobile.publish

data class DomainPool(
    val id: String,
    val domain: String,
)

data class ManagedHostname(
    val id: String,
    val hostname: String,
    val tunnelId: String?,
    val status: String,
)

data class TunnelAccess(
    val hostname: String,
    val token: String,
)

sealed interface PublishUiState {
    data object Loading : PublishUiState

    data class Ready(
        val pools: List<DomainPool>,
        val hostnames: List<ManagedHostname>,
    ) : PublishUiState

    data class Working(val message: String) : PublishUiState

    data class Published(
        val hostname: String,
    ) : PublishUiState

    data class Error(val message: String) : PublishUiState
}
