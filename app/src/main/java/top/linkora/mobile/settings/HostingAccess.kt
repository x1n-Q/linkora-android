package top.linkora.mobile.settings

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface

enum class HostingAccess(val id: String, val bindHost: String) {
    DeviceOnly("device_only", "127.0.0.1"),
    LocalNetwork("local_network", "0.0.0.0"),
}

class HostingSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): HostingAccess {
        val stored = preferences.getString(KEY_ACCESS, null)
        return HostingAccess.entries.firstOrNull { it.id == stored } ?: HostingAccess.DeviceOnly
    }

    fun save(access: HostingAccess) {
        preferences.edit().putString(KEY_ACCESS, access.id).apply()
    }

    fun localNetworkUrl(port: Int): String? {
        if (read() != HostingAccess.LocalNetwork) return null
        val address = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull() ?: return null
        return "http://$address:$port"
    }

    private companion object {
        const val PREFERENCES_NAME = "linkora_hosting_settings"
        const val KEY_ACCESS = "network_access"
    }
}
