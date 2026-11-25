package top.linkora.mobile.auth

import java.net.URI

internal object LinkoraAuthUrlPolicy {
    fun allowedUrl(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val allowedHost = host == ROOT_HOST || host.endsWith(".$ROOT_HOST")
        if (!allowedHost || !uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        return uri.toASCIIString()
    }

    private const val ROOT_HOST = "linkora.top"
}
