package top.linkora.mobile.update

import java.net.URI

object AppUpdatePolicy {
    private const val RELEASE_PATH_PREFIX = "/x1n-Q/linkora-android/releases/download/"
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun versionFromTag(tagName: String): String = tagName.trim().removePrefix("v")

    fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = versionParts(candidate) ?: return false
        val currentParts = versionParts(current) ?: return false
        val width = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until width) {
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (candidatePart != currentPart) return candidatePart > currentPart
        }
        return false
    }

    fun allowedDownloadUrl(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.equals("github.com", ignoreCase = true)) return null
        if (uri.port != -1 || uri.userInfo != null) return null
        val path = uri.path ?: return null
        if (!path.startsWith(RELEASE_PATH_PREFIX) || !path.endsWith(".apk")) return null
        return uri.toASCIIString()
    }

    fun normalizedSha256(value: String): String? {
        val digest = value.trim().removePrefix("sha256:")
        return digest.takeIf(sha256Pattern::matches)?.lowercase()
    }

    private fun versionParts(value: String): List<Int>? {
        val stable = versionFromTag(value).substringBefore('-')
        if (stable.isBlank()) return null
        return stable.split('.').map { part ->
            if (part.isBlank() || part.any { !it.isDigit() }) return null
            part.toIntOrNull() ?: return null
        }
    }
}
