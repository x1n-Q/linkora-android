package top.linkora.mobile.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppModeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val _mode = MutableStateFlow(readMode())

    val mode = _mode.asStateFlow()

    fun select(mode: AppMode) {
        preferences.edit().putString(KEY_MODE, mode.id).apply()
        _mode.value = mode
    }

    private fun readMode(): AppMode? {
        val stored = preferences.getString(KEY_MODE, null) ?: return null
        return AppMode.entries.firstOrNull { it.id == stored }
    }

    private companion object {
        const val PREFERENCES_NAME = "linkora_app_settings"
        const val KEY_MODE = "service_mode"
    }
}
