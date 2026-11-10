package top.linkora.mobile.runtime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RuntimePackUiState(
    val node: RuntimePackInspection,
    val php: RuntimePackInspection,
    val mariaDb: RuntimePackInspection,
)

class RuntimePackViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        RuntimePackUiState(
            node = RuntimePackInspection(
                spec = RuntimePacks.Node,
                state = RuntimePackState.Checking,
                message = "Checking the Node.js Runtime Pack...",
            ),
            php = RuntimePackInspection(
                spec = RuntimePacks.Php,
                state = RuntimePackState.Checking,
                message = "Checking the PHP Runtime Pack...",
            ),
            mariaDb = RuntimePackInspection(
                spec = RuntimePacks.MariaDb,
                state = RuntimePackState.Checking,
                message = "Checking the MariaDB Runtime Pack...",
            ),
        ),
    )
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.value = withContext(Dispatchers.IO) { readState() }
        }
    }

    private fun readState() = RuntimePackUiState(
        node = RuntimePacks.inspectNode(getApplication()),
        php = RuntimePacks.inspectPhp(getApplication()),
        mariaDb = RuntimePacks.inspectMariaDb(getApplication()),
    )
}
