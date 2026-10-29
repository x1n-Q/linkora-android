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
    val uptimeSeconds: Long? = null,
    val memoryMb: Long? = null,
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
                uptimeSeconds = if (phase == NodeRuntimePhase.Running) {
                    current.uptimeSeconds
                } else {
                    null
                },
                memoryMb = if (phase == NodeRuntimePhase.Running) current.memoryMb else null,
            )
        }
    }

    fun updateMetrics(uptimeSeconds: Long, memoryMb: Long) {
        _state.update { current ->
            if (current.phase == NodeRuntimePhase.Running) {
                current.copy(uptimeSeconds = uptimeSeconds, memoryMb = memoryMb)
            } else {
                current
            }
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
