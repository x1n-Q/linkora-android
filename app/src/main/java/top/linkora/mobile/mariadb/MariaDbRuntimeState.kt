package top.linkora.mobile.mariadb

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MariaDbRuntimePhase {
    Idle,
    Preparing,
    Starting,
    Running,
    Stopping,
    Error,
}

data class MariaDbRuntimeState(
    val phase: MariaDbRuntimePhase = MariaDbRuntimePhase.Idle,
    val message: String = "Optional database is ready to start.",
    val credentials: MariaDbCredentials? = null,
    val logs: List<String> = emptyList(),
    val uptimeSeconds: Long? = null,
    val memoryMb: Long? = null,
) {
    val isRunning: Boolean
        get() = phase == MariaDbRuntimePhase.Running

    val isBusy: Boolean
        get() = phase in setOf(
            MariaDbRuntimePhase.Preparing,
            MariaDbRuntimePhase.Starting,
            MariaDbRuntimePhase.Stopping,
        )
}

object MariaDbRuntimeStatus {
    private const val MAX_LOG_LINES = 200
    private val _state = MutableStateFlow(MariaDbRuntimeState())
    val state = _state.asStateFlow()

    fun setCredentials(credentials: MariaDbCredentials) {
        _state.update { it.copy(credentials = credentials) }
    }

    fun update(phase: MariaDbRuntimePhase, message: String, clearLogs: Boolean = false) {
        _state.update { current ->
            current.copy(
                phase = phase,
                message = message,
                logs = if (clearLogs) emptyList() else current.logs,
                uptimeSeconds = if (phase == MariaDbRuntimePhase.Running) {
                    current.uptimeSeconds
                } else {
                    null
                },
                memoryMb = if (phase == MariaDbRuntimePhase.Running) current.memoryMb else null,
            )
        }
    }

    fun updateMetrics(uptimeSeconds: Long, memoryMb: Long) {
        _state.update { current ->
            if (current.phase == MariaDbRuntimePhase.Running) {
                current.copy(uptimeSeconds = uptimeSeconds, memoryMb = memoryMb)
            } else {
                current
            }
        }
    }

    fun appendLog(line: String) {
        val clean = line.replace(Regex("[\\p{Cntrl}&&[^\\t]]"), "").take(2_000)
        if (clean.isBlank()) return
        _state.update { it.copy(logs = (it.logs + clean).takeLast(MAX_LOG_LINES)) }
    }
}
