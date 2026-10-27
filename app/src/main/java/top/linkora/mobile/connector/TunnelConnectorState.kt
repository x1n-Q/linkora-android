package top.linkora.mobile.connector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TunnelConnectorState {
    data object Stopped : TunnelConnectorState
    data class Starting(val hostname: String) : TunnelConnectorState
    data class Running(val hostname: String) : TunnelConnectorState
    data class Reconnecting(val hostname: String, val attempt: Int) : TunnelConnectorState
    data class Error(val hostname: String, val message: String) : TunnelConnectorState
}

object TunnelConnectorStatus {
    private val _state = MutableStateFlow<TunnelConnectorState>(TunnelConnectorState.Stopped)

    val state = _state.asStateFlow()

    internal fun update(state: TunnelConnectorState) {
        _state.value = state
    }
}
