package top.linkora.mobile.connector

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest

object CloudflaredBinary {
    private const val VERSION = "2026.10.4"
    private const val ARM64_ASSET = "connector/arm64-v8a/cloudflared"
    private const val ARM64_SHA256 =
        "2b7203b4fb0ba28a0c1e12f8855ce2c25f76d266a8d5edafac5540b6812acff6"

    fun install(context: Context): File {
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "The built-in Linkora connector currently requires an ARM64 Android device."
        }

        val directory = File(context.filesDir, "connector").apply {
            check(mkdirs() || isDirectory) { "The connector directory could not be created." }
        }
        val destination = File(directory, "cloudflared-$VERSION")
        if (destination.isFile && destination.sha256() == ARM64_SHA256) {
            check(destination.setExecutable(true, true) || destination.canExecute()) {
                "The Linkora connector could not be made executable."
            }
            return destination
        }

        val temporary = File(directory, "cloudflared-$VERSION.tmp")
        temporary.delete()
        context.assets.open(ARM64_ASSET).use { source ->
            temporary.outputStream().use { output -> source.copyTo(output) }
        }
        check(temporary.sha256() == ARM64_SHA256) {
            temporary.delete()
            "The bundled Linkora connector failed its integrity check."
        }
        check(temporary.setExecutable(true, true)) {
            temporary.delete()
            "The Linkora connector could not be made executable."
        }
        if (destination.exists()) check(destination.delete()) {
            "The old Linkora connector could not be replaced."
        }
        check(temporary.renameTo(destination)) {
            temporary.delete()
            "The Linkora connector could not be installed."
        }
        return destination
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
