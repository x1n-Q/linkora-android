package top.linkora.mobile.php

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class PhpRuntimeViewModel(application: Application) : AndroidViewModel(application) {
    val uiState = PhpRuntimeStatus.state

    fun start() = PhpRuntimeService.start(getApplication())

    fun restart() = PhpRuntimeService.restart(getApplication())

    fun stop() = PhpRuntimeService.stop(getApplication())
}
