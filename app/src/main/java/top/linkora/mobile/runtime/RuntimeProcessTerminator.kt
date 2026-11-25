package top.linkora.mobile.runtime

import android.os.Process
import android.system.OsConstants
import java.io.File

internal object RuntimeProcessTerminator {
    fun terminate(process: java.lang.Process?) {
        if (process == null) return
        val rootPid = processId(process)
        val descendants = rootPid?.let(::descendantPids).orEmpty()

        descendants.asReversed().forEach { pid -> sendSignal(pid, OsConstants.SIGTERM) }
        runCatching { process.destroy() }
        descendants.asReversed().forEach { pid ->
            if (File("/proc/$pid").exists()) sendSignal(pid, OsConstants.SIGKILL)
        }
        if (process.isAlive) runCatching { process.destroyForcibly() }
    }

    private fun descendantPids(rootPid: Int): List<Int> {
        val visited = mutableSetOf<Int>()
        val descendants = mutableListOf<Int>()

        fun visit(parentPid: Int) {
            childPids(parentPid).forEach { childPid ->
                if (visited.add(childPid)) {
                    descendants += childPid
                    visit(childPid)
                }
            }
        }

        visit(rootPid)
        return descendants
    }

    private fun childPids(parentPid: Int): List<Int> = runCatching {
        File("/proc/$parentPid/task/$parentPid/children")
            .readText()
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull(String::toIntOrNull)
    }.getOrDefault(emptyList())

    private fun processId(process: java.lang.Process): Int? {
        runCatching {
            process.javaClass.getDeclaredField("pid").let { field ->
                field.isAccessible = true
                field.getInt(process)
            }
        }.getOrNull()?.let { return it }

        return Regex("pid=(\\d+)")
            .find(process.toString())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun sendSignal(pid: Int, signal: Int) {
        runCatching { Process.sendSignal(pid, signal) }
    }
}
