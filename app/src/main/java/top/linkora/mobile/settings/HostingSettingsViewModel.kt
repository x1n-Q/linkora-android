package top.linkora.mobile.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HostingSettingsState(
    val access: HostingAccess,
    val localNetworkUrl: String?,
)

class HostingSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = HostingSettings(application)
    private val _state = MutableStateFlow(readState())

    val state = _state.asStateFlow()

    fun select(access: HostingAccess) {
        settings.save(access)
        _state.value = readState()
    }

    fun refresh() {
        _state.value = readState()
    }

    private fun readState() = HostingSettingsState(
        access = settings.read(),
        localNetworkUrl = settings.localNetworkUrl(8787),
    )
}
