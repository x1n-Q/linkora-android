package top.linkora.mobile.node

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class NodeRuntimeViewModel(application: Application) : AndroidViewModel(application) {
    val uiState = NodeRuntimeStatus.state

    fun start() = NodeRuntimeService.start(getApplication())

    fun restart() = NodeRuntimeService.restart(getApplication())

    fun installDependencies() = NodeRuntimeService.installDependencies(getApplication())

    fun stop() = NodeRuntimeService.stop(getApplication())
}
