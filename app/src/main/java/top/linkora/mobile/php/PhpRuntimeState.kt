package top.linkora.mobile.php

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PhpRuntimePhase {
    Idle,
    Syncing,
    Starting,
    Running,
    Stopping,
    Error,
}

data class PhpRuntimeState(
    val phase: PhpRuntimePhase = PhpRuntimePhase.Idle,
    val message: String = "PHP ${PhpBinary.VERSION} is ready.",
    val logs: List<String> = emptyList(),
    val uptimeSeconds: Long? = null,
    val memoryMb: Long? = null,
) {
    val isRunning: Boolean
        get() = phase == PhpRuntimePhase.Running

    val isBusy: Boolean
        get() = phase in setOf(
            PhpRuntimePhase.Syncing,
            PhpRuntimePhase.Starting,
            PhpRuntimePhase.Stopping,
        )
}

object PhpRuntimeStatus {
    private const val MAX_LOG_LINES = 250
    private val _state = MutableStateFlow(PhpRuntimeState())

    val state = _state.asStateFlow()

    fun update(phase: PhpRuntimePhase, message: String, clearLogs: Boolean = false) {
        _state.update { current ->
            current.copy(
                phase = phase,
                message = message,
                logs = if (clearLogs) emptyList() else current.logs,
                uptimeSeconds = if (phase == PhpRuntimePhase.Running) {
                    current.uptimeSeconds
                } else {
                    null
                },
                memoryMb = if (phase == PhpRuntimePhase.Running) current.memoryMb else null,
            )
        }
    }

    fun updateMetrics(uptimeSeconds: Long, memoryMb: Long) {
        _state.update { current ->
            if (current.phase == PhpRuntimePhase.Running) {
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
