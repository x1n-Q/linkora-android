package top.linkora.mobile.node

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class NodeRuntimePhase {
    Idle,
    Syncing,
    Starting,
    Running,
    Stopping,
    Installing,
    Error,
}

data class NodeRuntimeState(
    val phase: NodeRuntimePhase = NodeRuntimePhase.Idle,
    val message: String = "Node.js ${NodeBinary.VERSION} is ready.",
    val logs: List<String> = emptyList(),
) {
    val isRunning: Boolean
        get() = phase == NodeRuntimePhase.Running

    val isBusy: Boolean
        get() = phase in setOf(
            NodeRuntimePhase.Syncing,
            NodeRuntimePhase.Starting,
            NodeRuntimePhase.Stopping,
            NodeRuntimePhase.Installing,
        )
}

object NodeRuntimeStatus {
    private const val MAX_LOG_LINES = 250
    private val _state = MutableStateFlow(NodeRuntimeState())

    val state = _state.asStateFlow()

    fun update(phase: NodeRuntimePhase, message: String, clearLogs: Boolean = false) {
        _state.update { current ->
            current.copy(
                phase = phase,
                message = message,
                logs = if (clearLogs) emptyList() else current.logs,
            )
        }
    }

    fun appendLog(line: String) {
        val cleanLine = line.replace(Regex("[\\p{Cntrl}&&[^\\t]]"), "").take(2_000)
        if (cleanLine.isBlank()) return
        _state.update { current ->
            current.copy(logs = (current.logs + cleanLine).takeLast(MAX_LOG_LINES))
        }
    }
}
