package top.linkora.mobile.mariadb

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class MariaDbRuntimeViewModel(application: Application) : AndroidViewModel(application) {
    val uiState = MariaDbRuntimeStatus.state

    init {
        MariaDbRuntimeStatus.setCredentials(MariaDbCredentialStore.load(application))
    }

    fun start() = MariaDbRuntimeService.start(getApplication())

    fun restart() = MariaDbRuntimeService.restart(getApplication())

    fun stop() = MariaDbRuntimeService.stop(getApplication())
}
