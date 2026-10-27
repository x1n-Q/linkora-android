package top.linkora.mobile.connector

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest

object CloudflaredBinary {
    private const val LIBRARY_NAME = "libcloudflared.so"
    private const val ARM64_SHA256 =
        "218a64c6c09cdb0cb9c751cde31b976c0a1c4db9fd6d6d512c4ef96f374840e1"

    fun resolve(context: Context): File {
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "The built-in Linkora connector currently requires an ARM64 Android device."
        }

        val executable = File(context.applicationInfo.nativeLibraryDir, LIBRARY_NAME)
        check(executable.isFile) {
            "The bundled Linkora connector is missing."
        }
        check(executable.sha256() == ARM64_SHA256) {
            "The bundled Linkora connector failed its integrity check."
        }
        check(executable.canExecute()) {
            "Android did not install the Linkora connector as executable code."
        }
        File(context.filesDir, "connector").deleteRecursively()
        return executable
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
